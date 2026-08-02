package com.tanvoid0.portallauncher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.util.SizeF

/**
 * The launcher's connection to every widget on its home screen.
 *
 * A third-party launcher **cannot** hold `BIND_APPWIDGET` — it is signature|privileged —
 * so binding goes through [AppWidgetManager.bindAppWidgetIdIfAllowed], and the first
 * refusal is answered with the system consent dialog. That grant is per *app*, not per
 * widget, so the user sees it once and never again. See
 * [com.tanvoid0.portallauncher.ui.launcher.WidgetPlacement].
 *
 * Application-scoped, because widget ids outlive any Activity: an id allocated here and
 * stored in `home_cell` has to still mean the same widget after the launcher is killed
 * and restarted, which is most of the time for a home app.
 */
class LauncherWidgetHost(context: Context) : AppWidgetHost(context, HOST_ID) {

    private val manager: AppWidgetManager = AppWidgetManager.getInstance(context)

    /**
     * Every widget the user could add, grouped-friendly: sorted by app then by label so
     * a picker can section it without sorting again.
     *
     * ponytail: `installedProviders` covers the current user only, so work-profile
     * widgets do not appear. Adding them means walking
     * `getInstalledProvidersForProfile(user)` per profile from `LauncherApps` — worth
     * doing if anyone asks, not worth the surface before then.
     */
    fun providers(): List<AppWidgetProviderInfo> = manager.installedProviders

    /** The provider behind a live widget id, or null once the provider is uninstalled. */
    fun providerInfo(appWidgetId: Int): AppWidgetProviderInfo? =
        manager.getAppWidgetInfo(appWidgetId)

    /**
     * Binds without asking, which only works once the user has granted this app the
     * bind permission through the system dialog. False means "ask them".
     */
    fun bindIfAllowed(appWidgetId: Int, user: UserHandle, provider: ComponentName): Boolean =
        manager.bindAppWidgetIdIfAllowed(appWidgetId, user, provider, null)

    /** Tells the widget how much room it has, so it can pick a layout. */
    fun reportSize(appWidgetId: Int, widthDp: Int, heightDp: Int) {
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                putParcelableArrayList(
                    AppWidgetManager.OPTION_APPWIDGET_SIZES,
                    arrayListOf(SizeF(widthDp.toFloat(), heightDp.toFloat()))
                )
            }
        }
        manager.updateAppWidgetOptions(appWidgetId, options)
    }

    /**
     * Releases every id the host holds that no `home_cell` row names.
     *
     * This is not tidiness. Deleting a profile cascades its cells away in SQLite, and a
     * restore replaces the whole table — neither can call [deleteAppWidgetId], so
     * without a sweep every deleted profile leaves its widgets bound to their providers
     * for the life of the install, invisible and unremovable. Run at start-up, where it
     * catches all of those paths at once instead of one guard per path.
     */
    fun sweepOrphans(keep: Set<Int>) {
        appWidgetIds.filterNot { it in keep }.forEach { deleteAppWidgetId(it) }
    }

    companion object {
        /**
         * Stable across installs and versions. The system keys the host's allocated ids
         * by it, so changing it orphans every widget the user has.
         */
        private const val HOST_ID = 0x504C

        /**
         * How many cells a provider wants, clamped to the grid.
         *
         * API 31 providers declare their preference in cells directly, which is exact.
         * Below that the only signal is a minimum size in dp, so it is divided by the
         * cell size and rounded up — under-sizing a widget clips its content, while
         * over-sizing only costs space.
         */
        fun defaultSpan(
            info: AppWidgetProviderInfo,
            cellWidthDp: Int,
            cellHeightDp: Int,
            columns: Int,
            rows: Int
        ): Pair<Int, Int> {
            val (wantX, wantY) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                info.targetCellWidth > 0 && info.targetCellHeight > 0
            ) {
                info.targetCellWidth to info.targetCellHeight
            } else {
                cellsFor(info.minWidth, cellWidthDp) to cellsFor(info.minHeight, cellHeightDp)
            }
            return wantX.coerceIn(1, columns) to wantY.coerceIn(1, rows)
        }

        /**
         * [AppWidgetProviderInfo.minWidth] is in pixels at the *provider's* density, which
         * for practical purposes is dp — the framework's own launchers treat it that way.
         */
        private fun cellsFor(minDp: Int, cellDp: Int): Int =
            if (cellDp <= 0) 1 else ((minDp + cellDp - 1) / cellDp).coerceAtLeast(1)
    }
}
