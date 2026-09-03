package com.tanvoid0.portallauncher.data

import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tanvoid0.portallauncher.ui.kit.IconShape
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins the inset math in [toImageBitmap]: masking a real [AdaptiveIconDrawable] must
 * clip to the shape (transparent outside it) while still drawing the foreground layer,
 * oversized and centred, through that mask — the one part of Phase 1 the plan calls
 * out as easy to get subtly wrong.
 */
@RunWith(AndroidJUnit4::class)
class IconShapeTest {

    @Test
    fun circleMasksCornersAndCentresForeground() {
        val drawable = AdaptiveIconDrawable(
            ColorDrawable(Color.RED),
            ColorDrawable(Color.BLUE)
        )
        val bitmap = drawable.toImageBitmap(96, IconShape.Circle).asAndroidBitmap()

        // Corner: outside the circle, never drawn — still the bitmap's initial transparent.
        assertEquals(0, Color.alpha(bitmap.getPixel(2, 2)))
        // Centre: inside the mask, the foreground layer drawn opaque on top of the background.
        assertEquals(Color.BLUE, bitmap.getPixel(48, 48))
    }
}
