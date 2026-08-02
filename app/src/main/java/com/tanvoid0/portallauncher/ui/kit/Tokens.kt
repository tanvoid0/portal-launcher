package com.tanvoid0.portallauncher.ui.kit

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * One spacing scale for the whole app.
 *
 * Before this, every screen picked its own numbers — 4, 8, 12, 16, 20, 24 and 32 all
 * appeared as literals, and no two screens lined up on the same gutter. Titles on
 * Settings and Profiles were indented differently from the rows underneath them.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Horizontal page margin. Screen titles and inset groups both line up on it. */
    val gutter = 20.dp
}

/**
 * Corner radii, set app-wide through [MaterialTheme.shapes] in
 * [com.tanvoid0.portallauncher.ui.theme.PortalLauncherTheme].
 *
 * Material's defaults (4/8/12/16/28) are what makes stock Compose read as 2021. Every
 * `Card`, `Surface`, `Button`, chip and bottom sheet picks its shape up from the theme,
 * so raising the scale here modernises components we never touch — and is one edit
 * rather than a hunt for `RoundedCornerShape(...)` literals.
 */
val PortalShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

/**
 * How opaque the frosted panels on the home screen are.
 *
 * They sit on the user's wallpaper, so this is a legibility setting, not decoration:
 * too low and text disappears over a busy photo, too high and the wallpaper may as
 * well not be there. The old code had 0.5, 0.6, 0.8 and 0.95 scattered across four
 * components that are meant to look like one material.
 */
const val GLASS_ALPHA = 0.55f

/** Frosted backing colour for anything drawn over the wallpaper. See [GlassSurface]. */
@Composable
@ReadOnlyComposable
fun glassColor(alpha: Float = GLASS_ALPHA): Color =
    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)

/**
 * Text drawn directly on the wallpaper, which can be any colour at all — so no theme
 * colour is legible against it (`onSurface` is near-black in a light scheme and
 * vanishes on a dark photo). White plus a soft shadow is what every launcher does, and
 * it survives both extremes.
 */
val OnWallpaperTextStyle = TextStyle(
    color = Color.White,
    shadow = Shadow(color = Color.Black.copy(alpha = 0.75f), blurRadius = 6f)
)
