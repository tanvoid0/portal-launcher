package com.tanvoid0.portallauncher.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tanvoid0.portallauncher.ui.adaptive.AdaptiveLauncherScaffold
import com.tanvoid0.portallauncher.ui.adaptive.NavItem
import com.tanvoid0.portallauncher.ui.launcher.LauncherHomeScreen
import com.tanvoid0.portallauncher.ui.nav.Routes
import com.tanvoid0.portallauncher.ui.onboarding.OnboardingScreen
import com.tanvoid0.portallauncher.ui.profiles.ProfileEditScreen
import com.tanvoid0.portallauncher.ui.profiles.ProfileListScreen
import com.tanvoid0.portallauncher.ui.settings.SettingsScreen
import com.tanvoid0.portallauncher.ui.theme.PortalLauncherTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PortalLauncherTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                val navController = rememberNavController()
                val backStack = navController.currentBackStackEntryAsState()
                val currentRoute = backStack.value?.destination?.route?.let { route ->
                    when {
                        route.startsWith(Routes.PROFILE_EDIT) -> Routes.PROFILE_LIST
                        else -> route
                    }
                } ?: Routes.LAUNCHER_HOME

                val navItems = listOf(
                    NavItem(Routes.LAUNCHER_HOME, "Home", Icons.Default.Home),
                    NavItem(Routes.PROFILE_LIST, "Profiles", Icons.Default.Person),
                    NavItem(Routes.SETTINGS, "Settings", Icons.Default.Settings)
                )

                AdaptiveLauncherScaffold(
                    currentRoute = currentRoute,
                    navItems = navItems,
                    onNavClick = { route ->
                        if (route != currentRoute) navController.navigate(route) {
                            popUpTo(Routes.LAUNCHER_HOME) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    content = {
                        NavHost(
                            navController = navController,
                            startDestination = Routes.LAUNCHER_HOME,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable(Routes.LAUNCHER_HOME) {
                                LauncherHomeScreen(
                                    onOpenProfiles = { navController.navigate(Routes.PROFILE_LIST) },
                                    onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                                )
                            }
                            composable(Routes.PROFILE_LIST) {
                                ProfileListScreen(
                                    onNavigateToEdit = { id ->
                                        navController.navigate(Routes.profileEdit(id))
                                    }
                                )
                            }
                            composable("${Routes.PROFILE_EDIT}/{id}") { backStackEntry ->
                                val id = backStackEntry.arguments?.getString("id")
                                ProfileEditScreen(
                                    profileId = id,
                                    onSaved = { navController.popBackStack() }
                                )
                            }
                            composable(Routes.ONBOARDING) { OnboardingScreen() }
                            composable(Routes.SETTINGS) { SettingsScreen() }
                        }
                    }
                )
                }
            }
        }
    }
}
