package com.tanvoid0.portallauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Storage has to be boring: what goes in comes back, and what cannot be read falls
 * back instead of throwing. A config that crashes on decode crashes the home screen,
 * and a user with no home screen has no way to recover.
 */
class ConfigCodecTest {

    @Test
    fun `every config type round-trips`() {
        val visibility = AppVisibilityConfig(
            primaryCategoryIds = listOf("study", "productivity"),
            secondaryCategoryIds = listOf("social"),
            hiddenCategoryIds = listOf("gaming")
        )
        assertEquals(visibility, ConfigCodec.decodeOr(ConfigCodec.encode(visibility), AppVisibilityConfig()))

        val greyscale = GreyscaleConfig(enabled = true, intensity = 0.5f)
        assertEquals(greyscale, ConfigCodec.decodeOr(ConfigCodec.encode(greyscale), GreyscaleConfig()))

        val blocker = AppBlockerConfig(blockedPackageNames = listOf("com.instagram.android"), useOverlay = false)
        assertEquals(blocker, ConfigCodec.decodeOr(ConfigCodec.encode(blocker), AppBlockerConfig()))

        val notifications = NotificationFilterConfig(
            allowedPackageNames = listOf("com.android.dialer"),
            blockedPackageNames = listOf("com.example.spam"),
            cancelOnFilter = true
        )
        assertEquals(
            notifications,
            ConfigCodec.decodeOr(ConfigCodec.encode(notifications), NotificationFilterConfig())
        )

        val scheduler = SchedulerConfig(
            slots = listOf(
                ScheduleSlot(9 * 60, 17 * 60, "productivity"),
                // Crosses midnight; the codec must not care, only the scheduler does.
                ScheduleSlot(22 * 60, 7 * 60, "wellness")
            )
        )
        assertEquals(scheduler, ConfigCodec.decodeOr(ConfigCodec.encode(scheduler), SchedulerConfig()))
    }

    @Test
    fun `values that broke the old delimiter format now survive`() {
        // The previous format joined on "," and split records on ";", so either
        // character inside a value silently corrupted the config.
        val awkward = AppVisibilityConfig(primaryCategoryIds = listOf("a,b", "c;d", "e||f"))
        assertEquals(awkward, ConfigCodec.decodeOr(ConfigCodec.encode(awkward), AppVisibilityConfig()))
    }

    @Test
    fun `unreadable stored values fall back instead of throwing`() {
        val fallback = AppVisibilityConfig(primaryCategoryIds = listOf("study"))
        // A row written by the pre-JSON format, still present on dev installs.
        assertEquals(fallback, ConfigCodec.decodeOr("primary:study;secondary:;hidden:", fallback))
        assertEquals(fallback, ConfigCodec.decodeOr("{ not json", fallback))
        assertEquals(fallback, ConfigCodec.decodeOr("", fallback))
        assertEquals(fallback, ConfigCodec.decodeOr(null, fallback))
    }

    @Test
    fun `a field added by a newer build is ignored rather than fatal`() {
        val forwardCompatible = """{"primaryCategoryIds":["study"],"somethingWeAddLater":42}"""
        assertEquals(
            AppVisibilityConfig(primaryCategoryIds = listOf("study")),
            ConfigCodec.decodeOr(forwardCompatible, AppVisibilityConfig())
        )
    }
}
