package com.tanvoid0.portallauncher.automation

import com.tanvoid0.portallauncher.data.NotificationFilterConfig
import com.tanvoid0.portallauncher.data.ScheduleSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scheduler's arithmetic, which is where a timetable feature actually breaks:
 * midnight wrap, the boundary minute itself, and "what is the next event" when the next
 * event is tomorrow.
 */
class ProfileSchedulerLogicTest {

    private fun at(hour: Int, minute: Int = 0) = hour * 60 + minute

    private val work = ScheduleSlot(at(9), at(17), "productivity")
    private val evening = ScheduleSlot(at(20), at(22), "social")
    private val night = ScheduleSlot(at(22), at(7), "wellness")

    @Test
    fun `a normal slot covers its start but not its end`() {
        // Half-open, so 17:00 belongs to whatever comes next rather than to both.
        assertTrue(work.covers(at(9)))
        assertTrue(work.covers(at(12, 30)))
        assertFalse(work.covers(at(17)))
        assertFalse(work.covers(at(8, 59)))
    }

    @Test
    fun `a slot crossing midnight covers both sides of it`() {
        // The shape every wind-down schedule takes, and the one a plain start <= t < end
        // comparison gets wrong.
        assertTrue(night.covers(at(23)))
        assertTrue(night.covers(at(2)))
        assertTrue(night.covers(at(22)))
        assertFalse(night.covers(at(7)))
        assertFalse(night.covers(at(12)))
    }

    @Test
    fun `the later slot wins where two overlap`() {
        val slots = listOf(night, evening)
        // 21:00 is inside both; the list order decides, so the schedule behaves the way
        // it reads top to bottom.
        assertEquals("social", slotFor(at(21), slots)?.profileId)
    }

    @Test
    fun `no slot covering now resolves to nothing rather than guessing`() {
        assertNull(slotFor(at(18), listOf(work, evening)))
    }

    @Test
    fun `the next boundary is the soonest one, wrapping to tomorrow`() {
        assertEquals(at(9), nextBoundaryMinutes(at(7), listOf(work)))
        assertEquals(at(17), nextBoundaryMinutes(at(9), listOf(work)))
        // Past every boundary today, so the answer is tomorrow's first one.
        assertEquals(at(9), nextBoundaryMinutes(at(18), listOf(work)))
    }

    @Test
    fun `both ends of a slot are boundaries, because leaving one is a transition too`() {
        assertEquals(at(22), nextBoundaryMinutes(at(21), listOf(night)))
        assertEquals(at(7), nextBoundaryMinutes(at(23), listOf(night)))
    }

    @Test
    fun `an empty schedule has no next boundary`() {
        assertNull(nextBoundaryMinutes(at(12), emptyList()))
    }
}

/**
 * The notification filter's precedence. An allow-list that quietly ANDs with a
 * block-list is how a filter ends up hiding a phone call.
 */
class NotificationFilterLogicTest {

    @Test
    fun `with no rules nothing is held back`() {
        assertFalse(shouldHoldBack("com.example.any", NotificationFilterConfig()))
    }

    @Test
    fun `a block list holds back only what it names`() {
        val config = NotificationFilterConfig(blockedPackageNames = listOf("com.example.social"))
        assertTrue(shouldHoldBack("com.example.social", config))
        assertFalse(shouldHoldBack("com.example.mail", config))
    }

    @Test
    fun `an allow list wins outright and ignores the block list`() {
        val config = NotificationFilterConfig(
            allowedPackageNames = listOf("com.android.dialer"),
            // Deliberately contradictory: the allow-list must decide alone.
            blockedPackageNames = listOf("com.android.dialer")
        )
        assertFalse("an allowed package must get through", shouldHoldBack("com.android.dialer", config))
        assertTrue(shouldHoldBack("com.example.anything", config))
    }

    @Test
    fun `an empty allow list holds everything back, which is what it says`() {
        val config = NotificationFilterConfig(allowedPackageNames = emptyList())
        assertTrue(shouldHoldBack("com.android.dialer", config))
    }
}
