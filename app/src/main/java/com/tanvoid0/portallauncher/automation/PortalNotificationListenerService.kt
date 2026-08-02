package com.tanvoid0.portallauncher.automation

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.tanvoid0.portallauncher.PortalLauncherApplication
import com.tanvoid0.portallauncher.data.AutomationIds
import com.tanvoid0.portallauncher.data.ConfigCodec
import com.tanvoid0.portallauncher.data.NotificationFilterConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Applies the active profile's notification rules as notifications arrive.
 *
 * Three things here are deliberate:
 *
 * **Snooze, not cancel, by default.** Cancelling destroys a notification: the user never
 * sees it and there is nothing to go back to. A profile is a temporary state, so the
 * default holds a notification back rather than throwing it away.
 * [NotificationFilterConfig.cancelOnFilter] exists for people who want it gone, and the
 * profile editor has to say plainly what that does.
 *
 * **Ongoing and foreground-service notifications are never touched.** Those are the
 * media player, an active call, a running navigation. Suppressing them breaks the phone
 * rather than quieting it.
 *
 * **The rules are cached in memory.** [onNotificationPosted] runs on the main thread for
 * every notification on the device; reading a database there would jank the shade.
 */
class PortalNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val config = MutableStateFlow(NotificationFilterConfig())
    private val enabled = MutableStateFlow(false)

    override fun onCreate() {
        super.onCreate()
        val app = application as? PortalLauncherApplication ?: return
        app.activeProfileSource.activeConfigs
            .onEach { active ->
                enabled.value = active.isEnabled(AutomationIds.NOTIFICATION_FILTER)
                config.value = ConfigCodec.decodeOr(
                    active.configJson(AutomationIds.NOTIFICATION_FILTER),
                    NotificationFilterConfig()
                )
            }
            .launchIn(scope)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val posted = sbn ?: return
        if (!enabled.value) return
        if (posted.isOngoing) return
        if (posted.notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return
        if (!shouldHoldBack(posted.packageName, config.value)) return

        val cancel = config.value.cancelOnFilter
        scope.launch {
            runCatching {
                if (cancel) {
                    cancelNotification(posted.key)
                } else {
                    snoozeNotification(posted.key, SNOOZE_MILLIS)
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = Unit

    companion object {
        /** Two hours: covers a focus session without losing things for a whole day. */
        private const val SNOOZE_MILLIS = 2L * 60 * 60 * 1000

        /**
         * Whether the user has granted notification access.
         *
         * Read from `Settings.Secure` rather than assumed: the grant happens in another
         * app's UI, which returns no result, so the only reliable answer is to look.
         */
        fun hasAccess(context: Context): Boolean {
            val listeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            return listeners.split(':').any {
                ComponentName.unflattenFromString(it)?.packageName == context.packageName
            }
        }

        fun settingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }
}

/**
 * Whether a notification from [packageName] should be held back.
 *
 * An allow-list, when present, wins outright and the block-list is not consulted:
 * "only let these through" is a stronger and clearer statement than "keep these out",
 * and quietly combining the two is how a filter ends up hiding a phone call.
 *
 * Pure and package-level so it is testable without a StatusBarNotification, which
 * cannot be constructed off-device.
 */
fun shouldHoldBack(packageName: String, config: NotificationFilterConfig): Boolean {
    config.allowedPackageNames?.let { allowed -> return packageName !in allowed }
    return packageName in config.blockedPackageNames
}
