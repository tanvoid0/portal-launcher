package com.tanvoid0.portallauncher.data

/**
 * One profile the launcher ships with, before it becomes a row in the database.
 *
 * A built-in is data, not a code path: nothing in the launcher branches on which
 * profile is active, it only reads the categories the active profile marks primary.
 * That is why the user can rename, re-scope or delete any of these and why adding
 * an eighth is one entry in [BuiltInProfiles.all].
 */
data class BuiltInProfile(
    val id: String,
    val name: String,
    val type: ProfileType,
    /** Categories on the home screen. Empty means every app, which is the default. */
    val primaryCategories: List<AppCategory>
)

/**
 * The seven profiles from the product spec, plus the unfiltered default.
 *
 * Each one is a set of [AppCategory] values, so a profile is only ever as good as
 * the categoriser under it. Study, Social, Productivity and Gaming are well served
 * by `ApplicationInfo.category` and the keyword rules. **Wellness and Driving are
 * not** — there is no system category for health, the keyword list knows a dozen
 * fitness and navigation packages out of thousands, and the rest of the long tail
 * resolves to [AppCategory.Other]. Those two profiles are the reason the on-device
 * model exists: it is asked about exactly the packages nothing free could place.
 *
 * With the model switched off, every profile still works — Wellness and Driving
 * just show fewer apps, which is a smaller home screen rather than a broken one.
 */
object BuiltInProfiles {

    /** Shows everything. Seeded active, and the profile to fall back to. */
    const val DEFAULT_ID = "default"

    val all: List<BuiltInProfile> = listOf(
        BuiltInProfile(DEFAULT_ID, "All apps", ProfileType.Custom, emptyList()),

        // Study: learning material only. The spec's "show only study apps".
        BuiltInProfile("study", "Study", ProfileType.Study, listOf(AppCategory.Study)),

        // Social: the communication apps, prominent. Everything else stays in the
        // drawer — the spec is explicit that this profile hides nothing.
        BuiltInProfile("social", "Social", ProfileType.Social, listOf(AppCategory.Social)),

        // Productivity: work tools *and* study, because the rules file resolves
        // Drive, Docs and Calendar to Study. Dropping Study here would leave a work
        // profile with mail and Slack and no documents.
        BuiltInProfile(
            "productivity",
            "Productivity",
            ProfileType.Productivity,
            listOf(AppCategory.Productivity, AppCategory.Study)
        ),

        // Gaming: games plus the apps that come with them — Discord and the stores
        // both land in Social.
        BuiltInProfile(
            "gaming",
            "Gaming",
            ProfileType.Gaming,
            listOf(AppCategory.Gaming, AppCategory.Social)
        ),

        // Focus: the narrowest of the three work-shaped profiles — work tools with
        // the reference material taken away too. What separates it from Study is
        // scope, not machinery.
        BuiltInProfile("focus", "Focus", ProfileType.Focus, listOf(AppCategory.Productivity)),

        // Wellness: wind-down. Health and sleep apps, no social, no games.
        BuiltInProfile("wellness", "Wellness", ProfileType.Wellness, listOf(AppCategory.Wellness)),

        // Driving: maps and the road, deliberately almost empty. Phone and Messages
        // stay reachable regardless — the dock ignores the profile filter.
        BuiltInProfile("driving", "Driving", ProfileType.Driving, listOf(AppCategory.Driving))
    )

    /**
     * Automations every built-in switches on. App visibility is the only automation
     * that exists today.
     *
     * ponytail: greyscale, blocker and notification filter belong on Study, Focus
     * and Wellness per the spec, but listing an id the engine cannot apply yet is a
     * silent no-op in the profile editor. Add each id here with the automation.
     */
    val enabledAutomationIds: List<String> = listOf(AutomationIds.APP_VISIBILITY)

    fun visibilityConfig(profile: BuiltInProfile): AppVisibilityConfig =
        AppVisibilityConfig(primaryCategoryIds = profile.primaryCategories.map { it.id })
}
