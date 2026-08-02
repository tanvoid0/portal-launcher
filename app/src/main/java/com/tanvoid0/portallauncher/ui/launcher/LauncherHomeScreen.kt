package com.tanvoid0.portallauncher.ui.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanvoid0.portallauncher.data.LaunchableApp
import com.tanvoid0.portallauncher.ui.kit.EmptyState
import com.tanvoid0.portallauncher.ui.kit.GlassSurface
import com.tanvoid0.portallauncher.ui.kit.OnWallpaperTextStyle
import com.tanvoid0.portallauncher.ui.kit.Spacing
import com.tanvoid0.portallauncher.ui.kit.glassColor
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val HOME_GRID_COLUMNS = 4
private val ICON_SIZE = 48.dp
private val CELL_SIZE = 56.dp

/**
 * How far up you have to drag before the drawer opens. Well above the touch slop, so
 * a slightly imprecise tap on an icon does not open the drawer instead of the app.
 */
private val SWIPE_UP_THRESHOLD = 72.dp

// Preferred dock apps in order: Phone, Messages, Camera, Play Store (app drawer is added separately)
private val DOCK_PACKAGES = listOf(
    listOf("com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer"), // Phone
    listOf("com.google.android.apps.messaging", "com.android.mms", "com.samsung.android.messaging"), // Messages
    listOf("com.android.camera2", "com.android.camera", "com.google.android.GoogleCamera", "com.sec.android.camera"), // Camera
    listOf("com.android.vending") // Play Store
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherHomeScreen(
    modifier: Modifier = Modifier,
    viewModel: LauncherViewModel = viewModel(),
    onOpenProfiles: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    var appDrawerOpen by remember { mutableStateOf(false) }
    // Tapping the search pill means "I want to type"; swiping up or using the dock
    // button means "show me my apps". Only the first should raise the keyboard.
    var drawerOpenedForSearch by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<LaunchableApp?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Either the user's pinned layout or the profile's category default — the
    // resolver decides, and already applies the count limit. See resolveHomeApps.
    val homeApps = uiState.homeApps
    // Dock and drawer deliberately ignore the profile filter — see LauncherUiState.
    val dockApps = remember(uiState.allApps) { resolveDockApps(uiState.allApps) }
    val swipeThresholdPx = with(LocalDensity.current) { SWIPE_UP_THRESHOLD.toPx() }

    // The system wallpaper shows through the window itself (windowShowWallpaper in
    // Theme.PortalLauncher), so live wallpapers and parallax work and we hold no
    // bitmap of our own.
    Box(
        modifier = modifier
            .fillMaxSize()
            // Swipe up anywhere on the background opens the drawer. Attached to the
            // outer Box rather than the grid: a scrollable grid consumes vertical
            // drags itself, and the empty part of the home screen is where the gesture
            // is made anyway.
            .pointerInput(Unit) {
                var travelled = 0f
                detectVerticalDragGestures(
                    onDragStart = { travelled = 0f },
                    onDragEnd = {
                        if (travelled < -swipeThresholdPx) {
                            drawerOpenedForSearch = false
                            appDrawerOpen = true
                        }
                    }
                ) { _, delta -> travelled += delta }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            SearchPill(
                onClick = { drawerOpenedForSearch = true; appDrawerOpen = true },
                modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.md)
            )

            // Profile chips only when there is something to switch between.
            if (uiState.profiles.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    uiState.profiles.forEach { profile ->
                        FilterChip(
                            selected = uiState.activeProfile?.id == profile.id,
                            onClick = { viewModel.setActiveProfile(profile.id) },
                            label = { Text(profile.name) },
                            // An unselected chip defaults to a transparent container,
                            // which puts its label straight onto the wallpaper and
                            // makes it unreadable over anything dark. Give it the same
                            // frosted backing the search pill and glance card use.
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = glassColor(),
                                labelColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = null
                        )
                    }
                }
            }

            AtAGlanceCard(modifier = Modifier.padding(horizontal = Spacing.gutter))

            Spacer(modifier = Modifier.height(Spacing.xl))

            if (homeApps.isEmpty() && uiState.allApps.isNotEmpty()) {
                // Two different empty states, and saying the wrong one is worse than
                // saying nothing: "nothing matches this profile" is a lie to someone
                // who just unpinned everything on purpose. Both still offer a way out —
                // an empty grid with no explanation reads as a broken launcher.
                val emptiedByUser = uiState.hasCustomLayout
                EmptyState(
                    title = if (emptiedByUser) {
                        "Home screen is empty"
                    } else {
                        uiState.activeProfile?.name?.let { "No apps in $it" } ?: "No apps to show"
                    },
                    body = if (emptiedByUser) {
                        "You removed every app from this profile's home screen. " +
                            "They are all still in the drawer."
                    } else {
                        "Nothing installed matches this profile's categories. " +
                            "Every app is still in the drawer."
                    },
                    icon = Icons.Default.SearchOff,
                    onWallpaper = true,
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Button(onClick = { drawerOpenedForSearch = false; appDrawerOpen = true }) { Text("Open app drawer") }
                            if (emptiedByUser) {
                                // The way back from emptying the grid. Without it,
                                // unpinning everything is a one-way door.
                                OutlinedButton(onClick = viewModel::resetLayout) {
                                    Text("Restore defaults")
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            } else {
                // Sized to its content, not weight(1f). A weighted grid stretches over
                // the empty half of the screen, and its scroll modifier then swallows
                // the swipe-up that should open the drawer even with nothing to scroll —
                // which is exactly how the gesture silently did nothing.
                //
                // ponytail: caps the home screen at one screenful. Pin more than fits
                // and the grid runs under the dock rather than scrolling. Multi-page
                // home is explicitly out of v1.0 (see PRODUCTION_PLAN.md §5); if that
                // changes, this needs a scrollable region plus nested-scroll handling to
                // keep the gesture alive.
                LazyVerticalGrid(
                    columns = GridCells.Fixed(HOME_GRID_COLUMNS),
                    contentPadding = PaddingValues(
                        horizontal = Spacing.lg,
                        vertical = Spacing.sm
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                    userScrollEnabled = false,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(homeApps, key = { it.key }) { app ->
                        AppIconCell(
                            app = app,
                            viewModel = viewModel,
                            onClick = { viewModel.launch(app) },
                            onLongClick = { menuApp = app },
                            onWallpaper = true
                        )
                    }
                }
                // The gesture surface. Everything above is laid out; this is the part of
                // the home screen you can actually swipe up from.
                Spacer(modifier = Modifier.weight(1f))
            }

            Dock(
                apps = dockApps,
                viewModel = viewModel,
                onOpenDrawer = { drawerOpenedForSearch = false; appDrawerOpen = true },
                onLongPress = { menuApp = it }
            )
        }

        if (appDrawerOpen) {
            ModalBottomSheet(
                onDismissRequest = { appDrawerOpen = false },
                sheetState = sheetState,
                dragHandle = null,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                AppDrawerSheet(
                    apps = uiState.allApps,
                    categoryByKey = uiState.categoryByKey,
                    viewModel = viewModel,
                    autoFocusSearch = drawerOpenedForSearch,
                    onLaunch = { app ->
                        appDrawerOpen = false
                        viewModel.launch(app)
                    },
                    onLongPress = { menuApp = it },
                    onOpenProfiles = { appDrawerOpen = false; onOpenProfiles() },
                    onOpenSettings = { appDrawerOpen = false; onOpenSettings() }
                )
            }
        }

        menuApp?.let { app ->
            AppContextMenu(app = app, viewModel = viewModel, onDismiss = { menuApp = null })
        }
    }
}

/**
 * One clickable, screen-reader-visible target that carries the label it contains.
 *
 * The explicit `mergeDescendants` states the intent rather than relying on it: a
 * cell is one thing to tap and should be one thing to focus, which is what the
 * Material components do internally.
 *
 * Not verified end to end. `uiautomator dump` cannot check this — it reports
 * Compose's *unmerged* semantics tree, so labels always appear on separate nodes
 * there whether merging is on or not. Confirming what TalkBack actually announces
 * needs the Compose UI tests in phase 8 (merged tree, `assertHasClickAction`).
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.clickableCell(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
): Modifier = combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
    .semantics(mergeDescendants = true) {}

/**
 * Looks like a search field but is a button, and is announced as one. The old
 * version was a read-only [androidx.compose.material3.OutlinedTextField], which
 * told screen readers it was editable text and then did nothing at all.
 */
@Composable
private fun SearchPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = CircleShape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Search apps",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AtAGlanceCard(modifier: Modifier = Modifier) {
    // Re-formats every minute; the previous version formatted once and then showed
    // yesterday's date forever.
    val today by produceState(formatToday()) {
        while (true) {
            delay(60_000)
            value = formatToday()
        }
    }
    GlassSurface(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = today,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun formatToday(): String =
    SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())

@Composable
private fun Dock(
    apps: List<LaunchableApp>,
    viewModel: LauncherViewModel,
    onOpenDrawer: () -> Unit,
    onLongPress: (LaunchableApp) -> Unit,
    modifier: Modifier = Modifier
) {
    // A floating frosted bar rather than the full-width opaque strip it used to be —
    // an edge-to-edge slab of `surface` covers the bottom of the wallpaper and is the
    // most dated thing on the screen.
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            // Without this the dock sits under the navigation bar: edge-to-edge
            // is mandatory from targetSdk 35 and cannot be opted out of.
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        shape = CircleShape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            apps.forEach { app ->
                AppIcon(
                    app = app,
                    viewModel = viewModel,
                    onClick = { viewModel.launch(app) },
                    onLongClick = { onLongPress(app) }
                )
            }
            Box(
                modifier = Modifier
                    .size(CELL_SIZE)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickableCell(onOpenDrawer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Apps,
                    contentDescription = "App drawer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Icon plus label, for grids.
 *
 * [onWallpaper] must be true on the home screen and false in the app drawer: the
 * drawer sits on an opaque sheet where white-on-white would disappear, and the home
 * screen sits on the wallpaper where a theme colour would.
 */
@Composable
internal fun AppIconCell(
    app: LaunchableApp,
    viewModel: LauncherViewModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onWallpaper: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickableCell(onClick, onLongClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Null handlers: the enclosing cell already owns both gestures, so the icon
        // must not add a second target for the same app.
        AppIcon(app = app, viewModel = viewModel, onClick = null, onLongClick = null)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = app.displayLabel,
            style = if (onWallpaper) {
                MaterialTheme.typography.labelSmall.merge(OnWallpaperTextStyle)
            } else {
                MaterialTheme.typography.labelSmall
            },
            color = if (onWallpaper) Color.Unspecified else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Bare icon. Pass a null [onClick] when an ancestor is already clickable, so the
 * cell exposes one accessibility target instead of two.
 */
@Composable
internal fun AppIcon(
    app: LaunchableApp,
    viewModel: LauncherViewModel,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val icon = rememberAppIcon(app, viewModel)
    Box(
        modifier = modifier
            .size(CELL_SIZE)
            .then(
                if (onClick != null) Modifier.clickableCell(onClick, onLongClick) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = if (onClick != null) app.displayLabel else null,
                modifier = Modifier.size(ICON_SIZE)
            )
        } else {
            // Placeholder while the drawable rasterises, and fallback if it fails.
            Text(
                text = app.displayLabel.take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Resolves the icon at real device density, off the main thread, only when composed. */
@Composable
internal fun rememberAppIcon(
    app: LaunchableApp,
    viewModel: LauncherViewModel,
    size: Dp = ICON_SIZE
): ImageBitmap? {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    return produceState<ImageBitmap?>(null, app.key, sizePx) {
        value = viewModel.loadIcon(app, sizePx)
    }.value
}

@Composable
internal fun DrawerShortcut(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(72.dp)
            .clickableCell(onClick)
            .padding(Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(ICON_SIZE)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Dock: Phone, Messages, Camera, Play Store — whichever of each candidate list is
 * installed. Personal profile only; a work-profile dialler is not what the dock wants.
 */
private fun resolveDockApps(apps: List<LaunchableApp>): List<LaunchableApp> {
    val byPackage = apps.filterNot { it.isWorkProfile }.associateBy { it.packageName }
    return DOCK_PACKAGES.mapNotNull { candidates ->
        candidates.firstNotNullOfOrNull { byPackage[it] }
    }
}
