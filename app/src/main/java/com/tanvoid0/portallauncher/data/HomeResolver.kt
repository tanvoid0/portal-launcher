package com.tanvoid0.portallauncher.data

/**
 * Turns what is installed plus what the user decided into the lists the launcher
 * shows. Pure functions, so the rules are testable without a device — these decide
 * what appears on someone's home screen, which is the last place for a surprise.
 */

/**
 * Applies per-app overrides: drops hidden apps and attaches renames.
 *
 * Hiding is applied everywhere, including to apps on the home screen, because it is the
 * user's global "never show me this" — see [AppOverrideEntity]. Hiding also removes the
 * app from every home screen, so the two cannot contradict each other in storage
 * either; this filter is the backstop.
 */
fun applyOverrides(
    apps: List<LaunchableApp>,
    overrides: Map<String, AppOverrideEntity>
): List<LaunchableApp> = apps.mapNotNull { app ->
    val override = overrides[app.key] ?: return@mapNotNull app
    if (override.hidden) return@mapNotNull null
    app.copy(
        customLabel = override.customLabel,
        categoryOverride = override.categoryId?.let { AppCategory.fromId(it) }
    )
}

/** One thing to draw on the home grid, with the cell that says where. */
sealed interface HomeEntry {
    val cell: HomeCellEntity

    data class App(override val cell: HomeCellEntity, val app: LaunchableApp) : HomeEntry
    data class Widget(override val cell: HomeCellEntity) : HomeEntry
}

/**
 * What the home screen shows for a profile, across all its pages.
 *
 * Until the user takes the layout over, falls back to the profile's category-filtered
 * apps laid out on page one. That makes a new profile immediately useful — the
 * categories *are* the default home screen — and turns placing an app into an override
 * rather than a prerequisite. A launcher showing an empty grid until you pinned
 * something would be broken on first run.
 *
 * [isCustomised] rather than `cells.isEmpty()` because those are different states.
 * Removing the last icon leaves no rows, and inferring the fallback from that would
 * bring back every app the user had just removed — while an empty home screen is
 * precisely what someone using this as a minimal launcher wants.
 *
 * Placed apps deliberately ignore the category filter: an explicit placement outranks
 * a category guess, and it survives a profile whose categories match nothing.
 *
 * Cells whose app is no longer installed are dropped here rather than cleaned up
 * eagerly. Uninstalling does not have to write to the database, and a reinstall gets
 * the icon back — usually what someone reinstalling an app wants.
 *
 * Widget cells are dropped when [liveWidgetIds] does not contain their id, i.e. the
 * host no longer holds that widget. That happens when the provider is uninstalled, and
 * the alternative is an empty rectangle the user cannot explain. The row survives, so
 * reinstalling the provider brings the widget back the same way a reinstalled app
 * returns — its id is still allocated.
 */
fun resolveHomeEntries(
    cells: List<HomeCellEntity>,
    installed: List<LaunchableApp>,
    categoryFiltered: List<LaunchableApp>,
    liveWidgetIds: Set<Int>,
    profileId: String,
    grid: GridSize,
    fallbackLimit: Int,
    isCustomised: Boolean
): List<HomeEntry> {
    val effective = if (isCustomised) {
        cells
    } else {
        cellsForApps(categoryFiltered.take(fallbackLimit), profileId, grid)
    }
    val byKey = installed.associateBy { it.key }
    return effective.mapNotNull { cell ->
        if (cell.isWidget) {
            if (cell.appWidgetId in liveWidgetIds) HomeEntry.Widget(cell) else null
        } else {
            byKey[cell.appKey]?.let { HomeEntry.App(cell, it) }
        }
    }
}

/** True when [app] is somewhere on this profile's home screen. Drives the menu's wording. */
fun isOnHome(cells: List<HomeCellEntity>, app: LaunchableApp): Boolean =
    cells.any { !it.isWidget && it.appKey == app.key }
