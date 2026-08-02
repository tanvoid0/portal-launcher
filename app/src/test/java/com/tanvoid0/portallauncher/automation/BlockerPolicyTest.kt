package com.tanvoid0.portallauncher.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The block/allow decision. Getting it wrong in one direction annoys the user; in the
 * other it covers the launcher or the dialer with a wall — which is why [BlockerPolicy]
 * is pure enough to pin down here.
 */
class BlockerPolicyTest {

    private val blocked = setOf("com.example.social")
    private val never = setOf("com.tanvoid0.portallauncher", "com.android.dialer")
    private val now = 1_000_000L

    private fun decide(
        pkg: String?,
        allowedUntil: Map<String, Long> = emptyMap()
    ) = BlockerPolicy.shouldBlock(pkg, blocked, allowedUntil, now, never)

    @Test
    fun `a blocked app is blocked, everything else is not`() {
        assertTrue(decide("com.example.social"))
        assertFalse(decide("com.example.mail"))
        assertFalse(decide(null))
    }

    @Test
    fun `the launcher and the dialer are never blocked, even when listed`() {
        val policy = BlockerPolicy.shouldBlock(
            "com.android.dialer",
            blocked + "com.android.dialer" + "com.tanvoid0.portallauncher",
            emptyMap(),
            now,
            never
        )
        assertFalse(policy)
        assertFalse(
            BlockerPolicy.shouldBlock(
                "com.tanvoid0.portallauncher",
                blocked + "com.tanvoid0.portallauncher",
                emptyMap(),
                now,
                never
            )
        )
    }

    @Test
    fun `a snooze suppresses blocking until it expires, not after`() {
        val snoozed = mapOf("com.example.social" to now + BlockerPolicy.SNOOZE_MILLIS)
        assertFalse(decide("com.example.social", snoozed))
        // At the expiry instant the reprieve is over.
        val expired = mapOf("com.example.social" to now)
        assertTrue(decide("com.example.social", expired))
    }

    @Test
    fun `a session unlock never expires within the session`() {
        val unlocked = mapOf("com.example.social" to Long.MAX_VALUE)
        assertFalse(decide("com.example.social", unlocked))
    }
}
