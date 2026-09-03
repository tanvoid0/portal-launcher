package com.tanvoid0.portallauncher.automation

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.WindowManager
import androidx.core.net.toUri
import com.tanvoid0.portallauncher.data.BrightnessMode
import com.tanvoid0.portallauncher.data.RefreshRateMode
import kotlin.math.abs

/**
 * Writes brightness and refresh rate through [Settings.System], for [DisplayComfortAutomation]
 * and Power Saver.
 *
 * `WRITE_SETTINGS` is a special-access permission (grant screen, not a runtime prompt),
 * and some OEM skins accept the write but never actually apply it — so every write here
 * reads the value straight back and reports whether it stuck, rather than assuming a
 * `putInt` that did not throw means the screen changed.
 */
class DisplaySettingsController(private val context: Context) {

    private val resolver get() = context.contentResolver

    /** True when both the brightness mode and (for Manual) the level stuck. */
    fun applyBrightness(mode: BrightnessMode, percent: Int): Boolean {
        val modeApplied = writeAndVerifyInt(
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            if (mode == BrightnessMode.Auto) {
                Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            } else {
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            }
        )
        if (mode == BrightnessMode.Auto) return modeApplied
        // `and`, not `&&`: still write the level even if the mode write did not stick —
        // reporting one failed write should not cost us the other.
        return modeApplied and writeAndVerifyInt(Settings.System.SCREEN_BRIGHTNESS, brightnessValue(percent))
    }

    /** True when both the min and peak refresh-rate keys stuck. */
    fun applyRefreshRate(mode: RefreshRateMode, customHz: Float): Boolean {
        val (min, peak) = refreshRateTargets(mode, customHz, maxSupportedRefreshRate())
        val minApplied = writeAndVerifyFloat(KEY_MIN_REFRESH_RATE, min)
        val peakApplied = writeAndVerifyFloat(KEY_PEAK_REFRESH_RATE, peak)
        return minApplied && peakApplied
    }

    /** Back to the device deciding — see [DisplayComfortAutomation.revert]. */
    fun revertToDefaults(): Boolean {
        val brightnessOk = applyBrightness(BrightnessMode.Auto, DEFAULT_BRIGHTNESS_PERCENT)
        val refreshOk = applyRefreshRate(RefreshRateMode.Auto, DEFAULT_REFRESH_RATE)
        return brightnessOk && refreshOk
    }

    private fun writeAndVerifyInt(key: String, value: Int): Boolean = runCatching {
        Settings.System.putInt(resolver, key, value)
        Settings.System.getInt(resolver, key, value - 1) == value
    }.getOrDefault(false)

    private fun writeAndVerifyFloat(key: String, value: Float): Boolean = runCatching {
        Settings.System.putFloat(resolver, key, value)
        abs(Settings.System.getFloat(resolver, key, Float.NaN) - value) < EPSILON
    }.getOrDefault(false)

    @Suppress("DEPRECATION") // no per-Activity Display reaches a Context this general below API 30
    private fun maxSupportedRefreshRate(): Float = runCatching {
        context.getSystemService(WindowManager::class.java)
            .defaultDisplay.supportedModes.maxOf { it.refreshRate }
    }.getOrDefault(DEFAULT_REFRESH_RATE)

    companion object {
        fun canWrite(context: Context): Boolean = Settings.System.canWrite(context)

        fun writeSettingsIntent(context: Context): Intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            "package:${context.packageName}".toUri()
        )

        // peak_refresh_rate / min_refresh_rate have written the "Smooth Display" toggle
        // since API 30, but only became named Settings.System constants at API 35 — raw
        // string keys here so this still compiles and works down to our minSdk of 26
        // (canWrite() already gates whether the attempt is offered at all).
        const val KEY_PEAK_REFRESH_RATE = "peak_refresh_rate"
        const val KEY_MIN_REFRESH_RATE = "min_refresh_rate"

        const val DEFAULT_REFRESH_RATE = 60f
        const val DEFAULT_BRIGHTNESS_PERCENT = 50
        private const val EPSILON = 0.5f

        /**
         * Pure: mode + a custom Hz + the display's max → the (min, peak) pair to write.
         * Split out of [applyRefreshRate] so the mapping is testable without a Display.
         */
        fun refreshRateTargets(mode: RefreshRateMode, customHz: Float, maxSupported: Float): Pair<Float, Float> =
            when (mode) {
                RefreshRateMode.Auto -> 0f to maxSupported
                RefreshRateMode.Min -> 0f to 0f
                RefreshRateMode.Max -> maxSupported to maxSupported
                RefreshRateMode.Custom -> customHz to customHz
            }

        /** Pure: 0-100 → the 0-255 range [Settings.System.SCREEN_BRIGHTNESS] stores. */
        fun brightnessValue(percent: Int): Int = (percent.coerceIn(0, 100) * 255) / 100
    }
}
