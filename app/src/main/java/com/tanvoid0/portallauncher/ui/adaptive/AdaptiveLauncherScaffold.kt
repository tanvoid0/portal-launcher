package com.tanvoid0.portallauncher.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.tanvoid0.portallauncher.ui.nav.Routes

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
    content: @Composable () -> Unit
) {
    val sizeClass = currentWindowSizeClass()
    val isLauncherHome = currentRoute == Routes.LAUNCHER_HOME

    when (sizeClass) {
        WindowSizeClass.Compact -> Scaffold(
            bottomBar = {
                if (!isLauncherHome) {
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
        ) { content() }
        WindowSizeClass.Medium, WindowSizeClass.Expanded -> Scaffold { padding ->
            Row(modifier = Modifier.fillMaxSize()) {
                if (!isLauncherHome) {
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
                Box(modifier = Modifier.fillMaxSize().padding(padding)) { content() }
            }
        }
    }
}
