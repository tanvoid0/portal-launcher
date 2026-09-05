package com.tanvoid0.portallauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The gesture map itself is a lookup, but [neighbourProfileId] wraps, and wrapping is
 * where an off-by-one silently sends a gesture to the wrong profile.
 */
class LauncherGesturesTest {

    private val profiles = listOf("all", "study", "focus")

    @Test
    fun `moves forward and back through the list`() {
        assertEquals("focus", neighbourProfileId(profiles, "study", forward = true))
        assertEquals("all", neighbourProfileId(profiles, "study", forward = false))
    }

    @Test
    fun `wraps at both ends rather than stopping dead`() {
        assertEquals("all", neighbourProfileId(profiles, "focus", forward = true))
        assertEquals("focus", neighbourProfileId(profiles, "all", forward = false))
    }

    @Test
    fun `nothing to move to`() {
        assertNull(neighbourProfileId(emptyList(), null, forward = true))
        assertNull(neighbourProfileId(listOf("all"), "all", forward = true))
    }

    @Test
    fun `an active profile that is not in the list starts from the near end`() {
        // Happens between a profile being deleted and the list reloading.
        assertEquals("all", neighbourProfileId(profiles, "deleted", forward = true))
        assertEquals("focus", neighbourProfileId(profiles, null, forward = false))
    }

    @Test
    fun `every gesture has a default`() {
        // The preference flow reads DEFAULT_GESTURE_ACTIONS with getValue, which throws
        // on a gesture nobody added an entry for.
        LauncherGesture.entries.forEach { gesture ->
            assertEquals(true, DEFAULT_GESTURE_ACTIONS.containsKey(gesture))
        }
    }
}
