package com.tanvoid0.portallauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The built-ins are seeded once and then belong to the user, so a mistake here is
 * permanent on every install that has already run: a typo'd category id silently
 * empties a profile's home screen and no later release can fix it without touching
 * the user's own rows.
 */
class BuiltInProfilesTest {

    @Test
    fun `ids are unique`() {
        val ids = BuiltInProfiles.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `the default profile is first and filters nothing`() {
        val default = BuiltInProfiles.all.first()
        assertEquals(BuiltInProfiles.DEFAULT_ID, default.id)
        assertTrue(default.primaryCategories.isEmpty())
    }

    @Test
    fun `no profile ever filters to the Other bucket`() {
        // Unlike "shows every app", claiming Other specifically would be a typo either
        // way — it is the resolver's catch-all, never a category worth choosing.
        BuiltInProfiles.all.forEach { profile ->
            assertTrue("${profile.id} claims the Other bucket", AppCategory.Other !in profile.primaryCategories)
        }
    }

    @Test
    fun `the category-driven profiles filter to something`() {
        // Streaming, Outdoors, Performance and the two Power Saver profiles are not
        // category filters at all — see BuiltInProfiles' class doc — so this only
        // covers the profiles the product spec describes as narrowing the home screen.
        val categoryDriven = setOf(
            "study", "social", "productivity", "gaming", "focus", "wellness", "driving", "distraction_free"
        )
        BuiltInProfiles.all.filter { it.id in categoryDriven }.forEach { profile ->
            assertTrue("${profile.id} shows every app", profile.primaryCategories.isNotEmpty())
        }
    }

    @Test
    fun `every profile type has a built-in, Custom being the default one`() {
        val seeded = BuiltInProfiles.all.mapTo(mutableSetOf()) { it.type }
        assertEquals(ProfileType.entries.toSet(), seeded)
    }

    @Test
    fun `visibility config survives the round trip through storage`() {
        BuiltInProfiles.all.forEach { profile ->
            val config = BuiltInProfiles.visibilityConfig(profile)
            assertEquals(
                profile.primaryCategories.map { it.id },
                config.primaryCategoryIds
            )
            assertEquals(config, ConfigCodec.decodeOr(ConfigCodec.encode(config), AppVisibilityConfig()))
        }
    }

    @Test
    fun `every seeded automation config decodes and every id is registered`() {
        // Same failure mode as a typo'd category id: a bad automationId or configJson
        // here is a silent no-op that only shows up on a real device.
        val knownIds = setOf(
            AutomationIds.GREYSCALE, AutomationIds.NOTIFICATION_FILTER, AutomationIds.APP_BLOCKER,
            AutomationIds.DISPLAY_COMFORT, AutomationIds.DND, AutomationIds.POWER_SAVER
        )
        BuiltInProfiles.all.forEach { profile ->
            profile.automations.forEach { seed ->
                assertTrue("${profile.id}'s ${seed.automationId} is not a known automation", seed.automationId in knownIds)
                assertTrue("${profile.id}'s ${seed.automationId} config is blank", seed.configJson.isNotBlank())
            }
        }
    }

    @Test
    fun `ORIGINAL_IDS names exactly the profiles this object shipped with before the device-tuning ones`() {
        // Guards the seeding migration in ProfileRepository: if this set drifts from
        // what "all" used to contain, an upgrading install could either resurrect a
        // profile the user deleted or never receive one of the new templates.
        val newIds = setOf("streaming", "outdoors", "performance", "power_saver", "ultra_saver", "distraction_free")
        assertEquals(BuiltInProfiles.all.map { it.id }.toSet(), BuiltInProfiles.ORIGINAL_IDS + newIds)
    }

    @Test
    fun `Wellness and Driving depend on the AI, which is what the model is for`() {
        // Nothing here asserts the model runs — it asserts the gap it fills. Neither
        // category has an ApplicationInfo.category behind it beyond MAPS, so these
        // two profiles are the ones that visibly gain apps when the user opts in.
        val leanOnAi = BuiltInProfiles.all
            .filter { AppCategory.Wellness in it.primaryCategories || AppCategory.Driving in it.primaryCategories }
            .map { it.id }
        assertEquals(listOf("wellness", "driving"), leanOnAi)
    }
}
