package com.tanvoid0.portallauncher.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The greyscale automation is only honest if its two halves agree: themed colours are
 * desaturated here, app icons by `ColorMatrix.setSaturation`, and a half-way slider has
 * to land both on the same grey or one screen looks like two different settings.
 */
class DesaturateTest {

    @Test
    fun `saturation of one leaves the colour untouched`() {
        val red = Color(0xFFCC2200)
        assertEquals(red, red.desaturate(1f))
    }

    @Test
    fun `saturation of zero collapses the channels onto luminance`() {
        val grey = Color(0xFFCC2200).desaturate(0f)
        assertEquals(grey.red, grey.green, 1e-4f)
        assertEquals(grey.green, grey.blue, 1e-4f)
        // The ColorMatrix.setSaturation coefficients, on gamma-encoded components.
        val expected = 0.213f * (0xCC / 255f) + 0.715f * (0x22 / 255f) + 0.072f * 0f
        assertEquals(expected, grey.red, 1e-3f)
    }

    @Test
    fun `alpha survives`() {
        // Loose: Color stores alpha as 8 bits, so 0.5f round-trips as 0.50196.
        assertEquals(0.5f, Color(1f, 0f, 0f, alpha = 0.5f).desaturate(0f).alpha, 1e-2f)
    }

    @Test
    fun `out of range saturation is clamped, not extrapolated`() {
        val blue = Color(0xFF3366CC)
        assertEquals(blue.desaturate(0f), blue.desaturate(-1f))
        assertEquals(Color.Red, Color.Red.desaturate(2f))
    }
}
