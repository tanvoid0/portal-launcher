package com.tanvoid0.portallauncher.automation

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.provider.Settings
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.Modifier
import com.tanvoid0.portallauncher.R
import com.tanvoid0.portallauncher.data.AutomationIds

/**
 * Drains the colour out of the screen.
 *
 * Two layers, because one of them needs something we cannot grant ourselves:
 *
 * 1. **The launcher's own window**, always. [Modifier.greyscale] is a draw-time colour
 *    filter, so it needs no permission — but it only covers the composition it wraps.
 *    Not the wallpaper (the system draws that *behind* our transparent window) and not
 *    dialogs, dropdowns or bottom sheets, which Compose puts in windows of their own.
 * 2. **The whole device**, when [hasSecureSettings]. The system daltonizer set to
 *    monochromacy greys everything the display shows, including other apps. It lives in
 *    `Settings.Secure`, which needs `WRITE_SECURE_SETTINGS` — a `signature|privileged`
 *    permission no app can request at runtime, but one the user can grant themselves
 *    over ADB with [adbGrantCommand]. One command, once, survives reboots.
 *
 * `AccessibilityService` is not a way around this: it has no colour-transform
 * capability. Granting accessibility access buys nothing here.
 */
class GreyscaleAutomation : Automation {

    override val id: String = AutomationIds.GREYSCALE

    override val titleRes: Int = R.string.greyscale_title

    override val summaryRes: Int = R.string.greyscale_summary

    /**
     * Always ready. Without the ADB grant this still greys the launcher's own screens,
     * so it is never a dead switch — [hasSecureSettings] is what the editor uses to say
     * how far the greyscale actually reaches.
     */
    override fun availability(context: Context): AutomationAvailability =
        AutomationAvailability.Ready

    override suspend fun apply(context: Context, configJson: String?) {
        // The in-app filter is read at draw time by Modifier.greyscale, not pushed
        // anywhere — so all this has to do is the device-wide half.
        if (!hasSecureSettings(context)) return
        val resolver = context.contentResolver
        val prefs = prefs(context)
        // Save what was there before we are the ones who set it — someone running
        // deuteranomaly correction must get it back on revert, not be left with the
        // daltonizer off. Guarded on absence so a re-apply (the engine is idempotent,
        // and a switch can be interrupted) does not overwrite the real previous state
        // with our own monochromacy.
        if (!prefs.contains(KEY_PREV_ENABLED)) {
            prefs.edit()
                .putInt(KEY_PREV_ENABLED, secureInt(resolver, ENABLED, 0))
                .putInt(KEY_PREV_MODE, secureInt(resolver, MODE, MODE_DISABLED))
                .apply()
        }
        Settings.Secure.putInt(resolver, MODE, MODE_MONOCHROMACY)
        Settings.Secure.putInt(resolver, ENABLED, 1)
    }

    override suspend fun revert(context: Context) {
        if (!hasSecureSettings(context)) return
        val prefs = prefs(context)
        // Nothing saved means we never turned it on — leave the user's setting alone.
        if (!prefs.contains(KEY_PREV_ENABLED)) return
        val resolver = context.contentResolver
        Settings.Secure.putInt(resolver, MODE, prefs.getInt(KEY_PREV_MODE, MODE_DISABLED))
        Settings.Secure.putInt(resolver, ENABLED, prefs.getInt(KEY_PREV_ENABLED, 0))
        prefs.edit().remove(KEY_PREV_ENABLED).remove(KEY_PREV_MODE).apply()
    }

    companion object {
        /** `Settings.Secure.ACCESSIBILITY_DISPLAY_DALTONIZER_ENABLED` — not public API. */
        private const val ENABLED = "accessibility_display_daltonizer_enabled"

        /** `Settings.Secure.ACCESSIBILITY_DISPLAY_DALTONIZER` — not public API. */
        private const val MODE = "accessibility_display_daltonizer"

        /** `AccessibilityManager.DALTONIZER_CORRECT_DEUTERANOMALY` and friends: 0 is grey. */
        private const val MODE_MONOCHROMACY = 0

        private const val MODE_DISABLED = -1

        private const val PREFS = "greyscale_daltonizer"
        private const val KEY_PREV_ENABLED = "previous_enabled"
        private const val KEY_PREV_MODE = "previous_mode"

        /** Whether the ADB grant has been done, i.e. whether we can grey the whole device. */
        fun hasSecureSettings(context: Context): Boolean =
            context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED

        /**
         * The one command the user runs from a computer with the phone plugged in.
         *
         * Package name comes from the context rather than a literal so debug builds with
         * an application id suffix print a command that actually works.
         */
        fun adbGrantCommand(context: Context): String =
            "adb shell pm grant ${context.packageName} " +
                "android.permission.WRITE_SECURE_SETTINGS"

        private fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        private fun secureInt(resolver: ContentResolver, key: String, default: Int): Int =
            Settings.Secure.getInt(resolver, key, default)
    }
}

/**
 * Desaturates everything drawn inside, [saturation] running 1 (untouched) to 0 (grey).
 *
 * Uses a saved layer with a `ColorMatrixColorFilter` rather than `RenderEffect`, which
 * would need API 31 — this has to work from our minSdk of 26.
 */
fun Modifier.greyscale(saturation: Float): Modifier {
    if (saturation >= 1f) return this
    return drawWithCache {
        val paint = Paint().apply {
            asFrameworkPaint().colorFilter = ColorMatrixColorFilter(
                ColorMatrix().apply { setSaturation(saturation.coerceIn(0f, 1f)) }
            )
        }
        val bounds = Rect(Offset.Zero, size)
        onDrawWithContent {
            drawIntoCanvas { it.saveLayer(bounds, paint) }
            drawContent()
            drawIntoCanvas { it.restore() }
        }
    }
}
