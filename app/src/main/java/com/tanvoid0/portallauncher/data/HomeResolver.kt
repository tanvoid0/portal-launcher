package com.tanvoid0.portallauncher.data

/**
 * Turns what is installed plus what the user decided into the two lists the launcher
 * shows. Pure functions, so the rules are testable without a device — these decide
 * what appears on someone's home screen, which is the last place for a surprise.
 */

/**
 * Applies per-app overrides: drops hidden apps and attaches renames.
 *
 * Hiding is applied everywhere, including to pinned apps, because it is the user's
 * global "never show me this" — see [AppOverrideEntity]. Hiding also unpins, so the
 * two cannot contradict each other in storage either; this filter is the backstop.
 */
fun applyOverrides(
    apps: List<LaunchableApp>,
    overrides: Map<String, AppOverrideEntity>
): List<LaunchableApp> = apps.mapNotNull { app ->
    val override = overrides[app.key] ?: return@mapNotNull app
    if (override.hidden) return@mapNotNull null
    app.copy(customLabel = override.customLabel)
}

/**
 * The apps on the home screen for a profile.
 *
 * Until the user takes the layout over, falls back to the profile's category-filtered
 * apps. That makes a new profile immediately useful — the categories *are* the default
 * home screen — and turns pinning into an override rather than a prerequisite. A
 * launcher showing an empty grid until you pinned something would be broken on first run.
 *
 * [isCustomised] rather than `pinned.isEmpty()` because those are different states.
 * Unpinning the last app leaves no rows, and inferring the fallback from that would
 * bring back every app the user had just removed — while an empty home screen is
 * precisely what someone using this as a minimal launcher wants.
 *
 * Pinned apps deliberately ignore the category filter: an explicit pin outranks a
 * category guess, and it survives a profile whose categories match nothing.
 *
 * Pins whose app is no longer installed are dropped here rather than cleaned up
 * eagerly. Uninstalling does not have to write to the database, and a reinstall gets
 * the pin back — usually what someone reinstalling an app wants.
 */
fun resolveHomeApps(
    pinned: List<HomeItemEntity>,
    installed: List<LaunchableApp>,
    categoryFiltered: List<LaunchableApp>,
    fallbackLimit: Int,
    isCustomised: Boolean
): List<LaunchableApp> {
    if (!isCustomised) return categoryFiltered.take(fallbackLimit)
    val byKey = installed.associateBy { it.key }
    return pinned
        .sortedBy { it.position }
        .mapNotNull { byKey["${it.packageName}/${it.activityName}/${it.userSerial}"] }
}

/** True when [app] is pinned to [profileId]'s home screen. Drives the menu's wording. */
fun isPinned(pinned: List<HomeItemEntity>, app: LaunchableApp): Boolean =
    pinned.any {
        it.packageName == app.packageName &&
            it.activityName == app.activityName &&
            it.userSerial == app.userSerial
    }

/** The row to store when pinning [app] to [profileId] at [position]. */
fun homeItemFor(app: LaunchableApp, profileId: String, position: Int) = HomeItemEntity(
    profileId = profileId,
    packageName = app.packageName,
    activityName = app.activityName,
    userSerial = app.userSerial,
    position = position
)
