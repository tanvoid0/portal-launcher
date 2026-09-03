package com.tanvoid0.portallauncher.automation

import android.content.Context
import com.tanvoid0.portallauncher.R
import com.tanvoid0.portallauncher.data.AutomationIds
import com.tanvoid0.portallauncher.data.ConfigCodec
import com.tanvoid0.portallauncher.data.DisplayComfortConfig

/**
 * Brightness and refresh rate for the whole device, through [DisplaySettingsController].
 *
 * Unlike [GreyscaleAutomation], this one *can* reach past our own window — brightness
 * and refresh rate are `WRITE_SETTINGS` writes, not a colour matrix on our own surface.
 */
class DisplayComfortAutomation : Automation {

    override val id: String = AutomationIds.DISPLAY_COMFORT

    override val titleRes: Int = R.string.display_comfort_title

    override val summaryRes: Int = R.string.display_comfort_summary

    override fun availability(context: Context): AutomationAvailability =
        if (DisplaySettingsController.canWrite(context)) {
            AutomationAvailability.Ready
        } else {
            AutomationAvailability.NeedsPermission(
                explanation = context.getString(R.string.display_comfort_needs_write_settings),
                settingsIntent = DisplaySettingsController.writeSettingsIntent(context)
            )
        }

    override suspend fun apply(context: Context, configJson: String?) {
        val config = ConfigCodec.decodeOr(configJson, DisplayComfortConfig())
        val controller = DisplaySettingsController(context)
        controller.applyBrightness(config.brightnessMode, config.brightnessPercent)
        controller.applyRefreshRate(config.refreshRateMode, config.refreshRateHz)
    }

    /**
     * Not a snapshot-and-restore — nothing here records what the display was set to
     * before the profile applied, matching every other automation in this package (see
     * [GreyscaleAutomation], [NotificationFilterAutomation]). Auto is simply "back to
     * the device deciding," the same as if no profile had ever touched it.
     */
    override suspend fun revert(context: Context) {
        if (DisplaySettingsController.canWrite(context)) {
            DisplaySettingsController(context).revertToDefaults()
        }
    }
}
