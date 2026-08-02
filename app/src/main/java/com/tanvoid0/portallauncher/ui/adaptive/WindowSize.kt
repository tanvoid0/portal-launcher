package com.tanvoid0.portallauncher.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp

/**
 * Window width size class: Compact (< 600dp), Medium (600–839dp), Expanded (≥ 840dp).
 * Used for adaptive layout (bottom nav vs rail, single vs list-detail pane).
 */
enum class WindowSizeClass { Compact, Medium, Expanded }

/**
 * Reads the real window size instead of `Configuration.screenWidthDp`, which is
 * deprecated for this and reports the wrong thing in split-screen and on foldables:
 * what matters is the window we were given, not the display.
 */
@Composable
fun currentWindowSizeClass(): WindowSizeClass {
    val widthPx = LocalWindowInfo.current.containerSize.width
    val widthDp = with(LocalDensity.current) { widthPx.toDp() }
    return when {
        widthDp < 600.dp -> WindowSizeClass.Compact
        widthDp < 840.dp -> WindowSizeClass.Medium
        else -> WindowSizeClass.Expanded
    }
}
