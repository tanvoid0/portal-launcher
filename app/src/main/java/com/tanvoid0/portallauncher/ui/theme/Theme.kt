package com.tanvoid0.portallauncher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.lerp
import com.tanvoid0.portallauncher.ui.kit.PortalShapes
import com.tanvoid0.portallauncher.ui.kit.PortalTypography

// Seeded from the launcher's own icon rather than a generic Material seed: surface
// is `ic_launcher_background` (#101418, the family's black) and primary is the cyan
// sampled from `ic_launcher_foreground` (#4FC3D9). Tonal values around them are hand
// picked to the same M3 conventions Material Theme Builder would output for that seed.
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4FC3D9),
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFFB8EAFF),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE1E3E4),
    surfaceVariant = Color(0xFF3F484A),
    onSurfaceVariant = Color(0xFFBFC8CA),
    outline = Color(0xFF899294),
)

// Same family, deeper: #006874 is the same hue as the icon's cyan pulled dark enough
// for >=4.5:1 contrast against white (measured ~6.5:1), so the one brand accent reads
// on both a light and a dark background instead of needing two unrelated colours.
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006874),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9EEFFD),
    onPrimaryContainer = Color(0xFF001F24),
    surface = Color(0xFFFAFDFD),
    onSurface = Color(0xFF191C1D),
    surfaceVariant = Color(0xFFDBE4E6),
    onSurfaceVariant = Color(0xFF3F484A),
    outline = Color(0xFF6F797A),
)

/**
 * How much colour survives, 1 (untouched) to 0 (grey), for content this theme does not
 * colour itself — app icons and hosted widgets, which are bitmaps and foreign views.
 *
 * A composition local rather than a parameter because the things that need it are deep
 * in the tree, and because the alternative — one draw-time filter over the whole window
 * — would desaturate the themed colours a *second* time on top of [desaturate] and make
 * mid-slider settings inconsistent between the launcher's own screens and its popups.
 */
val LocalGreyscaleSaturation = staticCompositionLocalOf { 1f }

@Composable
fun PortalLauncherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    saturation: Float = 1f,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }.desaturate(saturation)
    // Shapes are themed, not per-component: every Card, Surface, Button, chip and
    // bottom sheet in the app reads its radius from here, so the whole surface gets
    // the softer geometry without any of them being edited. See PortalShapes.
    CompositionLocalProvider(LocalGreyscaleSaturation provides saturation) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = PortalShapes,
            typography = PortalTypography,
            content = content
        )
    }
}

/**
 * Pulls a colour towards its own luminance. [saturation] runs 1 (untouched) to 0 (grey).
 *
 * Same coefficients as `android.graphics.ColorMatrix.setSaturation`, and applied to the
 * gamma-encoded components like it is, so a themed surface and an app icon at the same
 * slider position end up the same grey.
 */
internal fun Color.desaturate(saturation: Float): Color {
    if (saturation >= 1f) return this
    val s = saturation.coerceIn(0f, 1f)
    val grey = 0.213f * red + 0.715f * green + 0.072f * blue
    return Color(
        red = lerp(grey, red, s),
        green = lerp(grey, green, s),
        blue = lerp(grey, blue, s),
        alpha = alpha
    )
}

/**
 * Desaturates every colour role, so *everything* Material colours goes grey together —
 * including dialogs, dropdown menus and bottom sheets, which live in their own windows
 * and so can never be reached by a draw-time filter on the launcher's window.
 *
 * Every role is listed because [ColorScheme.copy] has no "map all" form. A role added by
 * a future Material version will simply keep its colour until it is added here.
 */
private fun ColorScheme.desaturate(saturation: Float): ColorScheme {
    if (saturation >= 1f) return this
    fun Color.grey() = desaturate(saturation)
    return copy(
        primary = primary.grey(),
        onPrimary = onPrimary.grey(),
        primaryContainer = primaryContainer.grey(),
        onPrimaryContainer = onPrimaryContainer.grey(),
        inversePrimary = inversePrimary.grey(),
        secondary = secondary.grey(),
        onSecondary = onSecondary.grey(),
        secondaryContainer = secondaryContainer.grey(),
        onSecondaryContainer = onSecondaryContainer.grey(),
        tertiary = tertiary.grey(),
        onTertiary = onTertiary.grey(),
        tertiaryContainer = tertiaryContainer.grey(),
        onTertiaryContainer = onTertiaryContainer.grey(),
        background = background.grey(),
        onBackground = onBackground.grey(),
        surface = surface.grey(),
        onSurface = onSurface.grey(),
        surfaceVariant = surfaceVariant.grey(),
        onSurfaceVariant = onSurfaceVariant.grey(),
        surfaceTint = surfaceTint.grey(),
        inverseSurface = inverseSurface.grey(),
        inverseOnSurface = inverseOnSurface.grey(),
        error = error.grey(),
        onError = onError.grey(),
        errorContainer = errorContainer.grey(),
        onErrorContainer = onErrorContainer.grey(),
        outline = outline.grey(),
        outlineVariant = outlineVariant.grey(),
        scrim = scrim.grey(),
        surfaceBright = surfaceBright.grey(),
        surfaceDim = surfaceDim.grey(),
        surfaceContainer = surfaceContainer.grey(),
        surfaceContainerHigh = surfaceContainerHigh.grey(),
        surfaceContainerHighest = surfaceContainerHighest.grey(),
        surfaceContainerLow = surfaceContainerLow.grey(),
        surfaceContainerLowest = surfaceContainerLowest.grey()
    )
}
