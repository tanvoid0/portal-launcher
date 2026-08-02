package com.tanvoid0.portallauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A profile must always be in effect. Returning null disabled all filtering *and*
 * left nothing selected in the profile list, so the two screens disagreed about what
 * was active — which is why both now go through this one function.
 */
class ResolveActiveProfileTest {

    private fun profile(id: String) = ProfileEntity(
        id = id,
        name = id,
        iconResName = id,
        type = ProfileType.Custom.name,
        enabledAutomationIds = emptyList()
    )

    private val profiles = listOf(profile(BuiltInProfiles.DEFAULT_ID), profile("study"))

    @Test
    fun `the stored profile wins when it exists`() {
        assertEquals("study", resolveActiveProfile(profiles, "study")?.id)
    }

    @Test
    fun `a dangling id falls back to the default profile`() {
        // What happens after the user deletes the profile that was active.
        assertEquals(BuiltInProfiles.DEFAULT_ID, resolveActiveProfile(profiles, "deleted")?.id)
    }

    @Test
    fun `no stored id falls back to the default profile`() {
        assertEquals(BuiltInProfiles.DEFAULT_ID, resolveActiveProfile(profiles, null)?.id)
    }

    @Test
    fun `without the default profile it falls back to the first one`() {
        // The user is allowed to delete "All apps" too.
        val remaining = listOf(profile("study"), profile("gaming"))
        assertEquals("study", resolveActiveProfile(remaining, "deleted")?.id)
    }

    @Test
    fun `an empty table resolves to nothing, which is the pre-seed state`() {
        assertNull(resolveActiveProfile(emptyList(), "study"))
    }
}
