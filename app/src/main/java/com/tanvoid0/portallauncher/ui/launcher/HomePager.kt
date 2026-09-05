package com.tanvoid0.portallauncher.ui.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tanvoid0.portallauncher.data.GridSize
import com.tanvoid0.portallauncher.data.HomeEntry
import com.tanvoid0.portallauncher.data.Slot
import com.tanvoid0.portallauncher.ui.kit.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How far an item has to travel before a long press counts as a drag and not a menu. */
private const val DRAG_SLOP_CELLS = 0.25f

/** How long the dragged item has to sit over a page edge before the pager turns. */
private const val EDGE_TURN_DELAY_MS = 450L

/** How much of the width, each side, counts as the turn-the-page zone during a drag. */
private const val EDGE_FRACTION = 0.12f

/**
 * The home screen's pages.
 *
 * Absolute cell placement rather than a `LazyVerticalGrid`, because a widget occupies a
 * rectangle and a lazy grid can only lay out a sequence. It also makes the drag cheap:
 * with a cell size in hand, a pointer position *is* a cell.
 *
 * One gesture rule the whole grid depends on: a long press starts a drag, and releasing
 * without having moved opens the item's menu. Attaching a separate `onLongClick` for the
 * menu would fire it at the same moment the drag begins, so the two share one detector
 * and are told apart by distance. [DRAG_SLOP_CELLS] is well above the touch slop so a
 * shaky long press still opens the menu.
 */
@Composable
fun HomePager(
    entries: List<HomeEntry>,
    grid: GridSize,
    pageCount: Int,
    viewModel: LauncherViewModel,
    onOpenMenu: (HomeEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    // One spare page while dragging, so there is somewhere to drop an item to create a
    // page. It disappears on release, and the layout drops empty pages itself, so pages
    // are never a thing the user has to add or tidy up.
    var dragging by remember { mutableStateOf<Slot?>(null) }
    val pagerState = rememberPagerState(pageCount = { pageCount + if (dragging != null) 1 else 0 })
    val scope = rememberCoroutineScope()
    // Off the first page, back returns to it rather than falling through to the
    // enclosing screen's own handler.
    BackHandler(enabled = pagerState.currentPage != 0) {
        scope.launch { pagerState.animateScrollToPage(0) }
    }

    BoxWithConstraints(modifier = modifier) {
        val cellWidth = maxWidth / grid.columns
        val cellHeight = maxHeight / grid.rows
        val density = LocalDensity.current
        val cellWidthPx = with(density) { cellWidth.toPx() }
        val cellHeightPx = with(density) { cellHeight.toPx() }
        val widthPx = with(density) { maxWidth.toPx() }

        var dragOffset by remember { mutableStateOf(Offset.Zero) }
        // Where the pointer is across the whole grid, for the edge-turn check. Tracked
        // separately from dragOffset because that one is relative to the item's origin.
        var pointerX by remember { mutableStateOf(0f) }

        EdgeTurn(
            active = dragging != null,
            pointerX = pointerX,
            widthPx = widthPx,
            pagerState = pagerState
        )

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            Box(modifier = Modifier.fillMaxSize()) {
                entries.filter { it.cell.page == page }.forEach { entry ->
                    val slot = Slot(entry.cell.page, entry.cell.cellX, entry.cell.cellY)
                    val isDragged = dragging == slot
                    Box(
                        modifier = Modifier
                            .offset(x = cellWidth * entry.cell.cellX, y = cellHeight * entry.cell.cellY)
                            .then(
                                if (isDragged) {
                                    Modifier.offset {
                                        IntOffset(dragOffset.x.toInt(), dragOffset.y.toInt())
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .size(cellWidth * entry.cell.spanX, cellHeight * entry.cell.spanY)
                            .alpha(if (isDragged) 0.6f else 1f)
                            .pointerInput(slot, grid) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { start ->
                                        dragging = slot
                                        dragOffset = Offset.Zero
                                        pointerX = cellWidthPx * entry.cell.cellX + start.x
                                    },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        dragOffset += delta
                                        pointerX += delta.x
                                    },
                                    onDragCancel = {
                                        dragging = null
                                        dragOffset = Offset.Zero
                                    },
                                    onDragEnd = {
                                        val moved = dragOffset.getDistance() >
                                            DRAG_SLOP_CELLS * minOf(cellWidthPx, cellHeightPx)
                                        val landedOn = pagerState.currentPage
                                        if (!moved) {
                                            onOpenMenu(entry)
                                        } else if (landedOn != slot.page) {
                                            // The item crossed pages, so its offset says
                                            // nothing about where on the new page it is.
                                            viewModel.moveHomeCellToPage(slot, landedOn)
                                        } else {
                                            viewModel.moveHomeCell(
                                                from = slot,
                                                to = targetSlot(
                                                    page = landedOn,
                                                    cell = entry.cell.cellX to entry.cell.cellY,
                                                    span = entry.cell.spanX to entry.cell.spanY,
                                                    offset = dragOffset,
                                                    cellWidthPx = cellWidthPx,
                                                    cellHeightPx = cellHeightPx,
                                                    grid = grid
                                                )
                                            )
                                        }
                                        dragging = null
                                        dragOffset = Offset.Zero
                                    }
                                )
                            }
                            // A second detector so a plain tap still launches: the drag
                            // detector above swallows long presses, and this one's
                            // no-op onLongPress stops a long press also counting as a tap.
                            .pointerInput(slot) {
                                detectTapGestures(
                                    onLongPress = {},
                                    onTap = {
                                        if (entry is HomeEntry.App) viewModel.launch(entry.app)
                                    }
                                )
                            }
                    ) {
                        when (entry) {
                            is HomeEntry.App -> AppIconCell(
                                app = entry.app,
                                viewModel = viewModel,
                                onClick = null,
                                onLongClick = null,
                                onWallpaper = true,
                                modifier = Modifier.align(Alignment.Center)
                            )

                            is HomeEntry.Widget -> WidgetCell(
                                appWidgetId = entry.cell.appWidgetId,
                                widthDp = (cellWidth * entry.cell.spanX).value.toInt(),
                                heightDp = (cellHeight * entry.cell.spanY).value.toInt()
                            )
                        }
                    }
                }
            }
        }

        if (pagerState.pageCount > 1) {
            PageDots(
                count = pagerState.pageCount,
                current = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Spacing.sm)
            )
        }
    }
}

/**
 * Turns the page when the dragged item is held against an edge.
 *
 * A delay rather than an immediate flip: the drag that *starts* near an edge is the
 * common case, and turning the page out from under it makes the grid feel possessed.
 */
@Composable
private fun EdgeTurn(
    active: Boolean,
    pointerX: Float,
    widthPx: Float,
    pagerState: PagerState
) {
    val edge = when {
        !active || widthPx <= 0f -> 0
        pointerX < widthPx * EDGE_FRACTION -> -1
        pointerX > widthPx * (1f - EDGE_FRACTION) -> 1
        else -> 0
    }
    LaunchedEffect(edge, pagerState.currentPage) {
        if (edge == 0) return@LaunchedEffect
        delay(EDGE_TURN_DELAY_MS)
        val next = pagerState.currentPage + edge
        if (next in 0 until pagerState.pageCount) pagerState.animateScrollToPage(next)
    }
}

/**
 * Which cell the drag ended over.
 *
 * Rounds rather than truncates so an item that has been nudged more than half a cell
 * lands in the next one — truncating means a drag has to cross a whole cell before
 * anything happens, which reads as the grid ignoring you.
 */
private fun targetSlot(
    page: Int,
    cell: Pair<Int, Int>,
    span: Pair<Int, Int>,
    offset: Offset,
    cellWidthPx: Float,
    cellHeightPx: Float,
    grid: GridSize
): Slot {
    val x = cell.first + Math.round(offset.x / cellWidthPx)
    val y = cell.second + Math.round(offset.y / cellHeightPx)
    return Slot(
        page = page,
        cellX = x.coerceIn(0, grid.columns - span.first),
        cellY = y.coerceIn(0, grid.rows - span.second)
    )
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (index == current) 0.9f else 0.35f
                        )
                    )
            )
        }
    }
}
