package com.tanvoid0.portallauncher.data

import android.content.pm.ApplicationInfo

/**
 * Resolves the category an app belongs to, cheapest source first:
 *
 * 1. [ApplicationInfo.category] — declared by the app's own developer. Free, no
 *    model, no permission, and authoritative when it is set.
 * 2. [AppCategoryRules] — the keyword list. Catches the well-known packages that
 *    never bothered to declare a category.
 * 3. The AI cache — whatever an on-device classification run previously decided
 *    for this package. Optional; the map is simply empty when the user has not
 *    enabled it or the device cannot run it.
 * 4. [AppCategory.Other].
 *
 * The AI sits *last* on purpose: it is only ever asked about apps that steps 1 and
 * 2 could not place, so it fills a long tail rather than overriding facts. Every
 * step above it works with no network, no model and no permission, which is why
 * turning the AI off changes coverage and never changes whether the app works.
 */
object AppCategorizer {

    fun categoryFor(app: LaunchableApp, aiCategories: Map<String, AppCategory>): AppCategory =
        // A user override outranks every automatic source, including the app's own
        // declared category. It is the escape hatch for the categoriser being wrong,
        // so nothing may quietly win against it.
        app.categoryOverride ?: categoryFor(app.systemCategory, app.packageName, aiCategories)

    /**
     * The resolution itself, free of [LaunchableApp] so it stays a plain function of
     * its inputs — and so it can be tested without an Android runtime.
     */
    fun categoryFor(
        systemCategory: Int,
        packageName: String,
        aiCategories: Map<String, AppCategory>
    ): AppCategory {
        fromSystemCategory(systemCategory)?.let { return it }
        val byRule = AppCategoryRules.categoryFor(packageName)
        if (byRule != AppCategory.Other) return byRule
        return aiCategories[packageName] ?: AppCategory.Other
    }

    /** True when nothing but the AI could place this app. Drives what we ask about. */
    fun needsClassification(app: LaunchableApp): Boolean =
        categoryFor(app, emptyMap()) == AppCategory.Other

    /**
     * Only the system categories that map onto a profile without argument. NEWS,
     * AUDIO, VIDEO and IMAGE have no honest equivalent in [AppCategory], so they
     * fall through to the rules rather than being forced into a bucket.
     *
     * MAPS does have one now that [AppCategory.Driving] exists: a navigation app is
     * the one thing every driving profile needs, and the developer already told us.
     */
    private fun fromSystemCategory(category: Int): AppCategory? = when (category) {
        ApplicationInfo.CATEGORY_GAME -> AppCategory.Gaming
        ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.Social
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.Productivity
        ApplicationInfo.CATEGORY_MAPS -> AppCategory.Driving
        else -> null
    }
}
