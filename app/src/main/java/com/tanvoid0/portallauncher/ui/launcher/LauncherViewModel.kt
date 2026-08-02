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
import com.tanvoid0.portallauncher.data.DrawerSortMode
import com.tanvoid0.portallauncher.data.GridSize
import com.tanvoid0.portallauncher.data.HomeCellEntity
import com.tanvoid0.portallauncher.data.HomeEntry
import com.tanvoid0.portallauncher.data.LaunchableApp
import com.tanvoid0.portallauncher.data.ProfileEntity
import com.tanvoid0.portallauncher.data.Slot
import com.tanvoid0.portallauncher.data.applyOverrides
import com.tanvoid0.portallauncher.data.canPlace
import com.tanvoid0.portallauncher.data.cellsForApps
import com.tanvoid0.portallauncher.data.firstFreeSlot
import com.tanvoid0.portallauncher.data.homeCellFor
import com.tanvoid0.portallauncher.data.isOnHome
import com.tanvoid0.portallauncher.data.moveCell
import com.tanvoid0.portallauncher.data.normalisePages
import com.tanvoid0.portallauncher.data.pageCount
import com.tanvoid0.portallauncher.data.placeAnywhere
import com.tanvoid0.portallauncher.data.reflow
import com.tanvoid0.portallauncher.data.resolveActiveProfile
import com.tanvoid0.portallauncher.data.resolveHomeEntries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** How many category-matched apps fill the home screen before the user changes anything. */
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
    /** What to draw on the home grid: the user's layout, or the category default. */
    val homeEntries: List<HomeEntry> = emptyList(),
    /** The grid those entries are placed on. */
    val grid: GridSize = GridSize.Default,
    /** How many pages the layout spans. At least one. */
    val pageCount: Int = 1,
    /**
     * Resolved category per app key. Computed once here rather than per drawer section,
     * so the drawer never has to know what the categoriser needs as input.
     */
    val categoryByKey: Map<String, AppCategory> = emptyMap(),
    /** Stored cells for the active profile, so the menu knows what is already on home. */
    val cells: List<HomeCellEntity> = emptyList(),
    /** True once the user has changed anything, i.e. the grid is theirs and not a default. */
    val hasCustomLayout: Boolean = false,
    /** How the drawer orders apps within (or across) categories. */
    val drawerSortMode: DrawerSortMode = DrawerSortMode.ALPHABETICAL,
    /** Whether the drawer groups apps under category headers at all. */
    val categoryBarVisible: Boolean = true,
    /** Launch count per app key, for [DrawerSortMode.MOST_USED]. Absent means never launched. */
    val usageCountByKey: Map<String, Int> = emptyMap()
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PortalLauncherApplication
    private val profileRepository = app.profileRepository
    private val preferencesRepository = app.preferencesRepository
    private val appRepository = app.appRepository
    private val iconCache = app.iconCache
    private val widgetHost = app.widgetHost
    private val homeCellDao = app.database.homeCellDao()
    private val appOverrideDao = app.database.appOverrideDao()
    private val appUsageDao = app.database.appUsageDao()

    /**
     * Widget ids the host still holds.
     *
     * Cached rather than read inside the state combine, because `AppWidgetHost` answers
     * over Binder and the combine runs on the main dispatcher. Refreshed whenever the
     * launcher adds or removes a widget, which are the only times it can change while
     * we are running.
     */
    private val liveWidgetIds = MutableStateFlow<Set<Int>>(emptySet())

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
    private val cellsFlow: Flow<List<HomeCellEntity>> =
        activeProfileFlow.flatMapLatest { profile ->
            if (profile != null) homeCellDao.observeForProfile(profile.id) else flowOf(emptyList())
        }

    private val overridesFlow: Flow<Map<String, AppOverrideEntity>> =
        appOverrideDao.observeAll().map { rows ->
            rows.associateBy { "${it.packageName}/${it.activityName}/${it.userSerial}" }
        }

    private val usageCountByKeyFlow: Flow<Map<String, Int>> =
        appUsageDao.observeAll().map { rows ->
            rows.associate { "${it.packageName}/${it.activityName}/${it.userSerial}" to it.launchCount }
        }

    /**
     * The drawer's own display preferences, folded together before joining the main
     * [combine] below: that one is already at the five-flow ceiling the codebase has
     * hit twice before (see [appsFlow], [layoutFlow]).
     */
    private val drawerPrefsFlow = combine(
        preferencesRepository.drawerSortMode,
        preferencesRepository.categoryBarVisible,
        usageCountByKeyFlow
    ) { sortMode, categoryBarVisible, usageCountByKey ->
        DrawerPrefs(sortMode, categoryBarVisible, usageCountByKey)
    }

    private data class DrawerPrefs(
        val sortMode: DrawerSortMode,
        val categoryBarVisible: Boolean,
        val usageCountByKey: Map<String, Int>
    )

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

    /** The stored layout, the grid it sits on, and whether it is the user's own. */
    private val layoutFlow = combine(
        cellsFlow,
        activeProfileFlow,
        preferencesRepository.customLayoutProfileIds,
        preferencesRepository.homeGrid,
        liveWidgetIds
    ) { cells, profile, customised, grid, widgetIds ->
        Layout(cells, profile != null && profile.id in customised, grid, widgetIds)
    }

    private data class Layout(
        val cells: List<HomeCellEntity>,
        val isCustomised: Boolean,
        val grid: GridSize,
        val liveWidgetIds: Set<Int>
    )

    private val baseUiStateFlow = combine(
        activeProfileFlow,
        profileRepository.getAllProfiles(),
        visibilityConfigFlow,
        appsFlow,
        layoutFlow
    ) { active, profiles, visibilityConfig, (apps, ai), layout ->
        val categoryByKey = apps.associate { it.key to AppCategorizer.categoryFor(it, ai) }
        val categoryFiltered = filterAppsByProfile(apps, active, visibilityConfig, ai)
        val entries = resolveHomeEntries(
            cells = layout.cells,
            installed = apps,
            categoryFiltered = categoryFiltered,
            liveWidgetIds = layout.liveWidgetIds,
            profileId = active?.id.orEmpty(),
            grid = layout.grid,
            fallbackLimit = HOME_FALLBACK_COUNT,
            isCustomised = layout.isCustomised
        )
        LauncherUiState(
            activeProfile = active,
            profiles = profiles,
            allApps = apps,
            categoryByKey = categoryByKey,
            homeEntries = entries,
            grid = layout.grid,
            // Counted from the stored cells, not from what resolved: a page holding
            // only an uninstalled app's icon still exists, and dropping it under the
            // user while they are looking at it is worse than an empty page.
            pageCount = pageCount(if (layout.isCustomised) layout.cells else emptyList()),
            cells = layout.cells,
            hasCustomLayout = layout.isCustomised
        )
    }

    val uiState: StateFlow<LauncherUiState> = combine(
        baseUiStateFlow,
        drawerPrefsFlow
    ) { state, prefs ->
        state.copy(
            drawerSortMode = prefs.sortMode,
            categoryBarVisible = prefs.categoryBarVisible,
            usageCountByKey = prefs.usageCountByKey
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        // No loading flag: the home screen paints its chrome immediately and app
        // cells appear when this emits. A launcher must never show a spinner.
        initialValue = LauncherUiState()
    )

    init {
        refreshLiveWidgets()
        healLayoutForGrid()
    }

    fun launch(app: LaunchableApp) {
        appRepository.launch(app)
        viewModelScope.launch {
            appUsageDao.recordLaunch(app.packageName, app.activityName, app.userSerial)
        }
    }

    fun setDrawerSortMode(mode: DrawerSortMode) {
        viewModelScope.launch { preferencesRepository.setDrawerSortMode(mode) }
    }

    fun setCategoryBarVisible(visible: Boolean) {
        viewModelScope.launch { preferencesRepository.setCategoryBarVisible(visible) }
    }

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

    fun isOnHomeScreen(app: LaunchableApp): Boolean = isOnHome(uiState.value.cells, app)

    /** The category in effect for [app], however it was decided. */
    fun categoryOf(app: LaunchableApp): AppCategory =
        uiState.value.categoryByKey[app.key] ?: AppCategory.Other

    /**
     * Puts [app] on the home screen, or takes it off.
     *
     * The first change converts the grid from "the category default" into the user's
     * own layout, which means writing the current default down first — otherwise adding
     * one app would appear to delete the other seven.
     */
    fun toggleOnHome(app: LaunchableApp) {
        val state = uiState.value
        val profileId = state.activeProfile?.id ?: return
        viewModelScope.launch {
            if (isOnHome(state.cells, app)) {
                homeCellDao.removeApp(profileId, app.packageName, app.activityName, app.userSerial)
                compactPages(profileId)
                return@launch
            }
            val cells = seedIfDefault(state, profileId)
            val slot = placeAnywhere(cells, spanX = 1, spanY = 1, grid = state.grid)
            homeCellDao.upsert(homeCellFor(app, profileId, slot))
        }
    }

    /**
     * Moves whatever is at [from] to [to], swapping two icons when the target is taken.
     *
     * Silently does nothing when the move is illegal. The grid has already snapped the
     * dragged item back by then, so there is nothing to tell the user that they do not
     * already see.
     */
    fun moveHomeCell(from: Slot, to: Slot) {
        val state = uiState.value
        val profileId = state.activeProfile?.id ?: return
        viewModelScope.launch {
            val cells = seedIfDefault(state, profileId)
            val moved = moveCell(cells, from, to, state.grid) ?: return@launch
            homeCellDao.replaceForProfile(profileId, normalisePages(moved))
        }
    }

    /**
     * Moves whatever is at [from] onto [page], into the first free space there.
     *
     * The cross-page case of a drag. Where the item was on its old page says nothing
     * about where it should sit on the new one, and dropping it at the same coordinates
     * would land it on top of whatever is already there.
     *
     * [page] may be one past the last one — that is how a page gets created.
     */
    fun moveHomeCellToPage(from: Slot, page: Int) {
        val state = uiState.value
        val profileId = state.activeProfile?.id ?: return
        viewModelScope.launch {
            val cells = seedIfDefault(state, profileId)
            val moving = cells.firstOrNull {
                it.page == from.page && it.cellX == from.cellX && it.cellY == from.cellY
            } ?: return@launch
            val rest = cells - moving
            val slot = firstFreeSlot(rest, page, moving.spanX, moving.spanY, state.grid)
                ?: return@launch
            val moved = moving.copy(page = slot.page, cellX = slot.cellX, cellY = slot.cellY)
            homeCellDao.replaceForProfile(profileId, normalisePages(rest + moved))
        }
    }

    /** Takes whatever is at [slot] off the home screen, releasing a widget's id with it. */
    fun removeHomeCell(slot: Slot) {
        val state = uiState.value
        val profileId = state.activeProfile?.id ?: return
        val cell = state.cells.firstOrNull {
            it.page == slot.page && it.cellX == slot.cellX && it.cellY == slot.cellY
        }
        viewModelScope.launch {
            homeCellDao.deleteAt(profileId, slot.page, slot.cellX, slot.cellY)
            if (cell != null && cell.isWidget) {
                widgetHost.deleteAppWidgetId(cell.appWidgetId)
                refreshLiveWidgets()
            }
            compactPages(profileId)
        }
    }

    /**
     * Stores a widget the user has just bound and configured.
     *
     * The id arrives already live — see [com.tanvoid0.portallauncher.widgets.WidgetPlacement] —
     * so the only failure left is having nowhere to put it, and [placeAnywhere] answers
     * that by adding a page rather than refusing.
     */
    fun placeWidget(
        providerPackage: String,
        providerClass: String,
        appWidgetId: Int,
        spanX: Int,
        spanY: Int
    ) {
        val state = uiState.value
        val profileId = state.activeProfile?.id ?: return
        viewModelScope.launch {
            val cells = seedIfDefault(state, profileId)
            val slot = placeAnywhere(cells, spanX, spanY, state.grid)
            homeCellDao.upsert(
                HomeCellEntity(
                    profileId = profileId,
                    page = slot.page,
                    cellX = slot.cellX,
                    cellY = slot.cellY,
                    spanX = spanX,
                    spanY = spanY,
                    kind = HomeCellEntity.KIND_WIDGET,
                    packageName = providerPackage,
                    activityName = providerClass,
                    userSerial = 0L,
                    appWidgetId = appWidgetId
                )
            )
            refreshLiveWidgets()
        }
    }

    /** Resizes the widget at [slot]. Does nothing if the new rectangle would not fit. */
    fun resizeWidget(slot: Slot, spanX: Int, spanY: Int) {
        val state = uiState.value
        val profileId = state.activeProfile?.id ?: return
        val cell = state.cells.firstOrNull {
            it.page == slot.page && it.cellX == slot.cellX && it.cellY == slot.cellY
        } ?: return
        val resized = cell.copy(spanX = spanX, spanY = spanY)
        if (!canPlace(state.cells, resized, state.grid, ignoring = slot)) return
        viewModelScope.launch { homeCellDao.upsert(resized) }
    }

    fun setGrid(grid: GridSize) {
        viewModelScope.launch { preferencesRepository.setHomeGrid(grid) }
    }

    /** Gives the profile its category default back, discarding the user's own layout. */
    fun resetLayout() {
        val state = uiState.value
        val profileId = state.activeProfile?.id ?: return
        val widgetIds = state.cells.filter { it.isWidget }.map { it.appWidgetId }
        viewModelScope.launch {
            homeCellDao.deleteForProfile(profileId)
            // The rows are gone, so nothing would ever ask for these widgets again.
            widgetIds.forEach { widgetHost.deleteAppWidgetId(it) }
            refreshLiveWidgets()
            preferencesRepository.setLayoutCustomised(profileId, false)
        }
    }

    /**
     * Hides an app everywhere, and takes it off every home screen. Without the removal,
     * storage would hold a placement for something the user asked never to see; the
     * resolver filters hidden apps anyway, but two rows disagreeing is how stale state
     * gets shipped.
     */
    fun setHidden(app: LaunchableApp, hidden: Boolean) {
        viewModelScope.launch {
            appOverrideDao.upsert(currentOverride(app).copy(hidden = hidden))
            if (hidden) {
                uiState.value.profiles.forEach { profile ->
                    homeCellDao.removeApp(
                        profile.id,
                        app.packageName,
                        app.activityName,
                        app.userSerial
                    )
                    compactPages(profile.id)
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
     * Writes the category default down as real cells the first time the user edits a
     * profile that is still using it, and returns the layout to work from.
     *
     * Every edit goes through here for the same reason the old pin path did: the
     * default is a *view*, and the first edit has to turn it into data before it can be
     * changed, or the other icons vanish.
     */
    private suspend fun seedIfDefault(state: LauncherUiState, profileId: String): List<HomeCellEntity> {
        if (state.hasCustomLayout) return state.cells
        val seed = cellsForApps(
            apps = state.homeEntries.filterIsInstance<HomeEntry.App>().map { it.app },
            profileId = profileId,
            grid = state.grid
        )
        homeCellDao.replaceForProfile(profileId, seed)
        preferencesRepository.setLayoutCustomised(profileId, true)
        return seed
    }

    /**
     * Drops pages a removal emptied.
     *
     * Not folded into the delete queries: renumbering pages during a gesture would move
     * the page the user is looking at, so it runs once the change has settled.
     */
    private suspend fun compactPages(profileId: String) {
        val cells = homeCellDao.getForProfile(profileId)
        val compacted = normalisePages(cells)
        if (compacted != cells) homeCellDao.replaceForProfile(profileId, compacted)
    }

    private fun refreshLiveWidgets() {
        // Off the main thread: `appWidgetIds` answers over Binder, and this runs during
        // ViewModel construction — on the first frame of the launcher.
        viewModelScope.launch(Dispatchers.IO) {
            liveWidgetIds.value = widgetHost.appWidgetIds.toSet()
        }
    }

    /**
     * Puts cells back inside the grid after the user changes its size.
     *
     * A layout built on six columns has icons at `cellX = 5` that a four-column grid
     * simply never draws — apps would appear to be deleted by a settings change. [reflow]
     * is idempotent, so writing its result back cannot loop: the next emission finds
     * nothing to do.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun healLayoutForGrid() {
        viewModelScope.launch {
            activeProfileFlow
                .flatMapLatest { profile ->
                    if (profile == null) {
                        flowOf(null)
                    } else {
                        combine(
                            homeCellDao.observeForProfile(profile.id),
                            preferencesRepository.homeGrid
                        ) { cells, grid -> Triple(profile.id, cells, grid) }
                    }
                }
                .distinctUntilChanged()
                .collect { state ->
                    val (profileId, cells, grid) = state ?: return@collect
                    if (cells.isEmpty()) return@collect
                    val fixed = reflow(cells, grid)
                    if (fixed != cells) homeCellDao.replaceForProfile(profileId, fixed)
                }
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
