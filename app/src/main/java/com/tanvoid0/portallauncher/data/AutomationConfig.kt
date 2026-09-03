package com.tanvoid0.portallauncher.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlinx.serialization.Serializable

object AutomationIds {
    const val GREYSCALE = "greyscale"
    const val APP_VISIBILITY = "app_visibility"
    const val APP_BLOCKER = "app_blocker"
    const val NOTIFICATION_FILTER = "notification_filter"
    const val SCHEDULER = "scheduler"
    const val QUICK_SWITCH = "quick_switch"
    const val DISPLAY_COMFORT = "display_comfort"
    const val DND = "dnd"
    const val POWER_SAVER = "power_saver"
}

@Entity(
    tableName = "automation_config",
    primaryKeys = ["profileId", "automationId"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId")]
)
data class AutomationConfigEntity(
    val profileId: String,
    val automationId: String,
    val configJson: String
)

// Every config is @Serializable and stored in AutomationConfigEntity.configJson via
// ConfigCodec. Before this, only AppVisibilityConfig had a hand-written string
// format and the other five had no way to be persisted at all.
//
// Defaults on every field are load-bearing: with ConfigCodec's ignoreUnknownKeys,
// they are what lets an older build read a config written by a newer one, and a
// newer build read one written before a field existed.

@Serializable
data class GreyscaleConfig(
    val enabled: Boolean = false,
    val intensity: Float = 1f
)

@Serializable
data class AppVisibilityConfig(
    val primaryCategoryIds: List<String> = emptyList(),
    val secondaryCategoryIds: List<String> = emptyList(),
    val hiddenCategoryIds: List<String> = emptyList()
)

/** Blocklist covers what it names; AllowlistOnly inverts that — see [BlockerPolicy]. */
@Serializable
enum class BlockerMode { Blocklist, AllowlistOnly }

@Serializable
data class AppBlockerConfig(
    /**
     * Under [BlockerMode.Blocklist] (the default), these are the packages paused.
     * Under [BlockerMode.AllowlistOnly] the same field flips meaning to the packages
     * left *un*paused — everything else is. One field rather than two, so a config
     * never has to say which list is the live one.
     */
    val blockedPackageNames: List<String> = emptyList(),
    val useOverlay: Boolean = true,
    val mode: BlockerMode = BlockerMode.Blocklist
)

@Serializable
data class NotificationFilterConfig(
    /**
     * When set, *only* these packages get through and [blockedPackageNames] is ignored.
     * A stronger, clearer statement than a block-list; combining the two silently is how
     * a filter ends up hiding a phone call.
     */
    val allowedPackageNames: List<String>? = null,
    val blockedPackageNames: List<String> = emptyList(),
    /**
     * False means snooze — the notification comes back when the profile ends. True
     * destroys it. Defaults to the recoverable option, and the editor must say which
     * is which, because a user who loses a message will not forgive the app for it.
     */
    val cancelOnFilter: Boolean = false
)

@Serializable
data class ScheduleSlot(
    val startTimeMinutes: Int,
    val endTimeMinutes: Int,
    val profileId: String
)

@Serializable
data class SchedulerConfig(
    val slots: List<ScheduleSlot> = emptyList()
)

@Serializable
enum class BrightnessMode { Auto, Manual }

@Serializable
enum class RefreshRateMode { Auto, Min, Max, Custom }

@Serializable
data class DisplayComfortConfig(
    val brightnessMode: BrightnessMode = BrightnessMode.Auto,
    val brightnessPercent: Int = 50,
    val refreshRateMode: RefreshRateMode = RefreshRateMode.Auto,
    val refreshRateHz: Float = 60f
)

@Serializable
enum class DndFilterLevel { PriorityOnly, AlarmsOnly, TotalSilence }

@Serializable
data class DndConfig(val filterLevel: DndFilterLevel = DndFilterLevel.PriorityOnly)

@Serializable
enum class PowerSaverIntensity { Standard, Ultra }

@Serializable
data class PowerSaverConfig(
    val intensity: PowerSaverIntensity = PowerSaverIntensity.Standard,
    /**
     * Packages [com.tanvoid0.portallauncher.automation.BackgroundAppTrimmer] leaves
     * running. Not consulted by Ultra's app allowlist — that runs through
     * [AppBlockerConfig.mode] on its own config row.
     */
    val excludedPackages: Set<String> = emptySet()
)
