package com.tanvoid0.portallauncher.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tanvoid0.portallauncher.PortalLauncherApplication
import com.tanvoid0.portallauncher.ui.adaptive.AdaptiveLauncherScaffold
import com.tanvoid0.portallauncher.ui.adaptive.NavItem
import com.tanvoid0.portallauncher.ui.launcher.LauncherHomeScreen
import com.tanvoid0.portallauncher.ui.nav.Routes
import com.tanvoid0.portallauncher.ui.onboarding.OnboardingScreen
import com.tanvoid0.portallauncher.ui.profiles.ProfileEditScreen
import com.tanvoid0.portallauncher.ui.profiles.ProfileListScreen
import com.tanvoid0.portallauncher.ui.settings.SettingsScreen
import com.tanvoid0.portallauncher.ui.theme.PortalLauncherTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    /** Emits when the user presses HOME while we are already the foreground task. */
    private val homeRequested = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Mandatory from targetSdk 35 — the system draws behind the bars whether we
        // ask or not, so opt in explicitly and handle insets per screen.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PortalLauncherTheme {
                // Setup is shown until it has been finished or skipped, and that fact
                // is persisted — the old per-process check meant a cold start, which
                // for the home app happens whenever the system reclaims us, put the
                // user back on the first-run screen.
                val preferences = remember {
                    (application as PortalLauncherApplication).preferencesRepository
                }
                val setupComplete by produceState<Boolean?>(null) {
                    value = preferences.setupComplete.first()
                }
                // Nothing is drawn for the frame or two that read takes. The window is
                // already transparent over the wallpaper, so there is no flash — this
                // is the launcher looking like the wallpaper, briefly.
                val isSetupComplete = setupComplete ?: return@PortalLauncherTheme

                // Transparent: the system wallpaper is drawn behind this window
                // (windowShowWallpaper in Theme.PortalLauncher), and an opaque Surface
                // would hide it. contentColor is explicit because a transparent
                // container cannot imply one.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    val navController = rememberNavController()
                    val backStack = navController.currentBackStackEntryAsState()
                    val currentRoute = backStack.value?.destination?.route?.let { route ->
                        when {
                            route.startsWith(Routes.PROFILE_EDIT) -> Routes.PROFILE_LIST
                            else -> route
                        }
                    } ?: Routes.LAUNCHER_HOME

                    LaunchedEffect(navController) {
                        homeRequested.collect {
                            navController.popBackStack(Routes.LAUNCHER_HOME, inclusive = false)
                        }
                    }

                    val navItems = remember {
                        listOf(
                            NavItem(Routes.LAUNCHER_HOME, "Home", Icons.Default.Home),
                            NavItem(Routes.PROFILE_LIST, "Profiles", Icons.Default.Person),
                            NavItem(Routes.SETTINGS, "Settings", Icons.Default.Settings)
                        )
                    }

                    val startDestination =
                        if (isSetupComplete) Routes.LAUNCHER_HOME else Routes.ONBOARDING

                    AdaptiveLauncherScaffold(
                        currentRoute = currentRoute,
                        navItems = navItems,
                        onNavClick = { route ->
                            if (route != currentRoute) navController.navigate(route) {
                                popUpTo(Routes.LAUNCHER_HOME) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                        showNav = currentRoute != Routes.LAUNCHER_HOME &&
                            currentRoute != Routes.ONBOARDING
                    ) { scaffoldPadding ->
                        // The scaffold reports only its own bars (see its zeroed
                        // contentWindowInsets); each screen applies system-bar insets
                        // itself, which is what lets home draw full-bleed.
                        NavHost(
                            navController = navController,
                            startDestination = startDestination,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(scaffoldPadding)
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
                            composable(Routes.ONBOARDING) {
                                OnboardingScreen(
                                    onDone = {
                                        navController.navigate(Routes.LAUNCHER_HOME) {
                                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable(Routes.SETTINGS) { SettingsScreen() }
                        }
                    }
                }
            }
        }
    }

    /**
     * As the default home app with launchMode=singleTask, pressing the home button
     * while we are already foreground re-delivers the launcher intent here instead of
     * recreating the activity. Without handling it, the user presses home, nothing
     * happens, and they are stranded on whatever screen they had open.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.categories?.contains(Intent.CATEGORY_HOME) == true) {
            homeRequested.tryEmit(Unit)
        }
    }
}
