package com.tanvoid0.portallauncher.data

import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * These functions decide what appears on someone's home screen, so each rule that is
 * easy to get subtly wrong is pinned: the fallback, placements outranking the category
 * filter, cells whose app went away, widgets whose provider went away, and hiding
 * beating all of it.
 *
 * Instrumented rather than a JVM test only because [LaunchableApp] holds a real
 * [android.os.UserHandle], which has no constructor available off-device. The placement
 * rules themselves are in [HomeGridTest], which needs no device at all.
 */
@RunWith(AndroidJUnit4::class)
class HomeResolverTest {

    private val me = Process.myUserHandle()
    private val grid = GridSize(columns = 4, rows = 5)

    private fun app(pkg: String, label: String = pkg) = LaunchableApp(
        packageName = pkg,
        activityName = "$pkg.Main",
        user = me,
        userSerial = 0L,
        label = label,
        isWorkProfile = false,
        systemCategory = 0
    )

    private fun placed(app: LaunchableApp, x: Int, y: Int = 0, page: Int = 0) =
        homeCellFor(app, "study", Slot(page, x, y))

    private fun widget(appWidgetId: Int, x: Int = 0, y: Int = 0) = HomeCellEntity(
        profileId = "study",
        page = 0,
        cellX = x,
        cellY = y,
        spanX = 2,
        spanY = 2,
        kind = HomeCellEntity.KIND_WIDGET,
        packageName = "com.example.clock",
        activityName = "com.example.clock.Provider",
        userSerial = 0L,
        appWidgetId = appWidgetId
    )

    private val notes = app("com.example.notes", "Notes")
    private val game = app("com.example.game", "Game")
    private val mail = app("com.example.mail", "Mail")
    private val installed = listOf(notes, game, mail)

    private fun resolve(
        cells: List<HomeCellEntity>,
        categoryFiltered: List<LaunchableApp> = emptyList(),
        liveWidgetIds: Set<Int> = emptySet(),
        fallbackLimit: Int = 8,
        isCustomised: Boolean = true
    ) = resolveHomeEntries(
        cells = cells,
        installed = installed,
        categoryFiltered = categoryFiltered,
        liveWidgetIds = liveWidgetIds,
        profileId = "study",
        grid = grid,
        fallbackLimit = fallbackLimit,
        isCustomised = isCustomised
    )

    private fun apps(entries: List<HomeEntry>) =
        entries.filterIsInstance<HomeEntry.App>().map { it.app }

    @Test
    fun beforeAnyCustomisationFallsBackToTheCategoryFilterAndRespectsTheLimit() {
        val resolved = resolve(
            cells = emptyList(),
            categoryFiltered = listOf(notes, mail),
            fallbackLimit = 1,
            isCustomised = false
        )
        assertEquals(listOf(notes), apps(resolved))
    }

    @Test
    fun theFallbackLaysAppsOutInReadingOrderOnPageOne() {
        val resolved = resolve(
            cells = emptyList(),
            categoryFiltered = listOf(notes, mail, game),
            isCustomised = false
        )
        assertEquals(
            listOf(Slot(0, 0, 0), Slot(0, 1, 0), Slot(0, 2, 0)),
            resolved.map { it.cell.slot }
        )
    }

    @Test
    fun anEmptyCustomLayoutStaysEmpty() {
        // The bug this guards: inferring the fallback from an empty layout meant
        // removing the last icon brought every removed app straight back.
        val resolved = resolve(
            cells = emptyList(),
            categoryFiltered = listOf(notes, mail, game),
            isCustomised = true
        )
        assertEquals(emptyList<LaunchableApp>(), apps(resolved))
    }

    @Test
    fun placedAppsOutrankTheCategoryFilter() {
        // `game` is not in categoryFiltered at all; placing it must still show it,
        // otherwise an explicit choice loses to a category guess.
        val resolved = resolve(
            cells = listOf(placed(game, 0)),
            categoryFiltered = listOf(notes, mail)
        )
        assertEquals(listOf(game), apps(resolved))
    }

    @Test
    fun aPlacedAppThatIsNoLongerInstalledIsSkipped() {
        val resolved = resolve(
            cells = listOf(placed(notes, 0), placed(app("com.example.gone"), 1))
        )
        assertEquals(listOf(notes), apps(resolved))
    }

    @Test
    fun aWidgetSurvivesOnlyWhileTheHostStillHoldsIt() {
        val cells = listOf(widget(appWidgetId = 7), placed(notes, 2))
        assertEquals(2, resolve(cells, liveWidgetIds = setOf(7)).size)
        // Provider uninstalled: the row stays so a reinstall brings it back, but an
        // empty rectangle must not be drawn in the meantime.
        assertEquals(listOf(notes), apps(resolve(cells, liveWidgetIds = emptySet())))
        assertTrue(resolve(cells, liveWidgetIds = emptySet()).none { it is HomeEntry.Widget })
    }

    @Test
    fun hidingRemovesAnAppAndRenamingKeepsIt() {
        val overrides = mapOf(
            game.key to AppOverrideEntity(game.packageName, game.activityName, 0L, hidden = true),
            mail.key to AppOverrideEntity(
                mail.packageName,
                mail.activityName,
                0L,
                customLabel = "Inbox"
            )
        )
        val result = applyOverrides(installed, overrides)
        assertEquals(listOf("Notes", "Inbox"), result.map { it.displayLabel })
        // The app's own name is still there underneath, which the rename dialog shows.
        assertEquals("Mail", result.last().label)
    }

    @Test
    fun aBlankRenameFallsBackToTheRealName() {
        val overrides = mapOf(
            mail.key to AppOverrideEntity(mail.packageName, mail.activityName, 0L, customLabel = "  ")
        )
        assertEquals("Mail", applyOverrides(listOf(mail), overrides).single().displayLabel)
    }

    @Test
    fun isOnHomeMatchesOnIdentityNotOnLabel() {
        val cells = listOf(placed(notes, 0))
        assertTrue(isOnHome(cells, notes))
        // Same package, renamed: still the same app.
        assertTrue(isOnHome(cells, notes.copy(customLabel = "Something else")))
        assertFalse(isOnHome(cells, game))
        // A widget's package is a provider, never an app the drawer can launch.
        assertFalse(isOnHome(listOf(widget(1)), app("com.example.clock")))
    }
}
