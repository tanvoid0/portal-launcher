package com.tanvoid0.portallauncher.ui.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import com.tanvoid0.portallauncher.ui.kit.icon
import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Shop
import androidx.compose.ui.platform.LocalContext
import com.tanvoid0.portallauncher.R
import com.tanvoid0.portallauncher.data.AppCategory
import com.tanvoid0.portallauncher.data.DrawerSortMode
import com.tanvoid0.portallauncher.ui.kit.labelRes
import com.tanvoid0.portallauncher.data.LaunchableApp
import com.tanvoid0.portallauncher.data.searchApps
import com.tanvoid0.portallauncher.ui.kit.EmptyState
import com.tanvoid0.portallauncher.ui.kit.PortalGroup
import com.tanvoid0.portallauncher.ui.kit.PortalRow
import com.tanvoid0.portallauncher.ui.kit.SectionHeader
import com.tanvoid0.portallauncher.ui.kit.SelectableRow
import com.tanvoid0.portallauncher.ui.kit.Spacing

private val DRAWER_MIN_CELL = 80.dp

/**
 * The drawer, which is also the search surface.
 *
 * One sheet rather than a separate search screen: on a launcher, "show me everything"
 * and "find one thing" are the same gesture a moment apart, and splitting them means
 * the user picks the wrong entry point and has to back out.
 *
 * Grouped by category when idle — that grouping is the product's whole premise — and
 * flat, ranked, ungrouped while searching, because a ranked list that is also sectioned
 * hides the best match somewhere down the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDrawerSheet(
    apps: List<LaunchableApp>,
    categoryByKey: Map<String, AppCategory>,
    usageCountByKey: Map<String, Int>,
    sortMode: DrawerSortMode,
    categoryBarVisible: Boolean,
    viewModel: LauncherViewModel,
    autoFocusSearch: Boolean,
    onLaunch: (LaunchableApp) -> Unit,
    onLongPress: (LaunchableApp) -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHiddenApps: () -> Unit
) {
    val query = remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    var showOptions by remember { mutableStateOf(false) }
    // Null is "All". Not remembered across openings on purpose: the drawer is opened to
    // find something, and reopening it already filtered to a tab chosen minutes ago
    // reads as apps having gone missing.
    var selectedCategory by remember { mutableStateOf<AppCategory?>(null) }

    // Only when the user arrived via the search pill. Opening the keyboard on a swipe
    // up from the home screen would cover the apps they came to look at.
    LaunchedEffect(autoFocusSearch) {
        if (autoFocusSearch) focusRequester.requestFocus()
    }

    val searchResults = remember(apps, query.value) { searchApps(query.value, apps) }
    val searching = query.value.isNotBlank()
    // Clears the search first — losing what you typed along with the drawer itself
    // would make "go back" behave like "start over".
    BackHandler(enabled = searching) { query.value = "" }
    // Same idea one level out: back off a category tab before back closes the drawer.
    BackHandler(enabled = !searching && selectedCategory != null) { selectedCategory = null }
    // Search ranking already means something (best match first); the sort toggle only
    // applies once the user is browsing rather than typing.
    val results = remember(searchResults, searching, sortMode, usageCountByKey) {
        if (searching) searchResults else searchResults.sortedFor(sortMode, usageCountByKey)
    }

    if (showOptions) {
        DrawerOptionsSheet(
            sortMode = sortMode,
            onSortModeChange = viewModel::setDrawerSortMode,
            categoryBarVisible = categoryBarVisible,
            onCategoryBarVisibleChange = viewModel::setCategoryBarVisible,
            onOpenHiddenApps = {
                showOptions = false
                onOpenHiddenApps()
            },
            onDismiss = { showOptions = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = Spacing.gutter,
                    end = Spacing.sm,
                    top = Spacing.md,
                    bottom = Spacing.md
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query.value,
                onValueChange = { query.value = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.search_apps)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searching) {
                        IconButton(onClick = { query.value = "" }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.clear_search)
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                // Enter launches the top hit, so two characters and Go is the whole
                // interaction for an app whose name you know.
                keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.let(onLaunch) }),
                shape = MaterialTheme.shapes.extraLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
            )
            IconButton(onClick = { showOptions = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.drawer_options)
                )
            }
        }

        if (searching && results.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.no_apps_match),
                body = stringResource(R.string.no_apps_match_body, query.value),
                icon = Icons.Default.SearchOff,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xxl, bottom = Spacing.lg)
            )
            SearchElsewhere(query = query.value)
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = DRAWER_MIN_CELL),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                bottom = Spacing.xxl
            ),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            // fill = false so a short drawer still hugs its content instead of
            // stretching to the full sheet with the tab bar stranded at the bottom.
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
        ) {
            if (searching) {
                // Ranked order, no sections: the first cell is the hit Go would launch.
                items(results, key = { it.key }) { app ->
                    AppIconCell(
                        app = app,
                        viewModel = viewModel,
                        onClick = { onLaunch(app) },
                        onLongClick = { onLongPress(app) },
                        onWallpaper = false
                    )
                }
                // Under the matches, not instead of them: "the app I meant is not
                // installed" is a normal outcome of a search that did find something.
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SearchElsewhere(query = query.value)
                }
                return@LazyVerticalGrid
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    DrawerShortcut(
                        Icons.Default.Person,
                        stringResource(R.string.nav_profiles),
                        onOpenProfiles
                    )
                    DrawerShortcut(
                        Icons.Default.Settings,
                        stringResource(R.string.nav_settings),
                        onOpenSettings
                    )
                }
            }

            if (!categoryBarVisible) {
                // Same cells, no headers: the toggle turns off grouping, not sorting.
                items(results, key = { it.key }) { app ->
                    AppIconCell(
                        app = app,
                        viewModel = viewModel,
                        onClick = { onLaunch(app) },
                        onLongClick = { onLongPress(app) },
                        onWallpaper = false
                    )
                }
                return@LazyVerticalGrid
            }

            // Enum order, so the sections are stable between openings rather than
            // shuffling with whatever happens to be installed. Other last: it is the
            // "we could not place these" bucket, not a category anyone looks for first.
            val grouped = results.groupBy { categoryByKey[it.key] ?: AppCategory.Other }
            val chosen = selectedCategory
            if (chosen != null) {
                // One tab: the strip above already names it, so no header repeats it.
                val inCategory = grouped[chosen].orEmpty()
                if (inCategory.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            title = stringResource(chosen.labelRes),
                            body = stringResource(R.string.category_empty_body),
                            icon = Icons.Default.SearchOff,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.xxl)
                        )
                    }
                }
                items(inCategory, key = { it.key }) { app ->
                    AppIconCell(
                        app = app,
                        viewModel = viewModel,
                        onClick = { onLaunch(app) },
                        onLongClick = { onLongPress(app) },
                        onWallpaper = false
                    )
                }
                return@LazyVerticalGrid
            }
            AppCategory.entries.forEach { category ->
                val inCategory = grouped[category] ?: return@forEach
                item(span = { GridItemSpan(maxLineSpan) }, key = "header-${category.id}") {
                    SectionHeader(stringResource(category.labelRes))
                }
                items(inCategory, key = { it.key }) { app ->
                    AppIconCell(
                        app = app,
                        viewModel = viewModel,
                        onClick = { onLaunch(app) },
                        onLongClick = { onLongPress(app) },
                        onWallpaper = false
                    )
                }
            }
        }

        // Below the grid, where the reference launcher puts it and where a thumb
        // already is. Only when there is something to switch between, and never while
        // searching — a ranked result list that is also filtered hides the match the
        // user is typing towards.
        if (categoryBarVisible && !searching) {
            val present = remember(results, categoryByKey) {
                AppCategory.entries.filter { category ->
                    results.any { (categoryByKey[it.key] ?: AppCategory.Other) == category }
                }
            }
            if (present.size > 1) {
                CategoryBar(
                    categories = present,
                    selected = selectedCategory,
                    onSelect = { selectedCategory = it }
                )
            }
        }
    }
}

/**
 * Alphabetical is a no-op: [com.tanvoid0.portallauncher.data.AppRepository] already
 * hands back apps sorted by label, so this only ever does work for "most used".
 * [sortedByDescending] is stable, so ties fall back to that alphabetical order rather
 * than shuffling every time launch counts happen to tie.
 */
private fun List<LaunchableApp>.sortedFor(
    mode: DrawerSortMode,
    usageCountByKey: Map<String, Int>
): List<LaunchableApp> = when (mode) {
    DrawerSortMode.ALPHABETICAL -> this
    DrawerSortMode.MOST_USED -> sortedByDescending { usageCountByKey[it.key] ?: 0 }
}

/**
 * What the drawer's overflow menu offers: how it orders apps, whether it groups them
 * at all, and the way back to an app you hid. Same shape as [AppContextMenu] — one
 * [ModalBottomSheet], a title, a [PortalGroup] of actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawerOptionsSheet(
    sortMode: DrawerSortMode,
    onSortModeChange: (DrawerSortMode) -> Unit,
    categoryBarVisible: Boolean,
    onCategoryBarVisibleChange: (Boolean) -> Unit,
    onOpenHiddenApps: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                text = stringResource(R.string.drawer_options),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md)
            )
            PortalGroup(modifier = Modifier.selectableGroup()) {
                SelectableRow(
                    title = stringResource(R.string.sort_alphabetical),
                    selected = sortMode == DrawerSortMode.ALPHABETICAL,
                    onSelect = { onSortModeChange(DrawerSortMode.ALPHABETICAL) }
                )
                SelectableRow(
                    title = stringResource(R.string.sort_most_used),
                    selected = sortMode == DrawerSortMode.MOST_USED,
                    onSelect = { onSortModeChange(DrawerSortMode.MOST_USED) }
                )
            }
            PortalGroup {
                PortalRow(
                    title = stringResource(R.string.category_bar),
                    subtitle = stringResource(R.string.category_bar_subtitle),
                    trailing = {
                        Switch(
                            checked = categoryBarVisible,
                            onCheckedChange = onCategoryBarVisibleChange
                        )
                    }
                )
                PortalRow(
                    icon = Icons.Default.VisibilityOff,
                    title = stringResource(R.string.hidden_apps),
                    onClick = onOpenHiddenApps
                )
            }
        }
    }
}

/**
 * The drawer's category tabs.
 *
 * The reference launcher's closest analog to a profile: a strip of named, icon-labelled
 * groups that filters what the grid shows. Portal already sorted the drawer into these
 * categories but only as headings you had to scroll past — seven sections deep, "where
 * are my games" was a scroll, not a tap.
 */
@Composable
private fun CategoryBar(
    categories: List<AppCategory>,
    selected: AppCategory?,
    onSelect: (AppCategory?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.gutter, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.category_all)) }
        )
        categories.forEach { category ->
            FilterChip(
                selected = selected == category,
                // Tapping the tab you are on goes back to everything, so the strip
                // never becomes a state you can only leave with the back gesture.
                onClick = { onSelect(if (selected == category) null else category) },
                label = { Text(stringResource(category.labelRes)) },
                leadingIcon = {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        modifier = Modifier.size(CATEGORY_CHIP_ICON)
                    )
                }
            )
        }
    }
}

/** Material's own FilterChip leading-icon size. */
private val CATEGORY_CHIP_ICON = 18.dp

/**
 * Where a search goes when the answer is not an installed app.
 *
 * The drawer's search used to end at "No apps match", which is a dead end for the two
 * things people actually mean by then: look this up, or go and get it. Both are plain
 * intents — no permission, no API, and whichever browser and store the device already
 * has. Rendered only if something can handle them, so a device with no browser does not
 * get a row that fails when tapped.
 */
@Composable
private fun SearchElsewhere(query: String) {
    val context = LocalContext.current
    val web = remember(query) { webSearchIntent(query) }
    val store = remember(query) { playStoreIntent(query) }
    val canWeb = remember(web) { web.resolveActivity(context.packageManager) != null }
    val canStore = remember(store) { store.resolveActivity(context.packageManager) != null }
    if (!canWeb && !canStore) return
    PortalGroup {
        if (canWeb) {
            PortalRow(
                title = stringResource(R.string.search_the_web, query),
                icon = Icons.Default.Language,
                onClick = { context.startActivity(web) }
            )
        }
        if (canStore) {
            PortalRow(
                title = stringResource(R.string.search_play_store, query),
                icon = Icons.Default.Shop,
                onClick = { context.startActivity(store) }
            )
        }
    }
}

private fun webSearchIntent(query: String): Intent =
    Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)

/**
 * The `market:` scheme rather than a play.google.com URL, so this opens the store app
 * instead of bouncing through a browser. Falls back to nothing where no store handles
 * it — see the resolve check above.
 */
private fun playStoreIntent(query: String): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(query)))
