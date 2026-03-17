package com.tanvoid0.portallauncher.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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

data class GreyscaleConfig(
    val enabled: Boolean = false,
    val intensity: Float = 1f
)

data class AppVisibilityConfig(
    val primaryCategoryIds: List<String> = emptyList(),
    val secondaryCategoryIds: List<String> = emptyList(),
    val hiddenCategoryIds: List<String> = emptyList()
)

data class AppBlockerConfig(
    val blockedPackageNames: List<String> = emptyList(),
    val useOverlay: Boolean = true
)

data class NotificationFilterConfig(
    val allowedPackageNames: List<String>? = null,
    val blockedPackageNames: List<String> = emptyList(),
    val silenceAllExcept: List<String>? = null
)

data class ScheduleSlot(
    val startTimeMinutes: Int,
    val endTimeMinutes: Int,
    val profileId: String
)

data class SchedulerConfig(
    val slots: List<ScheduleSlot> = emptyList()
)
