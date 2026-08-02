package com.tanvoid0.portallauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Placement decides whether a drag is allowed and where a new icon or widget lands. It
 * is the part of the home screen that fails invisibly — a wrong answer draws a widget
 * on top of an app rather than throwing — so each rule that is easy to get subtly wrong
 * is pinned here.
 *
 * A plain JVM test: [HomeGrid] deliberately touches no Android type, unlike
 * [HomeResolverTest], which needs a real `UserHandle`.
 */
class HomeGridTest {

    private val grid = GridSize(columns = 4, rows = 5)

    private fun cell(
        page: Int = 0,
        x: Int = 0,
        y: Int = 0,
        spanX: Int = 1,
        spanY: Int = 1,
        name: String = "a"
    ) = HomeCellEntity(
        profileId = "study",
        page = page,
        cellX = x,
        cellY = y,
        spanX = spanX,
        spanY = spanY,
        kind = HomeCellEntity.KIND_APP,
        packageName = "com.example.$name",
        activityName = "com.example.$name.Main",
        userSerial = 0L,
        appWidgetId = HomeCellEntity.NO_WIDGET
    )

    @Test
    fun rectanglesOnlyOverlapOnTheSamePage() {
        val widget = cell(x = 0, y = 0, spanX = 2, spanY = 2)
        assertTrue(overlaps(widget, cell(x = 1, y = 1)))
        assertFalse(overlaps(widget, cell(x = 2, y = 0)))
        assertFalse(overlaps(widget, cell(x = 2, y = 2)))
        // Same coordinates, different page: the case a page-blind check gets wrong.
        assertFalse(overlaps(widget, cell(page = 1, x = 1, y = 1)))
    }

    @Test
    fun aRectangleHangingOffTheEdgeDoesNotFit() {
        assertTrue(fitsInGrid(cell(x = 2, y = 3, spanX = 2, spanY = 2), grid))
        assertFalse(fitsInGrid(cell(x = 3, y = 0, spanX = 2), grid))
        assertFalse(fitsInGrid(cell(x = 0, y = 4, spanY = 2), grid))
        assertFalse(fitsInGrid(cell(x = -1), grid))
    }

    @Test
    fun aCellCanBeTestedAgainstItsOwnPosition() {
        val existing = listOf(cell(x = 0, y = 0))
        val nudged = cell(x = 1, y = 0)
        // Without `ignoring`, moving something one column right always collides with
        // the row that is about to be replaced.
        assertTrue(canPlace(existing, nudged, grid, ignoring = Slot(0, 0, 0)))
        assertFalse(canPlace(existing, nudged.copy(cellX = 0), grid))
    }

    @Test
    fun theFirstFreeSlotIsFoundInReadingOrder() {
        val existing = listOf(cell(x = 0, y = 0), cell(x = 1, y = 0, name = "b"))
        assertEquals(Slot(0, 2, 0), firstFreeSlot(existing, 0, 1, 1, grid))
    }

    @Test
    fun aWidgetNeedsRoomForItsWholeRectangle() {
        // Only the last column is free on row 0, so a two-wide widget has to drop a row.
        val existing = (0..2).map { cell(x = it, y = 0, name = "a$it") }
        assertEquals(Slot(0, 0, 1), firstFreeSlot(existing, 0, 2, 1, grid))
    }

    @Test
    fun aFullPageIsAnsweredWithANewOne() {
        val full = (0 until grid.perPage).map {
            cell(x = it % grid.columns, y = it / grid.columns, name = "a$it")
        }
        assertEquals(Slot(1, 0, 0), placeAnywhere(full, 1, 1, grid))
        assertNull(firstFreeSlot(full, 0, 1, 1, grid))
    }

    @Test
    fun twoIconsSwapWhenOneIsDroppedOnTheOther() {
        val a = cell(x = 0, y = 0, name = "a")
        val b = cell(x = 1, y = 0, name = "b")
        val moved = moveCell(listOf(a, b), Slot(0, 0, 0), Slot(0, 1, 0), grid)!!
        assertEquals(
            setOf("com.example.a" to Slot(0, 1, 0), "com.example.b" to Slot(0, 0, 0)),
            moved.map { it.packageName to it.slot }.toSet()
        )
    }

    @Test
    fun droppingAnIconOnAWidgetIsRefusedRatherThanSwapped() {
        // Swapping rectangles of different sizes has no correct answer, and moving a
        // widget the user did not touch is worse than doing nothing.
        val widget = cell(x = 0, y = 0, spanX = 2, spanY = 2, name = "w")
        val icon = cell(x = 3, y = 0, name = "i")
        assertNull(moveCell(listOf(widget, icon), Slot(0, 3, 0), Slot(0, 1, 1), grid))
    }

    @Test
    fun aMoveOntoEmptySpaceKeepsEverythingElseWhereItIs() {
        val a = cell(x = 0, y = 0, name = "a")
        val b = cell(x = 1, y = 0, name = "b")
        val moved = moveCell(listOf(a, b), Slot(0, 0, 0), Slot(0, 0, 3), grid)!!
        assertEquals(Slot(0, 0, 3), moved.first { it.packageName == "com.example.a" }.slot)
        assertEquals(Slot(0, 1, 0), moved.first { it.packageName == "com.example.b" }.slot)
    }

    @Test
    fun emptyingAPageClosesTheGap() {
        val cells = listOf(cell(page = 0, name = "a"), cell(page = 2, name = "c"))
        assertEquals(listOf(0, 1), normalisePages(cells).map { it.page })
        // Page zero survives an empty layout, because a launcher with no pages has
        // nothing to draw.
        assertEquals(1, pageCount(emptyList()))
    }

    @Test
    fun narrowingTheGridBringsStrandedIconsBackInsteadOfHidingThem() {
        // The bug this guards: cells at cellX = 5 are simply never drawn by a
        // four-column grid, so a settings change looks like it deleted apps.
        val wide = listOf(cell(x = 5, y = 0, name = "a"), cell(x = 4, y = 1, name = "b"))
        val fixed = reflow(wide, grid)
        assertTrue(fixed.all { fitsInGrid(it, grid) })
        assertEquals(2, fixed.size)
    }

    @Test
    fun reflowLeavesAValidLayoutAloneAndIsIdempotent() {
        val fine = listOf(cell(x = 0, y = 0, name = "a"), cell(x = 3, y = 4, name = "b"))
        assertEquals(fine.toSet(), reflow(fine, grid).toSet())
        // Idempotence is what makes writing the result back safe: the ViewModel stores
        // it and immediately observes it again, and a second pass must find nothing.
        assertEquals(reflow(fine, grid), reflow(reflow(fine, grid), grid))
    }

    @Test
    fun aWidgetTooWideForTheGridIsShrunkRatherThanDropped() {
        val widget = cell(x = 0, y = 0, spanX = 6, spanY = 2, name = "w")
        val fixed = reflow(listOf(widget), grid).single()
        assertEquals(grid.columns, fixed.spanX)
        assertEquals(2, fixed.spanY)
    }

    @Test
    fun theLegacyLayoutReadsPositionsTheWayTheMigrationDoes() {
        // These numbers are frozen: the 5→6 migration and any old backup have to agree
        // forever about which cell a stored position means.
        assertEquals(Slot(0, 0, 0), LegacyLayout.slotFor(0))
        assertEquals(Slot(0, 3, 0), LegacyLayout.slotFor(3))
        assertEquals(Slot(0, 0, 1), LegacyLayout.slotFor(4))
        assertEquals(Slot(1, 0, 0), LegacyLayout.slotFor(20))
    }
}
