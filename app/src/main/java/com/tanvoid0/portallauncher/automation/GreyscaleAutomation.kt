package com.tanvoid0.portallauncher.automation

import android.content.Context
import android.content.Intent
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
 * Drains the colour out of the launcher's own UI.
 *
 * **This is deliberately not device-wide, because device-wide is not possible.** The
 * system daltonizer needs `WRITE_SECURE_SETTINGS`, which is `signature|privileged` and
 * not grantable to a normal app; a `SYSTEM_ALERT_WINDOW` overlay composites *on top of*
 * other windows and cannot apply a colour matrix to the pixels beneath it; and
 * `AccessibilityService` has no colour-transform capability. Any launcher claiming
 * otherwise is either system-signed or asking for an ADB grant.
 *
 * So this automation does the part that is honest and free: the home screen is the
 * surface that triggers a distraction, and greying it out is most of the value. For the
 * whole device, [systemGreyscaleIntent] sends the user to the developer-options
 * monochromacy toggle — three taps they perform themselves, with nothing to grant.
 */
class GreyscaleAutomation : Automation {

    override val id: String = AutomationIds.GREYSCALE

    override val titleRes: Int = R.string.greyscale_title

    override val summaryRes: Int = R.string.greyscale_summary

    /**
     * Always ready: it is a draw-time filter on our own window, so there is no
     * permission and no OS version that can refuse it.
     */
    override fun availability(context: Context): AutomationAvailability =
        AutomationAvailability.Ready

    // Nothing to do here — see the class docs. The value is read at draw time by
    // Modifier.greyscale rather than pushed into a system service, so applying is a
    // state change the UI observes, not a side effect this class performs.
    override suspend fun apply(context: Context, configJson: String?) = Unit

    override suspend fun revert(context: Context) = Unit

    companion object {
        /**
         * Developer options, where "Simulate colour space → Monochromacy" lives.
         *
         * Not a promise that it will work: developer options can be locked down by
         * policy, and the sub-setting cannot be deep-linked. Callers must handle
         * [android.content.ActivityNotFoundException] and say so rather than appearing
         * to do nothing.
         */
        fun systemGreyscaleIntent(): Intent =
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
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
