package com.tanvoid0.portallauncher.ui.launcher

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.tanvoid0.portallauncher.R
import com.tanvoid0.portallauncher.data.AppCategory
import com.tanvoid0.portallauncher.ui.kit.labelRes
import com.tanvoid0.portallauncher.data.LaunchableApp
import com.tanvoid0.portallauncher.data.searchApps
import com.tanvoid0.portallauncher.ui.kit.EmptyState
import com.tanvoid0.portallauncher.ui.kit.SectionHeader
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
@Composable
fun AppDrawerSheet(
    apps: List<LaunchableApp>,
    categoryByKey: Map<String, AppCategory>,
    viewModel: LauncherViewModel,
    autoFocusSearch: Boolean,
    onLaunch: (LaunchableApp) -> Unit,
    onLongPress: (LaunchableApp) -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val query = remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // Only when the user arrived via the search pill. Opening the keyboard on a swipe
    // up from the home screen would cover the apps they came to look at.
    LaunchedEffect(autoFocusSearch) {
        if (autoFocusSearch) focusRequester.requestFocus()
    }

    val results = remember(apps, query.value) { searchApps(query.value, apps) }
    val searching = query.value.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.gutter, vertical = Spacing.md)
                .focusRequester(focusRequester)
        )

        if (searching && results.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.no_apps_match),
                body = stringResource(R.string.no_apps_match_body, query.value),
                icon = Icons.Default.SearchOff,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.xxl)
            )
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
            modifier = Modifier.fillMaxWidth()
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

            // Enum order, so the sections are stable between openings rather than
            // shuffling with whatever happens to be installed. Other last: it is the
            // "we could not place these" bucket, not a category anyone looks for first.
            val grouped = results.groupBy { categoryByKey[it.key] ?: AppCategory.Other }
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
    }
}


