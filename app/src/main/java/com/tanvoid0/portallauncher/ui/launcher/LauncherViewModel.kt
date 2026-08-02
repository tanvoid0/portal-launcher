package com.tanvoid0.portallauncher.ui.launcher

import android.app.Application
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tanvoid0.portallauncher.PortalLauncherApplication
import com.tanvoid0.portallauncher.data.AppCategory
import com.tanvoid0.portallauncher.data.AppCategorizer
import com.tanvoid0.portallauncher.data.AppVisibilityConfig
import com.tanvoid0.portallauncher.data.LaunchableApp
import com.tanvoid0.portallauncher.data.ProfileEntity
import com.tanvoid0.portallauncher.data.resolveActiveProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    /** [allApps] narrowed to the active profile's primary categories. */
    val homeApps: List<LaunchableApp> = emptyList()
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PortalLauncherApplication
    private val profileRepository = app.profileRepository
    private val preferencesRepository = app.preferencesRepository
    private val appRepository = app.appRepository

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

    val uiState: StateFlow<LauncherUiState> = combine(
        activeProfileFlow,
        profileRepository.getAllProfiles(),
        visibilityConfigFlow,
        appRepository.apps,
        aiCategories
    ) { active, profiles, visibilityConfig, apps, aiCategories ->
        LauncherUiState(
            activeProfile = active,
            profiles = profiles,
            allApps = apps,
            homeApps = filterAppsByProfile(apps, active, visibilityConfig, aiCategories)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        // No loading flag: the home screen paints its chrome immediately and app
        // cells appear when this emits. A launcher must never show a spinner.
        initialValue = LauncherUiState()
    )

    fun launch(app: LaunchableApp) = appRepository.launch(app)

    fun setActiveProfile(profileId: String) {
        viewModelScope.launch {
            preferencesRepository.setActiveProfileId(profileId)
        }
    }

    /**
     * Rasterises one app icon at the caller's pixel size. Called per *visible*
     * grid cell, so only what is on screen is ever rendered — the previous code
     * rasterised every installed app up front and held the bitmaps in state.
     *
     * ponytail: no cache, so scrolling back re-renders the drawable. Phase 3 puts
     * an LruCache inside AppRepository.loadIcon — the only place that changes.
     */
    suspend fun loadIcon(app: LaunchableApp, sizePx: Int): ImageBitmap? =
        withContext(Dispatchers.Default) {
            appRepository.loadIcon(app)?.toImageBitmap(sizePx)
        }

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

private fun Drawable.toImageBitmap(size: Int): ImageBitmap {
    val bitmap = createBitmap(size, size)
    setBounds(0, 0, size, size)
    draw(Canvas(bitmap))
    return bitmap.asImageBitmap()
}
