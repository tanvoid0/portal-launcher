package com.tanvoid0.portallauncher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun PortalLauncherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    // Shapes are themed, not per-component: every Card, Surface, Button, chip and
    // bottom sheet in the app reads its radius from here, so the whole surface gets
    // the softer geometry without any of them being edited. See PortalShapes.
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = PortalShapes,
        typography = PortalTypography,
        content = content
    )
}
