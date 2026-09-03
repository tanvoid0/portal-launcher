package com.tanvoid0.portallauncher.automation

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.net.toUri
import com.tanvoid0.portallauncher.R
import com.tanvoid0.portallauncher.data.AutomationIds
import com.tanvoid0.portallauncher.data.BlockerMode

/**
 * Blocks chosen apps while the profile is active, by watching the foreground app and
 * covering blocked ones with a pause screen.
 *
 * **Usage access, not an AccessibilityService.** Both can tell which app came to the
 * front; accessibility is instant but carries a Play policy declaration and is a common
 * rejection cause, while usage-access polling costs ~1s of latency and a little battery.
 * One rejected release costs more than a second of latency — see PRODUCTION_PLAN §1.2.
 * The service itself is [BlockerService]; this class is the availability gate and the
 * engine's start/stop lever.
 */
class AppBlockerAutomation : Automation {

    override val id: String = AutomationIds.APP_BLOCKER

    override val titleRes: Int = R.string.blocker_title

    override val summaryRes: Int = R.string.blocker_summary

    /**
     * Two grants, asked for one at a time in this order: usage access to *notice* a
     * blocked app, then the overlay to *do* something about it. Each grant lives in a
     * different system screen, so one card per missing permission beats one card
     * pointing at two places.
     */
    override fun availability(context: Context): AutomationAvailability {
        if (!hasUsageAccess(context)) {
            return AutomationAvailability.NeedsPermission(
                explanation = context.getString(R.string.blocker_needs_usage),
                settingsIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            )
        }
        if (!Settings.canDrawOverlays(context)) {
            return AutomationAvailability.NeedsPermission(
                explanation = context.getString(R.string.blocker_needs_overlay),
                settingsIntent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:${context.packageName}".toUri()
                )
            )
        }
        return AutomationAvailability.Ready
    }

    override suspend fun apply(context: Context, configJson: String?) {
        // The service reads its own config from ActiveProfileSource, so a config edit
        // takes effect without a restart. May throw on API 31+ if we are truly in the
        // background; the engine wraps every apply, and the next foreground transition
        // re-runs it.
        context.startForegroundService(Intent(context, BlockerService::class.java))
    }

    override suspend fun revert(context: Context) {
        context.stopService(Intent(context, BlockerService::class.java))
    }

    companion object {
        /**
         * Usage access is an app-op, not a runtime permission, so it is checked through
         * AppOpsManager. MODE_DEFAULT means "fall back to the manifest permission",
         * which some OEM builds return instead of deciding.
         */
        fun hasUsageAccess(context: Context): Boolean {
            val appOps = context.getSystemService(AppOpsManager::class.java)
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            return mode == AppOpsManager.MODE_ALLOWED || (
                mode == AppOpsManager.MODE_DEFAULT &&
                    context.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                    PackageManager.PERMISSION_GRANTED
                )
        }
    }
}

/**
 * The block/allow decision, kept pure so it can be tested without a device.
 *
 * [allowedUntil] holds per-package reprieves: a "5 more minutes" snooze is `now + 5min`,
 * an "unlock for this session" is [Long.MAX_VALUE]. The map lives in the service, so
 * both kinds end when the service does — which is what "session" means here.
 */
object BlockerPolicy {

    const val SNOOZE_MILLIS: Long = 5 * 60 * 1000

    fun shouldBlock(
        packageName: String?,
        blocked: Set<String>,
        allowedUntil: Map<String, Long>,
        nowMillis: Long,
        neverBlock: Set<String>,
        mode: BlockerMode = BlockerMode.Blocklist
    ): Boolean {
        if (packageName == null) return false
        if (packageName in neverBlock) return false
        // Blocklist: block the named packages. AllowlistOnly: block everything else —
        // [blocked] is the same field, read as the surviving set instead (see
        // AppBlockerConfig.blockedPackageNames).
        val targeted = when (mode) {
            BlockerMode.Blocklist -> packageName in blocked
            BlockerMode.AllowlistOnly -> packageName !in blocked
        }
        if (!targeted) return false
        val until = allowedUntil[packageName] ?: return true
        return nowMillis >= until
    }
}
