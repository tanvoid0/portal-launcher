package com.tanvoid0.portallauncher.automation

import android.app.ActivityManager
import android.content.Context
import com.tanvoid0.portallauncher.PortalLauncherApplication

/**
 * Asks the system to end background processes across the installed app list — a
 * one-shot cleanup for Power Saver, not a service.
 *
 * [ActivityManager.killBackgroundProcesses] only reaches cached/background processes
 * in our own user and cannot touch anything foreground, so calling it broadly is safe:
 * the worst case is an app taking a moment longer to resume from cold next time.
 */
class BackgroundAppTrimmer(private val context: Context) {

    fun trim(excludedPackages: Set<String> = emptySet()) {
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return
        val app = context.applicationContext as PortalLauncherApplication
        val ownPackage = context.packageName
        app.appRepository.launchablePackageNames()
            .filter { it != ownPackage && it !in excludedPackages }
            .forEach { runCatching { activityManager.killBackgroundProcesses(it) } }
    }
}
