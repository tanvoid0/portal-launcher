package com.tanvoid0.portallauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * One launchable activity belonging to one user. Work-profile apps arrive as
 * entries for a second [UserHandle], which is why the user is part of identity:
 * the same package can legitimately appear twice.
 */
data class LaunchableApp(
    val packageName: String,
    val activityName: String,
    val user: UserHandle,
    /**
     * Stable id for [user], from [UserManager.getSerialNumberForUser]. Carried here so
     * anything that persists a reference to this app — the home layout, the per-app
     * overrides — can do it without a UserManager of its own, and so that reference
     * still resolves after a reboot, which a [UserHandle] would not.
     */
    val userSerial: Long,
    val label: String,
    val isWorkProfile: Boolean,
    /**
     * Raw [android.content.pm.ApplicationInfo.category] — the category the app's own
     * developer declared. Free, exact when present, and [ApplicationInfo.CATEGORY_UNDEFINED]
     * for a large minority of apps. [AppCategorizer] decides what to do with it.
     */
    val systemCategory: Int,
    /** True for apps shipped with the system image; those cannot be uninstalled. */
    val isSystemApp: Boolean = false,
    /** User-chosen name, when they renamed it. [displayLabel] is what UI should show. */
    val customLabel: String? = null,
    /** Category the user assigned by hand. Outranks every automatic source. */
    val categoryOverride: AppCategory? = null
) {
    /** Identity across processes and reboots. Used for list keys and for storage. */
    val key: String get() = "$packageName/$activityName/$userSerial"

    /** The name to show: the user's rename if there is one, otherwise the app's own. */
    val displayLabel: String get() = customLabel?.takeIf { it.isNotBlank() } ?: label
}

/**
 * Source of truth for what is installed.
 *
 * Uses [LauncherApps] rather than [android.content.pm.PackageManager.queryIntentActivities]
 * because it is the API built for launchers: it enumerates every profile the
 * device has (so managed/work profiles work), it exposes activity aliases
 * correctly, and it reports install/remove/update events instead of leaving us
 * with a list that goes stale until the process dies.
 */
class AppRepository(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    /**
     * Emits the current app list, then re-emits whenever a package is added,
     * removed, changed, or becomes (un)available — e.g. work profile paused.
     */
    val apps: Flow<List<LaunchableApp>> = callbackFlow {
        val callback = object : LauncherApps.Callback() {
            private fun refresh() = trySend(loadApps())
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) = refresh().let {}
            override fun onPackageAdded(packageName: String?, user: UserHandle?) = refresh().let {}
            override fun onPackageChanged(packageName: String?, user: UserHandle?) = refresh().let {}
            override fun onPackagesAvailable(
                packageNames: Array<out String>?,
                user: UserHandle?,
                replacing: Boolean
            ) = refresh().let {}

            override fun onPackagesUnavailable(
                packageNames: Array<out String>?,
                user: UserHandle?,
                replacing: Boolean
            ) = refresh().let {}
        }
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        trySend(loadApps())
        awaitClose { launcherApps.unregisterCallback(callback) }
    }.conflate()

    private fun loadApps(): List<LaunchableApp> {
        val me = Process.myUserHandle()
        return userManager.userProfiles
            .flatMap { user ->
                // Throws if a profile is locked or unavailable; an absent profile
                // is not an error, it just contributes nothing this round.
                runCatching { launcherApps.getActivityList(null, user) }
                    .getOrDefault(emptyList())
                    .map { info ->
                        LaunchableApp(
                            packageName = info.componentName.packageName,
                            activityName = info.componentName.className,
                            user = user,
                            userSerial = userManager.getSerialNumberForUser(user),
                            label = info.label?.toString() ?: info.componentName.packageName,
                            isWorkProfile = user != me,
                            systemCategory = info.applicationInfo.category,
                            isSystemApp = info.applicationInfo.flags and
                                ApplicationInfo.FLAG_SYSTEM != 0
                        )
                    }
            }
            .sortedBy { it.label.lowercase() }
    }

    /**
     * Opens the system's app-info screen. Goes through [LauncherApps] rather than an
     * ACTION_APPLICATION_DETAILS_SETTINGS intent because that intent cannot target
     * another user, so it would silently do nothing for a work-profile app.
     */
    fun openAppInfo(app: LaunchableApp) {
        runCatching {
            launcherApps.startAppDetailsActivity(
                ComponentName(app.packageName, app.activityName),
                app.user,
                null,
                null
            )
        }
    }

    /**
     * True when we can offer to uninstall. System apps cannot be removed, and
     * ACTION_DELETE has no way to name a different user, so offering it for a
     * work-profile app would be a button that does nothing.
     */
    fun canUninstall(app: LaunchableApp): Boolean = !app.isSystemApp && !app.isWorkProfile

    /**
     * Resolves the icon drawable, badged if the app belongs to a work profile.
     * Call off the main thread — rendering an adaptive icon is not free.
     *
     * Not memoised here: [IconCache] sits in front of this and caches the rasterised
     * result, which is the expensive part. Drawables are mutable and stateful, so
     * caching them rather than bitmaps would be sharing mutable state between cells.
     */
    fun loadIcon(app: LaunchableApp): Drawable? {
        val info = runCatching { launcherApps.getActivityList(app.packageName, app.user) }
            .getOrDefault(emptyList())
            .firstOrNull { it.componentName.className == app.activityName }
            ?: return null
        val icon = info.getIcon(0) ?: return null
        return if (app.isWorkProfile) {
            context.packageManager.getUserBadgedIcon(icon, app.user)
        } else {
            icon
        }
    }

    /**
     * Launches via [LauncherApps.startMainActivity] rather than a plain
     * startActivity: it is the only way to launch into another profile, and it
     * handles the task flags for us.
     */
    fun launch(app: LaunchableApp) {
        runCatching {
            launcherApps.startMainActivity(
                ComponentName(app.packageName, app.activityName),
                app.user,
                null,
                null
            )
        }
    }

    /** True when the app can be launched right now (profile unlocked, not suspended). */
    fun isLaunchable(app: LaunchableApp): Boolean =
        launcherApps.isActivityEnabled(
            ComponentName(app.packageName, app.activityName),
            app.user
        )
}
