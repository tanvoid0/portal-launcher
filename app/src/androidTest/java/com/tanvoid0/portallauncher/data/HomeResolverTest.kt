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
 * easy to get subtly wrong is pinned: the fallback, pins outranking the category
 * filter, pins whose app went away, and hiding beating both.
 *
 * Instrumented rather than a JVM test only because [LaunchableApp] holds a real
 * [android.os.UserHandle], which has no constructor available off-device. The logic
 * under test is pure — nothing here touches a database or a system service.
 */
@RunWith(AndroidJUnit4::class)
class HomeResolverTest {

    private val me = Process.myUserHandle()

    private fun app(pkg: String, label: String = pkg) = LaunchableApp(
        packageName = pkg,
        activityName = "$pkg.Main",
        user = me,
        userSerial = 0L,
        label = label,
        isWorkProfile = false,
        systemCategory = 0
    )

    private fun pin(app: LaunchableApp, position: Int) = homeItemFor(app, "study", position)

    private val notes = app("com.example.notes", "Notes")
    private val game = app("com.example.game", "Game")
    private val mail = app("com.example.mail", "Mail")
    private val installed = listOf(notes, game, mail)

    @Test
    fun beforeAnyCustomisation_fallsBackToTheCategoryFilterAndRespectsTheLimit() {
        val resolved = resolveHomeApps(
            pinned = emptyList(),
            installed = installed,
            categoryFiltered = listOf(notes, mail),
            fallbackLimit = 1,
            isCustomised = false
        )
        assertEquals(listOf(notes), resolved)
    }

    @Test
    fun anEmptyCustomLayoutStaysEmpty() {
        // The bug this guards: inferring the fallback from `pinned.isEmpty()` meant
        // unpinning the last app brought every removed app straight back.
        val resolved = resolveHomeApps(
            pinned = emptyList(),
            installed = installed,
            categoryFiltered = listOf(notes, mail, game),
            fallbackLimit = 8,
            isCustomised = true
        )
        assertEquals(emptyList<LaunchableApp>(), resolved)
    }

    @Test
    fun pinnedAppsOutrankTheCategoryFilter() {
        // `game` is not in categoryFiltered at all; pinning it must still show it,
        // otherwise an explicit choice loses to a category guess.
        val resolved = resolveHomeApps(
            pinned = listOf(pin(game, 0)),
            installed = installed,
            categoryFiltered = listOf(notes, mail),
            fallbackLimit = 8,
            isCustomised = true
        )
        assertEquals(listOf(game), resolved)
    }

    @Test
    fun pinnedAppsComeBackInStoredOrder() {
        val resolved = resolveHomeApps(
            pinned = listOf(pin(mail, 2), pin(notes, 0), pin(game, 1)),
            installed = installed,
            categoryFiltered = emptyList(),
            fallbackLimit = 8,
            isCustomised = true
        )
        assertEquals(listOf(notes, game, mail), resolved)
    }

    @Test
    fun aPinnedAppThatIsNoLongerInstalledIsSkipped() {
        val resolved = resolveHomeApps(
            pinned = listOf(pin(notes, 0), pin(app("com.example.gone"), 1)),
            installed = installed,
            categoryFiltered = emptyList(),
            fallbackLimit = 8,
            isCustomised = true
        )
        assertEquals(listOf(notes), resolved)
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
    fun isPinnedMatchesOnIdentityNotOnLabel() {
        val pinned = listOf(pin(notes, 0))
        assertTrue(isPinned(pinned, notes))
        // Same package, renamed: still the same app.
        assertTrue(isPinned(pinned, notes.copy(customLabel = "Something else")))
        assertFalse(isPinned(pinned, game))
    }
}
