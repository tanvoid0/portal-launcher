package com.tanvoid0.portallauncher.automation

import android.content.Context
import com.tanvoid0.portallauncher.R
import com.tanvoid0.portallauncher.data.AutomationIds
import com.tanvoid0.portallauncher.data.BrightnessMode
import com.tanvoid0.portallauncher.data.ConfigCodec
import com.tanvoid0.portallauncher.data.PowerSaverConfig
import com.tanvoid0.portallauncher.data.RefreshRateMode

/**
 * Standard: trims background apps and, where allowed, nudges the display down.
 *
 * Ultra additionally wants "block everything but a few essentials", which is exactly
 * [AppBlockerAutomation] under [com.tanvoid0.portallauncher.data.BlockerMode.AllowlistOnly]
 * rather than a second overlay/polling mechanism — see the "Ultra Saver" entry in
 * [com.tanvoid0.portallauncher.data.BuiltInProfiles], which enables both automations on
 * the same profile so [ProfileEngine]'s normal apply/revert diffing runs them together.
 * That composition cannot live here: [BlockerService] gates on the *profile's own*
 * `enabledAutomationIds`, not on whether [AppBlockerAutomation.apply] was called, so an
 * apply()-time-only trigger from this class would start the service and have it stop
 * itself on the very next config read.
 */
class PowerSaverAutomation : Automation {

    override val id: String = AutomationIds.POWER_SAVER

    override val titleRes: Int = R.string.power_saver_title

    override val summaryRes: Int = R.string.power_saver_summary

    // Always ready: killBackgroundProcesses needs only a normal manifest permission,
    // and the display nudge is skipped rather than blocked when WRITE_SETTINGS is not
    // granted — see apply(). Ultra's allowlist blocking reports its own permission
    // needs through AppBlockerAutomation's own row when that profile enables it.
    override fun availability(context: Context): AutomationAvailability = AutomationAvailability.Ready

    override suspend fun apply(context: Context, configJson: String?) {
        val config = ConfigCodec.decodeOr(configJson, PowerSaverConfig())
        BackgroundAppTrimmer(context).trim(config.excludedPackages)
        if (DisplaySettingsController.canWrite(context)) {
            val controller = DisplaySettingsController(context)
            controller.applyRefreshRate(RefreshRateMode.Min, 0f)
            controller.applyBrightness(BrightnessMode.Manual, LOW_BRIGHTNESS_PERCENT)
        }
    }

    /**
     * Restores the display nudge, same "back to Auto, not a snapshot" pattern as
     * [DisplayComfortAutomation.revert]. The background trim has nothing to undo — a
     * killed background process simply relaunches next time its app is opened.
     */
    override suspend fun revert(context: Context) {
        if (DisplaySettingsController.canWrite(context)) {
            DisplaySettingsController(context).revertToDefaults()
        }
    }

    private companion object {
        const val LOW_BRIGHTNESS_PERCENT = 30
    }
}
