package com.tanvoid0.portallauncher.ui.launcher

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import com.tanvoid0.portallauncher.PortalLauncherApplication
import com.tanvoid0.portallauncher.data.GridSize
import com.tanvoid0.portallauncher.data.HomeCellEntity
import com.tanvoid0.portallauncher.data.slot
import com.tanvoid0.portallauncher.ui.kit.EmptyState
import com.tanvoid0.portallauncher.ui.kit.PortalGroup
import com.tanvoid0.portallauncher.ui.kit.PortalRow
import com.tanvoid0.portallauncher.ui.kit.Spacing
import com.tanvoid0.portallauncher.widgets.LauncherWidgetHost
import com.tanvoid0.portallauncher.widgets.WidgetPlacement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One live widget, drawn by the system.
 *
 * The launcher never inflates a provider's layout itself — `AppWidgetHostView` renders
 * the `RemoteViews` the provider publishes, in a sandbox, which is why a crashing widget
 * cannot take the home screen with it.
 *
 * ponytail: a widget that scrolls horizontally will fight the pager for the gesture,
 * because the host view is an Android View and Compose's pager has no nested-scroll
 * relationship with it. Fixing that means `requestDisallowInterceptTouchEvent` from a
 * custom host view; worth doing when someone actually puts a horizontally scrolling
 * widget on a page, not before.
 */
@Composable
fun WidgetCell(appWidgetId: Int, widthDp: Int, heightDp: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val host = remember(context) {
        (context.applicationContext as PortalLauncherApplication).widgetHost
    }
    val info = remember(appWidgetId) { host.providerInfo(appWidgetId) }
    // Null once the provider is uninstalled. The resolver already drops those cells, so
    // this is the frame between the package event and the new state arriving.
    if (info == null) return

    // Not in AndroidView's `update`: that runs on every recomposition of the grid — a
    // drag recomposes it continuously — and reportSize is a Binder call on the main
    // thread. Keyed on the size, so it fires when the size actually changes.
    LaunchedEffect(appWidgetId, widthDp, heightDp) {
        withContext(Dispatchers.IO) { host.reportSize(appWidgetId, widthDp, heightDp) }
    }

    AndroidView(
        factory = { ctx -> host.createView(ctx, appWidgetId, info) },
        modifier = modifier.fillMaxSize()
    )
}

/**
 * Picks a widget to add.
 *
 * Our own list rather than `ACTION_APPWIDGET_PICK`: the system picker binds the chosen
 * widget on the caller's behalf and therefore needs `BIND_APPWIDGET`, which a
 * third-party launcher cannot hold. Listing providers ourselves and binding through the
 * consent flow is the only path that works — see
 * [com.tanvoid0.portallauncher.widgets.LauncherWidgetHost].
 *
 * Content rather than a sheet of its own. Closing one `ModalBottomSheet` and opening
 * another in the same frame races the first one's exit animation against the second
 * one's entrance, and the picker loses — tapping "Add widget" put the user back on an
 * empty home screen. Swapping the content of the sheet already on screen is what
 * [AppContextMenu] does for rename and category, for the same reason.
 */
@Composable
fun WidgetPickerContent(
    grid: GridSize,
    cellWidthDp: Int,
    cellHeightDp: Int,
    placement: WidgetPlacement?,
    viewModel: LauncherViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val host = remember(context) {
        (context.applicationContext as PortalLauncherApplication).widgetHost
    }

    // Loading providers walks every installed package's manifest, which is slow enough
    // to drop frames on a device with a lot of apps.
    val providers by produceState(emptyList<WidgetChoice>(), host) {
        value = withContext(Dispatchers.IO) {
            host.providers()
                .map { info ->
                    WidgetChoice(
                        info = info,
                        label = info.loadLabel(context.packageManager),
                        appLabel = runCatching {
                            context.packageManager
                                .getApplicationLabel(
                                    context.packageManager.getApplicationInfo(
                                        info.provider.packageName,
                                        0
                                    )
                                )
                                .toString()
                        }.getOrDefault(info.provider.packageName)
                    )
                }
                .sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Text(
            text = "Add widget",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md)
        )
        if (placement == null) {
            // No Activity to run the bind and configuration screens on. Offering the
            // list anyway would give the user a tap that silently does nothing.
            EmptyState(
                title = "Widgets unavailable",
                body = "Widgets can only be added from the home screen.",
                icon = Icons.Default.Widgets,
                modifier = Modifier.fillMaxWidth()
            )
            return@Column
        }
        // Bounded, because the sheet it sits in has no height of its own and an
        // unbounded LazyColumn inside one measures to zero.
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = PICKER_MAX_HEIGHT)
        ) {
            items(providers, key = { it.info.provider.flattenToString() }) { choice ->
                val (spanX, spanY) = LauncherWidgetHost.defaultSpan(
                    info = choice.info,
                    cellWidthDp = cellWidthDp,
                    cellHeightDp = cellHeightDp,
                    columns = grid.columns,
                    rows = grid.rows
                )
                PortalRow(
                    title = choice.label,
                    subtitle = "${choice.appLabel} · $spanX × $spanY",
                    trailing = { WidgetPreview(choice.info) },
                    onClick = {
                        scope.launch {
                            val id = placement.add(choice.info)
                            if (id != null) {
                                viewModel.placeWidget(
                                    providerPackage = choice.info.provider.packageName,
                                    providerClass = choice.info.provider.className,
                                    appWidgetId = id,
                                    spanX = spanX,
                                    spanY = spanY
                                )
                            }
                            onDismiss()
                        }
                    }
                )
            }
        }
    }
}

/**
 * How tall the provider list may get inside the options sheet. Half a phone screen:
 * enough to scan, short enough that the sheet still reads as a sheet.
 */
private val PICKER_MAX_HEIGHT = 420.dp

private data class WidgetChoice(
    val info: AppWidgetProviderInfo,
    val label: String,
    val appLabel: String
)

@Composable
private fun WidgetPreview(info: AppWidgetProviderInfo) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, info.provider) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val drawable = info.loadPreviewImage(context, 0) ?: info.loadIcon(context, 0)
                drawable?.toBitmap(PREVIEW_PX, PREVIEW_PX)?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
    }
}

private const val PREVIEW_PX = 120

/**
 * What long-pressing a widget offers: resize by a cell at a time, or remove it.
 *
 * Nudge buttons rather than drag handles. The handles are what a launcher of this size
 * should eventually have, but they need their own hit-testing against the grid, and
 * "one cell wider" is the whole of what people actually do with them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetContextMenu(
    cell: HomeCellEntity,
    viewModel: LauncherViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val slot = cell.slot
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = Spacing.lg)
        ) {
            Text(
                text = "Widget · ${cell.spanX} × ${cell.spanY}",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md)
            )
            Spacer(Modifier.height(Spacing.sm))
            PortalGroup {
                PortalRow(
                    icon = Icons.Default.Widgets,
                    title = "Wider",
                    onClick = { viewModel.resizeWidget(slot, cell.spanX + 1, cell.spanY) }
                )
                PortalRow(
                    icon = Icons.Default.Widgets,
                    title = "Narrower",
                    onClick = {
                        if (cell.spanX > 1) viewModel.resizeWidget(slot, cell.spanX - 1, cell.spanY)
                    }
                )
                PortalRow(
                    icon = Icons.Default.Height,
                    title = "Taller",
                    onClick = { viewModel.resizeWidget(slot, cell.spanX, cell.spanY + 1) }
                )
                PortalRow(
                    icon = Icons.Default.Height,
                    title = "Shorter",
                    onClick = {
                        if (cell.spanY > 1) viewModel.resizeWidget(slot, cell.spanX, cell.spanY - 1)
                    }
                )
                PortalRow(
                    icon = Icons.Default.Delete,
                    title = "Remove widget",
                    onClick = {
                        viewModel.removeHomeCell(slot)
                        onDismiss()
                    }
                )
            }
        }
    }
}
