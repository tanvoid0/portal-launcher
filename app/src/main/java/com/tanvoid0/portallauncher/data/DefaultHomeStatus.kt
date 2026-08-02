package com.tanvoid0.portallauncher.data

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

/**
 * Whether we are the device's home app, and how to ask to become it.
 *
 * Two mechanisms, because there is no single one that spans our minSdk:
 *  - API 29+ has [RoleManager.ROLE_HOME], which shows a proper system chooser.
 *  - API 26–28 has no request API at all, so the best available is to open the
 *    home-app settings screen and let the user pick.
 */
object DefaultHomeStatus {

    /** True when this package currently resolves as the home activity. */
    fun isDefaultHome(context: Context): Boolean {
        val resolved = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /**
     * Intent that asks the user to make us the home app, or null if the platform
     * says the request is pointless (role unavailable, or already held).
     *
     * On API 29+ this is the role request dialog. Below that it is the settings
     * screen — the user has to make the choice there themselves, so any caller
     * must re-check [isDefaultHome] when it regains focus rather than assuming
     * a result came back.
     */
    fun requestIntent(context: Context): Intent? {
        if (isDefaultHome(context)) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }
}
