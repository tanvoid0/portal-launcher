package com.tanvoid0.portallauncher.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Window width size class: Compact (< 600dp), Medium (600–839dp), Expanded (≥ 840dp).
 * Used for adaptive layout (bottom nav vs rail, single vs list-detail pane).
 */
enum class WindowSizeClass { Compact, Medium, Expanded }

@Composable
fun currentWindowSizeClass(): WindowSizeClass {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return when {
        widthDp < 600 -> WindowSizeClass.Compact
        widthDp < 840 -> WindowSizeClass.Medium
        else -> WindowSizeClass.Expanded
    }
}
