package com.tanvoid0.portallauncher.automation

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.tanvoid0.portallauncher.R
import com.tanvoid0.portallauncher.data.AutomationIds
import com.tanvoid0.portallauncher.data.ConfigCodec
import com.tanvoid0.portallauncher.data.DndConfig
import com.tanvoid0.portallauncher.data.DndFilterLevel

/**
 * The system's own Do Not Disturb, set to a level per profile.
 *
 * A different lever from [NotificationFilterAutomation]: that one snoozes specific
 * packages the profile names, this one is the device-wide filter every other app's
 * notifications already respect — the two can run at once without conflicting.
 */
class DndAutomation : Automation {

    override val id: String = AutomationIds.DND

    override val titleRes: Int = R.string.dnd_title

    override val summaryRes: Int = R.string.dnd_summary

    override fun availability(context: Context): AutomationAvailability {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        return if (notificationManager.isNotificationPolicyAccessGranted) {
            AutomationAvailability.Ready
        } else {
            AutomationAvailability.NeedsPermission(
                explanation = context.getString(R.string.dnd_needs_access),
                settingsIntent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            )
        }
    }

    override suspend fun apply(context: Context, configJson: String?) {
        val config = ConfigCodec.decodeOr(configJson, DndConfig())
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.setInterruptionFilter(
            when (config.filterLevel) {
                DndFilterLevel.PriorityOnly -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
                DndFilterLevel.AlarmsOnly -> NotificationManager.INTERRUPTION_FILTER_ALARMS
                DndFilterLevel.TotalSilence -> NotificationManager.INTERRUPTION_FILTER_NONE
            }
        )
    }

    override suspend fun revert(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
    }
}
