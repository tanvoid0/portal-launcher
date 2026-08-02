package com.tanvoid0.portallauncher.ui.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanvoid0.portallauncher.data.LaunchableApp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val PINNED_APP_COUNT = 8
private const val HOME_GRID_COLUMNS = 4
private val ICON_SIZE = 48.dp
private val CELL_SIZE = 56.dp

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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val apps = uiState.apps
    // ponytail: "pinned" is still the first N alphabetically. Phase 3 backs this
    // with the home_item table so the user actually chooses and reorders them.
    val pinnedApps = apps.take(PINNED_APP_COUNT)
    val dockApps = remember(apps) { resolveDockApps(apps) }

    // The system wallpaper shows through the window itself (windowShowWallpaper in
    // Theme.PortalLauncher), so live wallpapers and parallax work and we hold no
    // bitmap of our own.
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            SearchPill(
                // Phase 4 replaces this with a real query field.
                onClick = { appDrawerOpen = true },
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            // Profile chips only when there is something to switch between.
            if (uiState.profiles.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.profiles.forEach { profile ->
                        FilterChip(
                            selected = uiState.activeProfile?.id == profile.id,
                            onClick = { viewModel.setActiveProfile(profile.id) },
                            label = { Text(profile.name) }
                        )
                    }
                }
            }

            AtAGlanceCard(modifier = Modifier.padding(horizontal = 24.dp))

            Spacer(modifier = Modifier.height(24.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(HOME_GRID_COLUMNS),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(pinnedApps, key = { it.key }) { app ->
                    AppIconCell(
                        app = app,
                        viewModel = viewModel,
                        onClick = { viewModel.launch(app) },
                        onWallpaper = true
                    )
                }
            }

            Dock(
                apps = dockApps,
                viewModel = viewModel,
                onOpenDrawer = { appDrawerOpen = true }
            )
        }

        if (appDrawerOpen) {
            ModalBottomSheet(
                onDismissRequest = { appDrawerOpen = false },
                sheetState = sheetState,
                dragHandle = null,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                AppDrawerContent(
                    apps = apps,
                    viewModel = viewModel,
                    onLaunch = { app ->
                        appDrawerOpen = false
                        viewModel.launch(app)
                    },
                    onOpenProfiles = { appDrawerOpen = false; onOpenProfiles() },
                    onOpenSettings = { appDrawerOpen = false; onOpenSettings() }
                )
            }
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
private fun Modifier.clickableCell(onClick: () -> Unit): Modifier =
    clickable(role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) {}

/**
 * Looks like a search field but is a button, and is announced as one. The old
 * version was a read-only [androidx.compose.material3.OutlinedTextField], which
 * told screen readers it was editable text and then did nothing at all.
 */
@Composable
private fun SearchPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = today,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Without this the dock sits under the navigation bar: edge-to-edge
                // is mandatory from targetSdk 35 and cannot be opted out of.
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            apps.forEach { app ->
                AppIcon(
                    app = app,
                    viewModel = viewModel,
                    onClick = { viewModel.launch(app) }
                )
            }
            Box(
                modifier = Modifier
                    .size(CELL_SIZE)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
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

@Composable
private fun AppDrawerContent(
    apps: List<LaunchableApp>,
    viewModel: LauncherViewModel,
    onLaunch: (LaunchableApp) -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        Text(
            "Apps",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DrawerShortcut(Icons.Default.Person, "Profiles", onOpenProfiles)
            DrawerShortcut(Icons.Default.Settings, "Settings", onOpenSettings)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 80.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.heightIn(max = 420.dp)
        ) {
            items(apps, key = { it.key }) { app ->
                AppIconCell(
                    app = app,
                    viewModel = viewModel,
                    onClick = { onLaunch(app) },
                    onWallpaper = false
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
private fun AppIconCell(
    app: LaunchableApp,
    viewModel: LauncherViewModel,
    onClick: () -> Unit,
    onWallpaper: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickableCell(onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIcon(app = app, viewModel = viewModel, onClick = null)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = app.label,
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
 * Home-screen labels sit directly on the user's wallpaper, which can be any colour
 * at all, so no theme colour is legible against it — `onSurface` is near-black in a
 * light scheme and vanishes on a dark photo. White plus a soft shadow is what every
 * launcher does, and it survives both extremes.
 */
private val OnWallpaperTextStyle = TextStyle(
    color = Color.White,
    shadow = Shadow(color = Color.Black.copy(alpha = 0.75f), blurRadius = 6f)
)

/**
 * Bare icon. Pass a null [onClick] when an ancestor is already clickable, so the
 * cell exposes one accessibility target instead of two.
 */
@Composable
private fun AppIcon(
    app: LaunchableApp,
    viewModel: LauncherViewModel,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val icon = rememberAppIcon(app, viewModel)
    Box(
        modifier = modifier
            .size(CELL_SIZE)
            .then(if (onClick != null) Modifier.clickableCell(onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = if (onClick != null) app.label else null,
                modifier = Modifier.size(ICON_SIZE)
            )
        } else {
            // Placeholder while the drawable rasterises, and fallback if it fails.
            Text(
                text = app.label.take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Resolves the icon at real device density, off the main thread, only when composed. */
@Composable
private fun rememberAppIcon(
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
private fun DrawerShortcut(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(72.dp)
            .clickableCell(onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(ICON_SIZE)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(4.dp))
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
