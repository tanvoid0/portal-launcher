package com.tanvoid0.portallauncher.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

@Composable
fun AdaptiveLauncherScaffold(
    currentRoute: String,
    navItems: List<NavItem>,
    onNavClick: (String) -> Unit,
    /** False on full-bleed destinations — the home screen and onboarding. */
    showNav: Boolean,
    content: @Composable (PaddingValues) -> Unit
) {
    val sizeClass = currentWindowSizeClass()

    when (sizeClass) {
        WindowSizeClass.Compact -> Scaffold(
            // Transparent so the system wallpaper behind the window stays visible.
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            // Zeroed so the reported padding covers only our own bars. Screens apply
            // system-bar insets themselves, which is what lets home draw full-bleed
            // behind the status bar while still keeping its dock above the nav bar.
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                if (showNav) {
                    NavigationBar {
                        navItems.forEach { item ->
                            NavigationBarItem(
                                selected = currentRoute == item.route,
                                onClick = { onNavClick(item.route) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }
            }
            // The padding MUST reach content: without it every screen draws behind
            // the navigation bar, which is what the previous `{ content() }` did.
        ) { padding -> content(padding) }
        WindowSizeClass.Medium, WindowSizeClass.Expanded -> Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            contentWindowInsets = WindowInsets(0)
        ) { padding ->
            Row(modifier = Modifier.fillMaxSize()) {
                if (showNav) {
                    NavigationRail {
                        navItems.forEach { item ->
                            NavigationRailItem(
                                selected = currentRoute == item.route,
                                onClick = { onNavClick(item.route) },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) }
                            )
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) { content(padding) }
            }
        }
    }
}
