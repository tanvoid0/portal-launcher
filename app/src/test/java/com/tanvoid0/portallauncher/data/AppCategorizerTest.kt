package com.tanvoid0.portallauncher.data

import android.content.pm.ApplicationInfo
import com.tanvoid0.portallauncher.ai.parseClassification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the two things that decide whether the AI stays optional:
 * the resolution order (AI last, never overriding a fact) and the response parser
 * (a bad answer must degrade to no answer, never to a wrong category).
 */
class AppCategorizerTest {

    private val undefined = ApplicationInfo.CATEGORY_UNDEFINED

    @Test
    fun `system category beats the keyword rules`() {
        // The rules would call this Study — "docs" is in the Study list.
        assertEquals(
            AppCategory.Productivity,
            AppCategorizer.categoryFor(
                systemCategory = ApplicationInfo.CATEGORY_PRODUCTIVITY,
                packageName = "com.google.android.apps.docs",
                aiCategories = emptyMap()
            )
        )
    }

    @Test
    fun `keyword rules apply when the system declares nothing`() {
        assertEquals(
            AppCategory.Social,
            AppCategorizer.categoryFor(undefined, "com.instagram.android", emptyMap())
        )
    }

    @Test
    fun `ai only fills what nothing else could place`() {
        val ai = mapOf(
            "com.example.unknown" to AppCategory.Study,
            // The rules already place this one; the AI must not get a say.
            "com.duolingo" to AppCategory.Gaming
        )
        assertEquals(AppCategory.Study, AppCategorizer.categoryFor(undefined, "com.example.unknown", ai))
        assertEquals(AppCategory.Study, AppCategorizer.categoryFor(undefined, "com.duolingo", ai))
    }

    @Test
    fun `without ai every app still resolves`() {
        assertEquals(AppCategory.Other, AppCategorizer.categoryFor(undefined, "com.example.unknown", emptyMap()))
        assertEquals(AppCategory.Gaming, AppCategorizer.categoryFor(ApplicationInfo.CATEGORY_GAME, "com.example.x", emptyMap()))
    }

    @Test
    fun `parses a well formed response`() {
        val parsed = parseClassification(
            """
            1. study
            2. social
            3. other
            """.trimIndent(),
            listOf("a.pkg", "b.pkg", "c.pkg")
        )
        assertEquals(
            mapOf(
                "a.pkg" to AppCategory.Study,
                "b.pkg" to AppCategory.Social,
                "c.pkg" to AppCategory.Other
            ),
            parsed
        )
    }

    @Test
    fun `drops preamble, invented categories and out of range indices`() {
        val parsed = parseClassification(
            """
            Sure, here are the categories:
            1) productivity
            2 - entertainment
            9. social
            """.trimIndent(),
            listOf("a.pkg", "b.pkg")
        )
        assertEquals(mapOf("a.pkg" to AppCategory.Productivity), parsed)
    }

    @Test
    fun `an empty or nonsense response yields nothing rather than a wrong answer`() {
        assertTrue(parseClassification("", listOf("a.pkg")).isEmpty())
        assertTrue(parseClassification("I cannot help with that.", listOf("a.pkg")).isEmpty())
    }
}
