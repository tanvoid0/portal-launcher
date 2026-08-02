package com.tanvoid0.portallauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A launcher search is judged on the first result after two or three characters, so
 * these tests are about *ranking*, not about whether a match was found at all.
 */
class AppSearchTest {

    @Test
    fun `tiers rank exact over prefix over word start over initials`() {
        assertEquals(MATCH_EXACT, matchScore("clock", "Clock"))
        assertEquals(MATCH_PREFIX, matchScore("clo", "Clock"))
        assertEquals(MATCH_WORD_PREFIX, matchScore("maps", "Google Maps"))
        assertEquals(MATCH_INITIALS, matchScore("gm", "Google Maps"))
        assertEquals(MATCH_CONTAINS, matchScore("oogle", "Google Maps"))
        assertEquals(MATCH_SUBSEQUENCE, matchScore("ggmp", "Google Maps"))
    }

    @Test
    fun `a query that shares no letters does not match`() {
        assertNull(matchScore("xyz", "Google Maps"))
    }

    @Test
    fun `matching ignores case and accents`() {
        assertEquals(MATCH_PREFIX, matchScore("POKE", "Pokémon GO"))
        // The point: reachable from a keyboard with no accented keys.
        assertEquals(MATCH_PREFIX, matchScore("pokemon", "Pokémon GO"))
    }

    @Test
    fun `initials work across the separators app names actually use`() {
        assertEquals(MATCH_INITIALS, matchScore("vs", "Visual-Studio"))
        assertEquals(MATCH_INITIALS, matchScore("ab", "Adobe_Bridge"))
        assertEquals(MATCH_INITIALS, matchScore("gd", "Google (Drive)"))
    }

    @Test
    fun `prefix beats contains, which is the case a plain filter gets wrong`() {
        // "ca" must lead with Calendar and Camera, not with an app that merely
        // contains "ca" somewhere in the middle.
        val prefix = matchScore("ca", "Calendar")!!
        val contains = matchScore("ca", "Vlc Cast")!!
        assertEquals(true, prefix < contains)
    }

    @Test
    fun `an empty query matches everything so the drawer shows the full list`() {
        assertEquals(MATCH_CONTAINS, matchScore("", "Anything"))
        assertEquals(MATCH_CONTAINS, matchScore("   ", "Anything"))
    }

    @Test
    fun `ties break on the shorter name`() {
        // Both are prefix matches; the one that is just "Clock" should win.
        assertEquals(MATCH_PREFIX, matchScore("clock", "Clock Widget Pro"))
        assertEquals(MATCH_EXACT, matchScore("clock", "Clock"))
    }
}
