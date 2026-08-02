package com.tanvoid0.portallauncher.data

/**
 * Where things are allowed to sit on the home screen.
 *
 * Pure functions over [HomeCellEntity], with no Android types anywhere, so the rules
 * that decide whether a drag is legal are unit-testable off device. Placement is the
 * part of a launcher that is easy to get subtly wrong and impossible to notice until a
 * widget is drawn on top of an app.
 */

/**
 * The home grid, in cells.
 *
 * Columns are the user's choice; rows follow from it and the space available, because
 * a row count that does not fit means icons under the dock. See
 * [com.tanvoid0.portallauncher.ui.launcher.HomePager].
 */
data class GridSize(val columns: Int, val rows: Int) {
    val perPage: Int get() = columns * rows

    companion object {
        /**
         * What a fresh install gets, and what the 5→6 migration assumed when it turned
         * linear positions into cells. Changing this does not re-lay-out anyone's home
         * screen — existing cells keep their coordinates — so it is safe to tune.
         */
        val Default = GridSize(columns = 4, rows = 5)
    }
}

/** A place something could go: which page, and the top-left cell on it. */
data class Slot(val page: Int, val cellX: Int, val cellY: Int)

/**
 * How the pre-pages home layout is read.
 *
 * Before schema 6 the home screen was a flat list with one `position` per app. Two
 * places still have to interpret those numbers — the 5→6 migration and a backup file
 * written by an older build — and they must agree forever, so the arithmetic lives here
 * once.
 *
 * **These constants are frozen.** They are not [GridSize.Default] and must not be
 * changed to follow it: they describe how already-written data is read, so tying them
 * to a tunable default would silently re-lay-out home screens that were converted
 * months ago.
 */
object LegacyLayout {
    const val COLUMNS = 4
    const val PER_PAGE = 20

    fun slotFor(position: Int) = Slot(
        page = position / PER_PAGE,
        cellX = (position % PER_PAGE) % COLUMNS,
        cellY = (position % PER_PAGE) / COLUMNS
    )
}

val HomeCellEntity.slot: Slot get() = Slot(page, cellX, cellY)

/** True when the two rectangles share any cell. Both are half-open in each axis. */
fun overlaps(a: HomeCellEntity, b: HomeCellEntity): Boolean =
    a.page == b.page &&
        a.cellX < b.cellX + b.spanX && b.cellX < a.cellX + a.spanX &&
        a.cellY < b.cellY + b.spanY && b.cellY < a.cellY + a.spanY

/** True when the whole rectangle is inside the grid. */
fun fitsInGrid(cell: HomeCellEntity, grid: GridSize): Boolean =
    cell.cellX >= 0 && cell.cellY >= 0 &&
        cell.spanX >= 1 && cell.spanY >= 1 &&
        cell.cellX + cell.spanX <= grid.columns &&
        cell.cellY + cell.spanY <= grid.rows

/**
 * True when [candidate] can go where it says it wants to go.
 *
 * [ignoring] is the slot the thing being moved currently occupies, so a cell can be
 * tested against its own future position without colliding with itself — without it,
 * nudging a widget one column right is always rejected.
 */
fun canPlace(
    existing: List<HomeCellEntity>,
    candidate: HomeCellEntity,
    grid: GridSize,
    ignoring: Slot? = null
): Boolean =
    fitsInGrid(candidate, grid) &&
        existing.none { it.slot != ignoring && overlaps(it, candidate) }

/**
 * The first free top-left cell for a `spanX` × `spanY` rectangle on [page], scanning
 * in reading order. Null when the page has no room.
 */
fun firstFreeSlot(
    existing: List<HomeCellEntity>,
    page: Int,
    spanX: Int,
    spanY: Int,
    grid: GridSize
): Slot? {
    val onPage = existing.filter { it.page == page }
    for (y in 0..grid.rows - spanY) {
        for (x in 0..grid.columns - spanX) {
            val free = onPage.none {
                x < it.cellX + it.spanX && it.cellX < x + spanX &&
                    y < it.cellY + it.spanY && it.cellY < y + spanY
            }
            if (free) return Slot(page, x, y)
        }
    }
    return null
}

/**
 * Somewhere for a new item to land: the first free slot on the first page that has
 * room, and a new page past the end if none does.
 *
 * Growing the page count rather than refusing the placement is deliberate — "pin this
 * app" failing because page 1 is full is a dead end the user cannot diagnose, and
 * pages are free.
 */
fun placeAnywhere(
    existing: List<HomeCellEntity>,
    spanX: Int,
    spanY: Int,
    grid: GridSize
): Slot {
    val pages = pageCount(existing)
    for (page in 0 until pages) {
        firstFreeSlot(existing, page, spanX, spanY, grid)?.let { return it }
    }
    return Slot(pages, 0, 0)
}

/** How many pages the layout spans. Always at least one, even when empty. */
fun pageCount(cells: List<HomeCellEntity>): Int =
    (cells.maxOfOrNull { it.page } ?: 0) + 1

/**
 * Closes gaps left by emptying a page, and drops empty pages off the end.
 *
 * Run after a removal rather than during one: deleting the only icon on page 2 while
 * the user is looking at page 2 should not yank the pager to a different page
 * mid-gesture. Page 0 always survives, because a launcher with no pages has nothing
 * to draw.
 */
fun normalisePages(cells: List<HomeCellEntity>): List<HomeCellEntity> {
    if (cells.isEmpty()) return cells
    val used = cells.map { it.page }.distinct().sorted()
    val renumbered = used.withIndex().associate { (index, page) -> page to index }
    return cells.map { cell ->
        val target = renumbered.getValue(cell.page)
        if (target == cell.page) cell else cell.copy(page = target)
    }
}

/**
 * Moves whatever starts at [from] to [to], swapping with what is already there when
 * both are single cells.
 *
 * Returns null when the move is not legal, and the caller leaves the layout alone —
 * a rejected drag must not half-apply. Swapping is limited to 1×1 items on purpose:
 * exchanging rectangles of different sizes has no correct answer, and silently
 * relocating a widget the user did not touch is worse than refusing.
 */
fun moveCell(
    cells: List<HomeCellEntity>,
    from: Slot,
    to: Slot,
    grid: GridSize
): List<HomeCellEntity>? {
    if (from == to) return cells
    val moving = cells.firstOrNull { it.slot == from } ?: return null
    val moved = moving.copy(page = to.page, cellX = to.cellX, cellY = to.cellY)
    if (!fitsInGrid(moved, grid)) return null

    if (canPlace(cells, moved, grid, ignoring = from)) {
        return cells.map { if (it.slot == from) moved else it }
    }

    // Blocked. The one case worth rescuing is two icons trading places.
    val blocking = cells.filter { it.slot != from && overlaps(it, moved) }
    val other = blocking.singleOrNull() ?: return null
    if (moving.spanX != 1 || moving.spanY != 1 || other.spanX != 1 || other.spanY != 1) return null
    val swapped = other.copy(page = from.page, cellX = from.cellX, cellY = from.cellY)
    return cells.map {
        when (it.slot) {
            from -> moved
            other.slot -> swapped
            else -> it
        }
    }
}

/**
 * Re-lays-out [cells] so every one of them fits [grid], keeping what already does where
 * it is.
 *
 * Needed because the grid is a setting. Going from six columns to four leaves icons at
 * `cellX = 5`, which are simply not drawn — the user would see apps disappear and have
 * no way to guess that widening the grid brings them back. Rotation does not trigger
 * this: the grid is fixed in cells and it is the cells that get shorter.
 *
 * Reading order is the tie-break, so the result is stable: run it twice and nothing
 * moves the second time. Spans are clamped before placement, because a 5-wide widget in
 * a 4-wide grid can never be placed as it stands.
 */
fun reflow(cells: List<HomeCellEntity>, grid: GridSize): List<HomeCellEntity> {
    val placed = mutableListOf<HomeCellEntity>()
    cells.sortedWith(compareBy({ it.page }, { it.cellY }, { it.cellX })).forEach { cell ->
        val clamped = cell.copy(
            spanX = cell.spanX.coerceIn(1, grid.columns),
            spanY = cell.spanY.coerceIn(1, grid.rows)
        )
        val target = if (canPlace(placed, clamped, grid)) {
            clamped
        } else {
            // Its own page first. Falling straight through to placeAnywhere would pull
            // a cell from page three into a hole on page one, which is a re-ordering of
            // a layout the user built and never asked for.
            val slot = firstFreeSlot(placed, clamped.page, clamped.spanX, clamped.spanY, grid)
                ?: placeAnywhere(placed, clamped.spanX, clamped.spanY, grid)
            clamped.copy(page = slot.page, cellX = slot.cellX, cellY = slot.cellY)
        }
        placed += target
    }
    return normalisePages(placed)
}

/**
 * Turns an ordered list of apps into cells filling the grid in reading order.
 *
 * Used in one place that matters: the moment the user first customises a profile whose
 * home screen is still the category default. That default has to be written down as
 * real cells before the first edit, or pinning one app would appear to delete the rest
 * — the same trap the old flat layout had.
 */
fun cellsForApps(
    apps: List<LaunchableApp>,
    profileId: String,
    grid: GridSize
): List<HomeCellEntity> = apps.mapIndexed { index, app ->
    val onPage = index % grid.perPage
    homeCellFor(
        app = app,
        profileId = profileId,
        slot = Slot(
            page = index / grid.perPage,
            cellX = onPage % grid.columns,
            cellY = onPage / grid.columns
        )
    )
}

/** The row to store for an app at [slot]. */
fun homeCellFor(app: LaunchableApp, profileId: String, slot: Slot) = HomeCellEntity(
    profileId = profileId,
    page = slot.page,
    cellX = slot.cellX,
    cellY = slot.cellY,
    spanX = 1,
    spanY = 1,
    kind = HomeCellEntity.KIND_APP,
    packageName = app.packageName,
    activityName = app.activityName,
    userSerial = app.userSerial,
    appWidgetId = HomeCellEntity.NO_WIDGET
)
