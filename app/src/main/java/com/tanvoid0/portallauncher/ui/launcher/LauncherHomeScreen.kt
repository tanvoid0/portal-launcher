package com.tanvoid0.portallauncher.ui.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Widgets
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
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanvoid0.portallauncher.R
import com.tanvoid0.portallauncher.data.GridSize
import com.tanvoid0.portallauncher.data.HomeCellEntity
import com.tanvoid0.portallauncher.data.HomeEntry
import com.tanvoid0.portallauncher.data.LaunchableApp
import com.tanvoid0.portallauncher.data.PreferencesRepository
import com.tanvoid0.portallauncher.data.Slot
import com.tanvoid0.portallauncher.data.slot
import com.tanvoid0.portallauncher.ui.kit.EmptyState
import com.tanvoid0.portallauncher.ui.kit.GlassSurface
import com.tanvoid0.portallauncher.ui.kit.OnWallpaperTextStyle
import com.tanvoid0.portallauncher.ui.kit.PortalGroup
import com.tanvoid0.portallauncher.ui.kit.PortalRow
import com.tanvoid0.portallauncher.ui.kit.Spacing
import com.tanvoid0.portallauncher.ui.kit.glassColor
import com.tanvoid0.portallauncher.widgets.WidgetPlacement
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    /** Null where there is no Activity to run a widget's system screens on. */
    widgetPlacement: WidgetPlacement? = null,
    onOpenProfiles: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    var appDrawerOpen by remember { mutableStateOf(false) }
    // Tapping the search pill means "I want to type"; swiping up or using the dock
    // button means "show me my apps". Only the first should raise the keyboard.
    var drawerOpenedForSearch by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<LaunchableApp?>(null) }
    var menuWidget by remember { mutableStateOf<Slot?>(null) }
    var homeOptionsOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Either the user's own layout or the profile's category default — the resolver
    // decides, and already applies the count limit. See resolveHomeEntries.
    val homeEntries = uiState.homeEntries
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
            // Long-pressing the wallpaper is where widgets and the grid size live. It
            // only fires on empty space: an icon's own detector consumes the press
            // first, and this ancestor never sees it.
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { homeOptionsOpen = true })
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

            if (homeEntries.isEmpty() && uiState.allApps.isNotEmpty()) {
                // Two different empty states, and saying the wrong one is worse than
                // saying nothing: "nothing matches this profile" is a lie to someone
                // who just unpinned everything on purpose. Both still offer a way out —
                // an empty grid with no explanation reads as a broken launcher.
                val emptiedByUser = uiState.hasCustomLayout
                EmptyState(
                    title = if (emptiedByUser) {
                        stringResource(R.string.home_empty_user_title)
                    } else {
                        uiState.activeProfile?.name
                            ?.let { stringResource(R.string.home_empty_profile_title, it) }
                            ?: stringResource(R.string.home_empty_none_title)
                    },
                    body = if (emptiedByUser) {
                        stringResource(R.string.home_empty_user_body)
                    } else {
                        stringResource(R.string.home_empty_profile_body)
                    },
                    icon = Icons.Default.SearchOff,
                    onWallpaper = true,
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Button(onClick = { drawerOpenedForSearch = false; appDrawerOpen = true }) {
                                Text(stringResource(R.string.open_app_drawer))
                            }
                            if (emptiedByUser) {
                                // The way back from emptying the grid. Without it,
                                // unpinning everything is a one-way door.
                                OutlinedButton(onClick = viewModel::resetLayout) {
                                    Text(stringResource(R.string.restore_defaults))
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            } else {
                // Weighted, so the grid owns the space between the glance card and the
                // dock and the cell size follows from it. This used to be forbidden:
                // a weighted LazyVerticalGrid stretched over the empty half of the
                // screen and its scroll modifier swallowed the swipe-up that opens the
                // drawer. HorizontalPager only claims horizontal drags, so the vertical
                // gesture on the enclosing Box still gets through.
                HomePager(
                    entries = homeEntries,
                    grid = uiState.grid,
                    pageCount = uiState.pageCount,
                    viewModel = viewModel,
                    onOpenMenu = { entry ->
                        when (entry) {
                            is HomeEntry.App -> menuApp = entry.app
                            is HomeEntry.Widget -> menuWidget = entry.cell.slot
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.sm)
                )
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

        // Looked up live rather than captured when the menu opened: the menu resizes the
        // widget without closing, so a captured copy would still say 2 × 2 after the
        // first "Wider" and every further tap would ask for a size it already has.
        menuWidget?.let { slot ->
            uiState.cells.firstOrNull { it.slot == slot }?.let { cell ->
                WidgetContextMenu(
                    cell = cell,
                    viewModel = viewModel,
                    onDismiss = { menuWidget = null }
                )
            }
        }

        if (homeOptionsOpen) {
            // Approximate cell size, only used to pick a widget's starting span — the
            // exact one is not known outside the grid's own layout pass, and being a
            // cell out means a widget that arrives one cell too big and is resized.
            val windowWidth = LocalWindowInfo.current.containerSize.width
            val cellDp = with(LocalDensity.current) {
                (windowWidth / uiState.grid.columns).toDp().value.toInt()
            }
            HomeOptionsSheet(
                grid = uiState.grid,
                cellDp = cellDp,
                hasCustomLayout = uiState.hasCustomLayout,
                widgetPlacement = widgetPlacement,
                viewModel = viewModel,
                onDismiss = { homeOptionsOpen = false }
            )
        }
    }
}

/**
 * What long-pressing the wallpaper offers.
 *
 * Pages are deliberately absent. They are created by dragging an icon onto the spare
 * page at the end and removed when the last thing leaves them, so there is no page to
 * add, name, reorder or delete — and therefore no screen for it. See [HomePager].
 *
 * The widget picker swaps into this sheet rather than opening a second one, which
 * silently lost the race against this one closing. Same shape as [AppContextMenu].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeOptionsSheet(
    grid: GridSize,
    cellDp: Int,
    hasCustomLayout: Boolean,
    widgetPlacement: WidgetPlacement?,
    viewModel: LauncherViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var choosingWidget by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        if (choosingWidget) {
            WidgetPickerContent(
                grid = grid,
                cellWidthDp = cellDp,
                cellHeightDp = cellDp,
                placement = widgetPlacement,
                viewModel = viewModel,
                onDismiss = onDismiss
            )
            return@ModalBottomSheet
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = Spacing.lg)
        ) {
            Text(
                text = stringResource(R.string.home_options_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md)
            )
            PortalGroup {
                PortalRow(
                    icon = Icons.Default.Widgets,
                    title = stringResource(R.string.add_widget),
                    onClick = { choosingWidget = true }
                )
                PortalRow(
                    icon = Icons.Default.GridView,
                    title = stringResource(R.string.grid),
                    subtitle = stringResource(R.string.grid_size, grid.columns, grid.rows),
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            GridStep(
                                label = "−",
                                enabled = grid.columns > PreferencesRepository.MIN_COLUMNS,
                                onClick = {
                                    viewModel.setGrid(grid.copy(columns = grid.columns - 1))
                                }
                            )
                            GridStep(
                                label = "+",
                                enabled = grid.columns < PreferencesRepository.MAX_COLUMNS,
                                onClick = {
                                    viewModel.setGrid(grid.copy(columns = grid.columns + 1))
                                }
                            )
                        }
                    }
                )
                PortalRow(
                    icon = Icons.Default.Height,
                    title = stringResource(R.string.rows),
                    subtitle = stringResource(R.string.rows_subtitle),
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            GridStep(
                                label = "−",
                                enabled = grid.rows > PreferencesRepository.MIN_ROWS,
                                onClick = { viewModel.setGrid(grid.copy(rows = grid.rows - 1)) }
                            )
                            GridStep(
                                label = "+",
                                enabled = grid.rows < PreferencesRepository.MAX_ROWS,
                                onClick = { viewModel.setGrid(grid.copy(rows = grid.rows + 1)) }
                            )
                        }
                    }
                )
                if (hasCustomLayout) {
                    // The way back from a layout the user no longer wants. Without it,
                    // taking the grid over is a one-way door.
                    PortalRow(
                        icon = Icons.Default.Restore,
                        title = stringResource(R.string.reset_home),
                        subtitle = stringResource(R.string.reset_home_subtitle),
                        onClick = {
                            viewModel.resetLayout()
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun GridStep(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled) { Text(label) }
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
                text = stringResource(R.string.search_apps),
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
                    contentDescription = stringResource(R.string.app_drawer),
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
 *
 * Pass a null [onClick] on the home grid, where the enclosing cell owns tap, long press
 * and drag as one gesture — a second clickable inside it would announce the same app
 * twice to a screen reader and steal the press the drag needs.
 */
@Composable
internal fun AppIconCell(
    app: LaunchableApp,
    viewModel: LauncherViewModel,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    onWallpaper: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickableCell(onClick, onLongClick) else Modifier)
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
