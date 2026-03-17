package com.tanvoid0.portallauncher.automation

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Placeholder for per-profile notification filtering.
 * User must enable in Settings > Notifications > Notification access.
 * Phase 3: filter/cancel notifications by profile rules.
 */
class PortalNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // Phase 3: apply profile notification filter
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}
