package com.tanvoid0.portallauncher.automation

import com.tanvoid0.portallauncher.data.RefreshRateMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The mode → target-Hz mapping and the percent → SCREEN_BRIGHTNESS conversion, kept
 * pure so they can be pinned down without a Display or a Settings.System write.
 */
class DisplaySettingsControllerTest {

    private val maxSupported = 120f

    @Test
    fun `auto lets the device range from zero up to its max`() {
        assertEquals(
            0f to maxSupported,
            DisplaySettingsController.refreshRateTargets(RefreshRateMode.Auto, 90f, maxSupported)
        )
    }

    @Test
    fun `min and max pin both keys to the same rate`() {
        assertEquals(0f to 0f, DisplaySettingsController.refreshRateTargets(RefreshRateMode.Min, 90f, maxSupported))
        assertEquals(
            maxSupported to maxSupported,
            DisplaySettingsController.refreshRateTargets(RefreshRateMode.Max, 90f, maxSupported)
        )
    }

    @Test
    fun `custom pins both keys to the chosen rate, ignoring the device max`() {
        assertEquals(
            90f to 90f,
            DisplaySettingsController.refreshRateTargets(RefreshRateMode.Custom, 90f, maxSupported)
        )
    }

    @Test
    fun `brightness percent maps onto the 0-255 range SCREEN_BRIGHTNESS stores`() {
        assertEquals(0, DisplaySettingsController.brightnessValue(0))
        assertEquals(255, DisplaySettingsController.brightnessValue(100))
        assertEquals(127, DisplaySettingsController.brightnessValue(50))
    }

    @Test
    fun `brightness percent clamps out-of-range input instead of over- or under-flowing`() {
        assertEquals(0, DisplaySettingsController.brightnessValue(-10))
        assertEquals(255, DisplaySettingsController.brightnessValue(150))
    }
}
