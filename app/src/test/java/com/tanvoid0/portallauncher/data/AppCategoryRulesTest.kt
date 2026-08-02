package com.tanvoid0.portallauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins how packages map to categories. The rules are an ordered substring list, so
 * the *order* is behaviour: a package matching two rules gets the earlier one. These
 * tests exist so phase 4 (ApplicationInfo.category first, keywords as fallback, user
 * override on top) is a deliberate change rather than an accidental one.
 */
class AppCategoryRulesTest {

    @Test
    fun `matches each category from an unambiguous package`() {
        assertEquals(AppCategory.Study, AppCategoryRules.categoryFor("com.duolingo"))
        assertEquals(AppCategory.Social, AppCategoryRules.categoryFor("com.instagram.android"))
        assertEquals(AppCategory.Productivity, AppCategoryRules.categoryFor("com.Slack"))
        assertEquals(
            AppCategory.Gaming,
            AppCategoryRules.categoryFor("com.valvesoftware.android.steam.community")
        )
    }

    @Test
    fun `unknown package falls back to Other`() {
        assertEquals(AppCategory.Other, AppCategoryRules.categoryFor("com.example.something"))
    }

    @Test
    fun `matching is case insensitive`() {
        assertEquals(AppCategory.Social, AppCategoryRules.categoryFor("COM.WhatsApp"))
    }

    @Test
    fun `substring rules mis-file apps that phase 4 must fix`() {
        // Both of these are Productivity apps to any user. They land in Study only
        // because "calendar" and "docs" appear in the Study rules first. Change these
        // assertions when the categoriser starts with ApplicationInfo.category.
        assertEquals(AppCategory.Study, AppCategoryRules.categoryFor("com.google.android.calendar"))
        assertEquals(
            AppCategory.Study,
            AppCategoryRules.categoryFor("com.google.android.apps.docs")
        )

        // And this is the failure mode of substring matching itself: an unrelated app
        // whose package happens to contain a keyword.
        assertEquals(AppCategory.Gaming, AppCategoryRules.categoryFor("com.acme.gamesetup"))
    }
}
