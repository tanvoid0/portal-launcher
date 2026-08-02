package com.tanvoid0.portallauncher.ui.launcher

import android.app.Application
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tanvoid0.portallauncher.PortalLauncherApplication
import com.tanvoid0.portallauncher.data.AppCategory
import com.tanvoid0.portallauncher.data.AppCategorizer
import com.tanvoid0.portallauncher.data.AppOverrideEntity
import com.tanvoid0.portallauncher.data.AppVisibilityConfig
import com.tanvoid0.portallauncher.data.HomeItemEntity
import com.tanvoid0.portallauncher.data.LaunchableApp
import com.tanvoid0.portallauncher.data.ProfileEntity
import com.tanvoid0.portallauncher.data.applyOverrides
import com.tanvoid0.portallauncher.data.homeItemFor
import com.tanvoid0.portallauncher.data.isPinned
import com.tanvoid0.portallauncher.data.resolveActiveProfile
import com.tanvoid0.portallauncher.data.resolveHomeApps
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How many category-matched apps fill the home screen before the user pins anything. */
const val HOME_FALLBACK_COUNT = 8

data class LauncherUiState(
    val activeProfile: ProfileEntity? = null,
    val profiles: List<ProfileEntity> = emptyList(),
    /**
     * Every launchable app, whatever the profile says. The dock and the drawer read
     * this: a profile decides what the home screen leads with, it must never be able
     * to put the dialler out of reach or leave the user with no way to open an app
     * it filtered out. The spec says the same thing — hidden from the main view,
     * still accessible.
     */
    val allApps: List<LaunchableApp> = emptyList(),
    /** What the home grid shows: the user's pinned apps, or the category default. */
    val homeApps: List<LaunchableApp> = emptyList(),
    /**
     * Resolved category per app key. Computed once here rather than per drawer section,
     * so the drawer never has to know what the categoriser needs as input.
     */
    val categoryByKey: Map<String, AppCategory> = emptyMap(),
    /** Pinned rows for the active profile, so the menu knows whether to say Pin or Unpin. */
    val pinned: List<HomeItemEntity> = emptyList(),
    /** True once the user has pinned anything, i.e. the grid is theirs and not a default. */
    val hasCustomLayout: Boolean = false
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PortalLauncherApplication
    private val profileRepository = app.profileRepository
    private val preferencesRepository = app.preferencesRepository
    private val appRepository = app.appRepository
    private val iconCache = app.iconCache
    private val homeItemDao = app.database.homeItemDao()
    private val appOverrideDao = app.database.appOverrideDao()

    /**
     * Whatever the on-device model has classified so far. Empty on every device
     * where the feature is unavailable or switched off — which is the same code
     * path, not a special case.
     */
    private val aiCategories = app.database.aiCategoryDao().observeAll()
        .map { rows -> rows.associate { it.packageName to AppCategory.fromId(it.categoryId) } }

    /**
     * The profile in effect, which is not simply the stored id — see
     * [resolveActiveProfile]. Everything downstream keys off *this* rather than the
     * preference, so the profile shown as active and the config being applied can
     * never disagree; keying the config off the raw id meant a dangling preference
     * silently applied no filter while the UI showed the default profile selected.
     */
    private val activeProfileFlow = combine(
        preferencesRepository.activeProfileId,
        profileRepository.getAllProfiles()
    ) { activeId, profiles -> resolveActiveProfile(profiles, activeId) }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val visibilityConfigFlow =
        activeProfileFlow.flatMapLatest { profile ->
            if (profile != null) profileRepository.getVisibilityConfigForProfile(profile.id)
            else flowOf(null)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val pinnedFlow: Flow<List<HomeItemEntity>> =
        activeProfileFlow.flatMapLatest { profile ->
            if (profile != null) homeItemDao.observeForProfile(profile.id) else flowOf(emptyList())
        }

    private val overridesFlow: Flow<Map<String, AppOverrideEntity>> =
        appOverrideDao.observeAll().map { rows ->
            rows.associateBy { "${it.packageName}/${it.activityName}/${it.userSerial}" }
        }

    /**
     * Combined in two stages because [combine] takes at most five flows. The first
     * stage is everything about the apps themselves, the second everything about the
     * profile — splitting it this way keeps each stage's inputs related.
     */
    private val appsFlow = combine(
        appRepository.apps,
        overridesFlow,
        aiCategories
    ) { apps, overrides, ai -> applyOverrides(apps, overrides) to ai }

    /** Pinned rows plus whether this profile's layout is the user's, empty or not. */
    private val layoutFlow = combine(
        pinnedFlow,
        activeProfileFlow,
        preferencesRepository.customLayoutProfileIds
    ) { pinned, profile, customised -> pinned to (profile != null && profile.id in customised) }

    val uiState: StateFlow<LauncherUiState> = combine(
        activeProfileFlow,
        profileRepository.getAllProfiles(),
        visibilityConfigFlow,
        appsFlow,
        layoutFlow
    ) { active, profiles, visibilityConfig, (apps, ai), (pinned, isCustomised) ->
        val categoryByKey = apps.associate { it.key to AppCategorizer.categoryFor(it, ai) }
        val categoryFiltered = filterAppsByProfile(apps, active, visibilityConfig, ai)
        LauncherUiState(
            activeProfile = active,
            profiles = profiles,
            allApps = apps,
            categoryByKey = categoryByKey,
            homeApps = resolveHomeApps(
                pinned = pinned,
                installed = apps,
                categoryFiltered = categoryFiltered,
                fallbackLimit = HOME_FALLBACK_COUNT,
                isCustomised = isCustomised
            ),
            pinned = pinned,
            hasCustomLayout = isCustomised
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        // No loading flag: the home screen paints its chrome immediately and app
        // cells appear when this emits. A launcher must never show a spinner.
        initialValue = LauncherUiState()
    )

    fun launch(app: LaunchableApp) = appRepository.launch(app)

    fun openAppInfo(app: LaunchableApp) = appRepository.openAppInfo(app)

    fun canUninstall(app: LaunchableApp) = appRepository.canUninstall(app)

    fun setActiveProfile(profileId: String) {
        viewModelScope.launch {
            preferencesRepository.setActiveProfileId(profileId)
        }
    }

    /** Icon for a visible cell, cached across screens. See [com.tanvoid0.portallauncher.data.IconCache]. */
    suspend fun loadIcon(app: LaunchableApp, sizePx: Int): ImageBitmap? =
        iconCache.icon(app, sizePx)

    fun isPinnedToHome(app: LaunchableApp): Boolean = isPinned(uiState.value.pinned, app)

    /** The category in effect for [app], however it was decided. */
    fun categoryOf(app: LaunchableApp): AppCategory =
        uiState.value.categoryByKey[app.key] ?: AppCategory.Other

    /**
     * Pinning the first app converts the grid from "the category default" into the
     * user's own layout. That means seeding the current default first, or the act of
     * pinning one app would appear to delete the other seven.
     */
    fun togglePin(app: LaunchableApp) {
        val state = uiState.value
        val profileId = state.activeProfile?.id ?: return
        viewModelScope.launch {
            if (isPinned(state.pinned, app)) {
                homeItemDao.remove(profileId, app.packageName, app.activityName, app.userSerial)
                return@launch
            }
            // First pin converts the grid from "the category default" into the user's
            // own list. Seed the default first, or pinning one app would look like it
            // deleted the other seven.
            if (!state.hasCustomLayout) {
                val seed = state.homeApps.filter { it.key != app.key }
                homeItemDao.replaceForProfile(
                    profileId,
                    seed.mapIndexed { index, seeded -> homeItemFor(seeded, profileId, index) }
                )
                preferencesRepository.setLayoutCustomised(profileId, true)
            }
            homeItemDao.upsert(homeItemFor(app, profileId, homeItemDao.nextPosition(profileId)))
        }
    }

    /** Gives the profile its category default back, discarding the user's own layout. */
    fun resetLayout() {
        val profileId = uiState.value.activeProfile?.id ?: return
        viewModelScope.launch {
            homeItemDao.deleteForProfile(profileId)
            preferencesRepository.setLayoutCustomised(profileId, false)
        }
    }

    /**
     * Hides an app everywhere, and unpins it. Without the unpin, storage would hold a
     * pin for something the user asked never to see; the resolver filters hidden apps
     * anyway, but two rows disagreeing is how stale state gets shipped.
     */
    fun setHidden(app: LaunchableApp, hidden: Boolean) {
        viewModelScope.launch {
            appOverrideDao.upsert(currentOverride(app).copy(hidden = hidden))
            if (hidden) {
                uiState.value.profiles.forEach { profile ->
                    homeItemDao.remove(profile.id, app.packageName, app.activityName, app.userSerial)
                }
            }
            appOverrideDao.pruneEmpty()
        }
    }

    /**
     * Assigns a category by hand, or clears the assignment when [category] is null.
     * Outranks every automatic source — see [AppCategorizer.categoryFor].
     */
    fun setCategory(app: LaunchableApp, category: AppCategory?) {
        viewModelScope.launch {
            appOverrideDao.upsert(currentOverride(app).copy(categoryId = category?.id))
            appOverrideDao.pruneEmpty()
        }
    }

    /** Renames an app, or clears the rename when [label] is blank. */
    fun rename(app: LaunchableApp, label: String) {
        viewModelScope.launch {
            val trimmed = label.trim().takeIf { it.isNotEmpty() && it != app.label }
            appOverrideDao.upsert(currentOverride(app).copy(customLabel = trimmed))
            appOverrideDao.pruneEmpty()
        }
    }

    /**
     * The app's override row as it stands, so a `copy()` changing one field does not
     * clear the others. Building a blank row instead meant renaming an app silently
     * dropped its category override, and vice versa.
     *
     * `hidden` is false because every app reachable from the UI has already passed the
     * hidden filter; [setHidden] is the only caller that sets it true.
     */
    private fun currentOverride(app: LaunchableApp) = AppOverrideEntity(
        packageName = app.packageName,
        activityName = app.activityName,
        userSerial = app.userSerial,
        hidden = false,
        customLabel = app.customLabel,
        categoryId = app.categoryOverride?.id
    )

    private fun filterAppsByProfile(
        apps: List<LaunchableApp>,
        profile: ProfileEntity?,
        visibilityConfig: AppVisibilityConfig?,
        aiCategories: Map<String, AppCategory>
    ): List<LaunchableApp> {
        if (profile == null || visibilityConfig == null) return apps
        val primary = visibilityConfig.primaryCategoryIds.toSet()
        if (primary.isEmpty()) return apps
        return apps.filter { AppCategorizer.categoryFor(it, aiCategories).id in primary }
    }
}
