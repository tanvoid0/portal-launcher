package com.tanvoid0.portallauncher.data

import com.tanvoid0.portallauncher.R

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
    /**
     * Display-name resource. Resolved to a string once, at seed time, by the caller of
     * [com.tanvoid0.portallauncher.data.ProfileRepository.seedBuiltInProfiles] — after
     * that the name is the user's data, exactly like a rename.
     */
    val nameRes: Int,
    val type: ProfileType,
    /** Categories on the home screen. Empty means every app, which is the default. */
    val primaryCategories: List<AppCategory>,
    /**
     * Automations this built-in switches on besides [AutomationIds.APP_VISIBILITY],
     * which every profile gets regardless. Empty for a profile that is purely a
     * category filter.
     */
    val automations: List<BuiltInAutomation> = emptyList()
)

/** One automation id + the config JSON to seed it with, for [BuiltInProfile.automations]. */
data class BuiltInAutomation(val automationId: String, val configJson: String)

/**
 * The built-in profiles from the product spec, plus the unfiltered default.
 *
 * Most are a set of [AppCategory] values, so a profile is only ever as good as the
 * categoriser under it. Study, Social, Productivity and Gaming are well served by
 * `ApplicationInfo.category` and the keyword rules. **Wellness and Driving are not** —
 * there is no system category for health, the keyword list knows a dozen fitness and
 * navigation packages out of thousands, and the rest of the long tail resolves to
 * [AppCategory.Other]. Those two profiles are the reason the on-device model exists: it
 * is asked about exactly the packages nothing free could place.
 *
 * With the model switched off, every profile still works — Wellness and Driving just
 * show fewer apps, which is a smaller home screen rather than a broken one.
 *
 * The device-tuning profiles added alongside this comment (Streaming, Outdoors,
 * Performance, Power Saver, Ultra Saver) are not category filters at all — they leave
 * every app on the home screen and instead compose automations that change how the
 * device itself behaves, so [primaryCategories] is deliberately empty for them.
 */
object BuiltInProfiles {

    /** Shows everything. Seeded active, and the profile to fall back to. */
    const val DEFAULT_ID = "default"

    /**
     * The ids this object shipped with before the device-tuning profiles below. Frozen
     * here rather than derived from [all]: it is what [ProfileRepository.seedBuiltInProfiles]
     * needs to tell "already decided, possibly since deleted" from "never seeded" on an
     * install that ran before this list grew — see its `alreadySeeded` parameter.
     */
    val ORIGINAL_IDS: Set<String> = setOf(
        DEFAULT_ID, "study", "social", "productivity", "gaming", "focus", "wellness", "driving"
    )

    val all: List<BuiltInProfile> = listOf(
        BuiltInProfile(DEFAULT_ID, R.string.profile_all_apps, ProfileType.Custom, emptyList()),

        // Study: learning material only, plus greyscale and a quieter notification
        // policy — the spec's "show only study apps, minimise distraction".
        BuiltInProfile(
            "study",
            R.string.profile_study,
            ProfileType.Study,
            listOf(AppCategory.Study),
            automations = listOf(
                BuiltInAutomation(AutomationIds.GREYSCALE, ConfigCodec.encode(GreyscaleConfig(enabled = true))),
                BuiltInAutomation(
                    AutomationIds.DND,
                    ConfigCodec.encode(DndConfig(filterLevel = DndFilterLevel.PriorityOnly))
                )
            )
        ),

        // Social: the communication apps, prominent. Everything else stays in the
        // drawer — the spec is explicit that this profile hides nothing.
        BuiltInProfile("social", R.string.profile_social, ProfileType.Social, listOf(AppCategory.Social)),

        // Productivity: work tools *and* study, because the rules file resolves
        // Drive, Docs and Calendar to Study. Dropping Study here would leave a work
        // profile with mail and Slack and no documents.
        BuiltInProfile(
            "productivity",
            R.string.profile_productivity,
            ProfileType.Productivity,
            listOf(AppCategory.Productivity, AppCategory.Study)
        ),

        // Gaming: games plus the apps that come with them — Discord and the stores
        // both land in Social.
        BuiltInProfile(
            "gaming",
            R.string.profile_gaming,
            ProfileType.Gaming,
            listOf(AppCategory.Gaming, AppCategory.Social)
        ),

        // Focus: the narrowest of the three work-shaped profiles — work tools with
        // the reference material taken away too. What separates it from Study is
        // scope, not machinery.
        BuiltInProfile(
            "focus",
            R.string.profile_focus,
            ProfileType.Focus,
            listOf(AppCategory.Productivity)
        ),

        // Wellness: wind-down. Health and sleep apps, no social, no games.
        BuiltInProfile(
            "wellness",
            R.string.profile_wellness,
            ProfileType.Wellness,
            listOf(AppCategory.Wellness)
        ),

        // Driving: maps and the road, deliberately almost empty. Phone and Messages
        // stay reachable regardless — the dock ignores the profile filter.
        BuiltInProfile(
            "driving",
            R.string.profile_driving,
            ProfileType.Driving,
            listOf(AppCategory.Driving)
        ),

        // Streaming: every app stays on screen, the display just runs at its smoothest.
        BuiltInProfile(
            "streaming",
            R.string.profile_streaming,
            ProfileType.Custom,
            emptyList(),
            automations = listOf(
                BuiltInAutomation(
                    AutomationIds.DISPLAY_COMFORT,
                    ConfigCodec.encode(DisplayComfortConfig(refreshRateMode = RefreshRateMode.Max))
                )
            )
        ),

        // Outdoors: brighter than Auto usually goes, to fight sunlight glare.
        BuiltInProfile(
            "outdoors",
            R.string.profile_outdoors,
            ProfileType.Custom,
            emptyList(),
            automations = listOf(
                BuiltInAutomation(
                    AutomationIds.DISPLAY_COMFORT,
                    ConfigCodec.encode(
                        DisplayComfortConfig(brightnessMode = BrightnessMode.Manual, brightnessPercent = 100)
                    )
                )
            )
        ),

        // Performance: max refresh for the smoothest touch response. No background
        // trim automation — see PRODUCTION_PLAN's note on this profile; ProfileEngine
        // stays data-driven with no per-profile-id branching, and the OS's own
        // low-memory killer already reclaims what a game actually needs.
        BuiltInProfile(
            "performance",
            R.string.profile_performance,
            ProfileType.Custom,
            emptyList(),
            automations = listOf(
                BuiltInAutomation(
                    AutomationIds.DISPLAY_COMFORT,
                    ConfigCodec.encode(DisplayComfortConfig(refreshRateMode = RefreshRateMode.Max))
                )
            )
        ),

        // Power Saver: trims background apps and dims the display. Standard intensity.
        BuiltInProfile(
            "power_saver",
            R.string.profile_power_saver,
            ProfileType.Custom,
            emptyList(),
            automations = listOf(
                BuiltInAutomation(
                    AutomationIds.POWER_SAVER,
                    ConfigCodec.encode(PowerSaverConfig(intensity = PowerSaverIntensity.Standard))
                )
            )
        ),

        // Ultra Saver: Power Saver's Ultra intensity plus the app blocker in allowlist
        // mode, both enabled on the same profile so ProfileEngine's ordinary diffing
        // applies and reverts them together — see PowerSaverAutomation's doc comment
        // for why that composition cannot happen from inside apply() instead. An empty
        // allowlist blocks everything BlockerService's neverBlock set does not already
        // exempt — the launcher, the phone dialer, and the default SMS and camera apps,
        // resolved live rather than hardcoded, since none of those vary by profile.
        BuiltInProfile(
            "ultra_saver",
            R.string.profile_ultra_saver,
            ProfileType.Custom,
            emptyList(),
            automations = listOf(
                BuiltInAutomation(
                    AutomationIds.POWER_SAVER,
                    ConfigCodec.encode(PowerSaverConfig(intensity = PowerSaverIntensity.Ultra))
                ),
                BuiltInAutomation(
                    AutomationIds.APP_BLOCKER,
                    ConfigCodec.encode(AppBlockerConfig(mode = BlockerMode.AllowlistOnly))
                )
            )
        ),

        // Distraction-Free: a handful of essential apps on screen, everything else
        // held back three ways at once — blocked outright, notifications snoozed, and
        // the device's own Do Not Disturb on top. Every piece here is an automation
        // that already exists; this profile is only the wiring.
        BuiltInProfile(
            "distraction_free",
            R.string.profile_distraction_free,
            ProfileType.Custom,
            listOf(AppCategory.Productivity),
            automations = listOf(
                BuiltInAutomation(
                    AutomationIds.APP_BLOCKER,
                    ConfigCodec.encode(AppBlockerConfig(mode = BlockerMode.Blocklist))
                ),
                BuiltInAutomation(
                    AutomationIds.NOTIFICATION_FILTER,
                    ConfigCodec.encode(NotificationFilterConfig(allowedPackageNames = emptyList()))
                ),
                BuiltInAutomation(
                    AutomationIds.DND,
                    ConfigCodec.encode(DndConfig(filterLevel = DndFilterLevel.PriorityOnly))
                )
            )
        )
    )

    fun visibilityConfig(profile: BuiltInProfile): AppVisibilityConfig =
        AppVisibilityConfig(primaryCategoryIds = profile.primaryCategories.map { it.id })
}
