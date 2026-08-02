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

@Serializable
data class AppBlockerConfig(
    val blockedPackageNames: List<String> = emptyList(),
    val useOverlay: Boolean = true
)

@Serializable
data class NotificationFilterConfig(
    val allowedPackageNames: List<String>? = null,
    val blockedPackageNames: List<String> = emptyList(),
    val silenceAllExcept: List<String>? = null
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
