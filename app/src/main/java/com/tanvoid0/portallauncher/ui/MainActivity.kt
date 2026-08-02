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
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tanvoid0.portallauncher.PortalLauncherApplication
import com.tanvoid0.portallauncher.R
import com.tanvoid0.portallauncher.automation.greyscale
import com.tanvoid0.portallauncher.data.AutomationIds
import com.tanvoid0.portallauncher.data.ConfigCodec
import com.tanvoid0.portallauncher.data.GreyscaleConfig
import com.tanvoid0.portallauncher.ui.adaptive.AdaptiveLauncherScaffold
import com.tanvoid0.portallauncher.ui.adaptive.NavItem
import com.tanvoid0.portallauncher.ui.launcher.LauncherHomeScreen
import com.tanvoid0.portallauncher.ui.nav.Routes
import com.tanvoid0.portallauncher.ui.onboarding.OnboardingScreen
import com.tanvoid0.portallauncher.ui.profiles.ProfileEditScreen
import com.tanvoid0.portallauncher.ui.profiles.ProfileListScreen
import com.tanvoid0.portallauncher.ui.settings.HiddenAppsScreen
import com.tanvoid0.portallauncher.ui.settings.ScheduleScreen
import com.tanvoid0.portallauncher.ui.settings.SettingsScreen
import com.tanvoid0.portallauncher.ui.theme.PortalLauncherTheme
import com.tanvoid0.portallauncher.widgets.ActivityWidgetPlacement
import com.tanvoid0.portallauncher.widgets.LauncherWidgetHost
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    /** Emits when the user presses HOME while we are already the foreground task. */
    private val homeRequested = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val widgetHost: LauncherWidgetHost by lazy {
        (application as PortalLauncherApplication).widgetHost
    }

    /**
     * Registered in [onCreate], because an ActivityResultLauncher cannot be created once
     * the Activity has started.
     */
    private lateinit var widgetPlacement: ActivityWidgetPlacement

    override fun onCreate(savedInstanceState: Bundle?) {
        // Mandatory from targetSdk 35 — the system draws behind the bars whether we
        // ask or not, so opt in explicitly and handle insets per screen.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        widgetPlacement = ActivityWidgetPlacement(this, widgetHost)
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
                // The active profile's greyscale setting, read here because it wraps
                // every screen. Device-wide greyscale is impossible for a normal app —
                // see GreyscaleAutomation — so this is the launcher's own surfaces.
                val greyscale by produceState(1f) {
                    val app = application as PortalLauncherApplication
                    app.activeProfileSource.activeConfigs.collect { active ->
                        val config = ConfigCodec.decodeOr(
                            active.configJson(AutomationIds.GREYSCALE),
                            GreyscaleConfig()
                        )
                        value = if (active.isEnabled(AutomationIds.GREYSCALE) && config.enabled) {
                            1f - config.intensity.coerceIn(0f, 1f)
                        } else {
                            1f
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .greyscale(greyscale),
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

                    // Not remembered: stringResource must run in composition, and three
                    // NavItems are cheaper than the cache that would hold them.
                    val navItems = listOf(
                        NavItem(
                            Routes.LAUNCHER_HOME,
                            stringResource(R.string.nav_home),
                            Icons.Default.Home
                        ),
                        NavItem(
                            Routes.PROFILE_LIST,
                            stringResource(R.string.nav_profiles),
                            Icons.Default.Person
                        ),
                        NavItem(
                            Routes.SETTINGS,
                            stringResource(R.string.nav_settings),
                            Icons.Default.Settings
                        )
                    )

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
                                    widgetPlacement = widgetPlacement,
                                    onOpenProfiles = { navController.navigate(Routes.PROFILE_LIST) },
                                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                                    onOpenHiddenApps = { navController.navigate(Routes.HIDDEN_APPS) }
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
                            composable(Routes.SETTINGS) {
                                SettingsScreen(
                                    onOpenHiddenApps = {
                                        navController.navigate(Routes.HIDDEN_APPS)
                                    },
                                    onOpenSchedule = {
                                        navController.navigate(Routes.SCHEDULE)
                                    }
                                )
                            }
                            composable(Routes.HIDDEN_APPS) { HiddenAppsScreen() }
                            composable(Routes.SCHEDULE) { ScheduleScreen() }
                        }
                    }
                }
            }
        }
    }

    /**
     * A widget only receives updates while its host is listening, and a host that
     * listens while the launcher is in the background wakes this process for every
     * clock tick on the home screen. Tied to the visible lifetime for both reasons.
     */
    override fun onStart() {
        super.onStart()
        widgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        widgetHost.stopListening()
    }

    /**
     * The result of a widget's configuration screen.
     *
     * Deprecated, and there is no replacement that works here: the configuration
     * activity is launched through an IntentSender the system grants to the widget host
     * — see [ActivityWidgetPlacement] — and no ActivityResultContract can express that.
     */
    @Deprecated("Required by AppWidgetHost.startAppWidgetConfigureActivityForResult")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        widgetPlacement.onConfigureResult(requestCode, resultCode)
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
