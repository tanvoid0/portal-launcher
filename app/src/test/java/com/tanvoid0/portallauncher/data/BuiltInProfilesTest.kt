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
    fun `every other profile filters to something and never to Other`() {
        BuiltInProfiles.all.drop(1).forEach { profile ->
            assertTrue("${profile.id} shows every app", profile.primaryCategories.isNotEmpty())
            assertTrue(
                "${profile.id} claims the Other bucket",
                AppCategory.Other !in profile.primaryCategories
            )
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
