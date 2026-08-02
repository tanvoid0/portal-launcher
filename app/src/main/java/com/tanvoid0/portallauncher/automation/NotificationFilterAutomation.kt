package com.tanvoid0.portallauncher.automation

import android.content.Context
import com.tanvoid0.portallauncher.data.AutomationIds

/**
 * Per-profile notification rules.
 *
 * The work happens in [PortalNotificationListenerService], which the system binds and
 * which reads the active profile itself — so there is nothing for [apply] to push. This
 * class exists to report *availability*, which is the part the user has to act on: the
 * grant is made in another app's UI, and until it is made, switching this on in a
 * profile would do exactly nothing.
 */
class NotificationFilterAutomation : Automation {

    override val id: String = AutomationIds.NOTIFICATION_FILTER

    override val title: String = "Hold back notifications"

    override val summary: String =
        "Snoozes notifications the active profile does not want. They come back when " +
            "the profile changes."

    override fun availability(context: Context): AutomationAvailability =
        if (PortalNotificationListenerService.hasAccess(context)) {
            AutomationAvailability.Ready
        } else {
            AutomationAvailability.NeedsPermission(
                explanation = "Portal needs notification access to hold notifications " +
                    "back. It reads which app sent a notification, on the device — " +
                    "notification contents are never stored or sent anywhere.",
                settingsIntent = PortalNotificationListenerService.settingsIntent()
            )
        }

    // Nothing to push: the listener service observes the active profile directly, so it
    // is already applying whatever is current the moment the profile changes.
    override suspend fun apply(context: Context, configJson: String?) = Unit

    /**
     * Deliberately does not un-snooze. Snoozed notifications return on their own, and
     * force-posting them the instant a profile ends would dump a session's worth of
     * interruptions on the user at once — the opposite of what they asked for.
     */
    override suspend fun revert(context: Context) = Unit
}
