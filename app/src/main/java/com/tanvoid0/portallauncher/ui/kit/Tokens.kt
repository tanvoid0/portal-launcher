package com.tanvoid0.portallauncher.ui.kit

import android.graphics.Path
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

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
 * The wallpaper-world colours: fixed dark glass and white content, in both light and
 * dark theme. No theme colour is legible on an arbitrary wallpaper — [OnWallpaperTextStyle]
 * already made that call for text; this is the same call for the panels underneath it,
 * so `glassColor()` stops following `colorScheme.surfaceContainerHighest` and every
 * panel reads as one material regardless of the theme or the wallpaper under it.
 */
object Launcher {
    val glass = Color.Black.copy(alpha = 0.40f)
    val glassStrong = Color(0xFF101418).copy(alpha = 0.94f)
    val onGlass = Color.White
    val onGlassMuted = Color.White.copy(alpha = 0.7f)
}

/** Frosted backing colour for anything drawn over the wallpaper. See [GlassSurface]. */
fun glassColor(): Color = Launcher.glass

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

/**
 * Deliberate weights on top of system Roboto — no bundled font, so this costs nothing
 * on APK size and R8 can't shrink what was never added.
 */
val PortalTypography = Typography().let { base ->
    base.copy(
        // The home-screen clock. Light weight and tight tracking read as a clock face
        // rather than a heading, the way the reference launcher's does.
        displayLarge = base.displayLarge.copy(
            fontSize = 88.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-1.5).sp
        ),
        // Screen titles — Settings, Profiles, the drawer.
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold),
        // Icon labels, one notch heavier than the default Medium so they hold up at
        // small size on a busy wallpaper.
        labelSmall = base.labelSmall.copy(fontWeight = FontWeight.SemiBold)
    )
}

/**
 * Icon mask shapes the user can pick instead of the OEM's own adaptive-icon mask. One
 * [path] function, three callers: [com.tanvoid0.portallauncher.data.IconCache]'s
 * rasteriser and, from Phase 4 on, the Appearance preview and the blocker overlay.
 */
enum class IconShape {
    /** The OS mask. Callers check for this and skip clipping entirely. */
    System,
    Circle,
    Squircle,
    Rounded,
    Teardrop;

    /** Clip path for a [size]x[size] icon, in the same pixel space it will be drawn in. */
    fun path(size: Float): Path = when (this) {
        System -> Path()
        Circle -> Path().apply { addOval(0f, 0f, size, size, Path.Direction.CW) }
        Squircle -> superellipsePath(size)
        Rounded -> {
            val r = size * 0.3f
            Path().apply { addRoundRect(0f, 0f, size, size, r, r, Path.Direction.CW) }
        }
        // One sharp corner, mirroring the adaptive-icon teardrop mask convention: a
        // rounded square with the bottom-right corner squared off instead of curved.
        Teardrop -> {
            val r = size * 0.3f
            Path().apply {
                addRoundRect(
                    0f, 0f, size, size,
                    floatArrayOf(r, r, r, r, 0f, 0f, r, r),
                    Path.Direction.CW
                )
            }
        }
    }
}

/** Superellipse (|x|^n + |y|^n = 1) sampled as a polygon — close enough at icon size. */
private fun superellipsePath(size: Float): Path {
    val r = size / 2f
    val n = 4.0
    val steps = 64
    val path = Path()
    for (i in 0..steps) {
        val t = 2.0 * Math.PI * i / steps
        val x = r + r * sign(cos(t)) * abs(cos(t)).pow(2.0 / n)
        val y = r + r * sign(sin(t)) * abs(sin(t)).pow(2.0 / n)
        if (i == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
    }
    path.close()
    return path
}
