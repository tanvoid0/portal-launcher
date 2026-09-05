package com.tanvoid0.portallauncher.data

import android.content.pm.ApplicationInfo

/**
 * Resolves the category an app belongs to, cheapest source first:
 *
 * 1. [AppCategoryRules] — our own keyword list. Narrow, curated, and the only
 *    source here anybody reviews.
 * 2. [ApplicationInfo.category] — a weak signal, despite reading like a strong one.
 * 3. The AI cache — whatever an on-device classification run previously decided
 *    for this package. Optional; the map is simply empty when the user has not
 *    enabled it or the device cannot run it.
 * 4. [AppCategory.Other].
 *
 * **The rules go first because the system category is not what it claims to be.**
 * It was documented here as "declared by the app's own developer", and on a real
 * phone it mostly is not: `dumpsys package` on the test device shows Chrome, Adblock
 * Browser and a VPN all carrying `override: 4` — CATEGORY_SOCIAL, written by the
 * *installer*, not the manifest, out of Play's own much coarser store taxonomy. Play
 * Games declares SOCIAL in its manifest outright. Trusting it first put a browser, a
 * VPN and a games launcher in the Social tab, which is worse than not placing them:
 * a wrong-but-specific answer sends someone to the wrong tab, where [AppCategory.Other]
 * at least admits it does not know.
 *
 * The AI still sits *last*: it is only ever asked about apps nothing above it could
 * place, so it fills a long tail rather than overriding anything. Every step above it
 * works with no network, no model and no permission, which is why turning the AI off
 * changes coverage and never changes whether the app works.
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
        val byRule = AppCategoryRules.categoryFor(packageName)
        if (byRule != AppCategory.Other) return byRule
        fromSystemCategory(systemCategory)?.let { return it }
        return aiCategories[packageName] ?: AppCategory.Other
    }

    /** True when nothing but the AI could place this app. Drives what we ask about. */
    fun needsClassification(app: LaunchableApp): Boolean =
        categoryFor(app, emptyMap()) == AppCategory.Other

    /**
     * Only the system categories that map onto a profile without argument. NEWS,
     * AUDIO, VIDEO and IMAGE have no honest equivalent in [AppCategory], so they
     * fall through rather than being forced into a bucket.
     *
     * MAPS does have one now that [AppCategory.Driving] exists: a navigation app is
     * the one thing every driving profile needs.
     *
     * **SOCIAL is deliberately absent.** It is the value Play's installer override
     * reaches for whenever it is unsure — on the test device it is what a browser, an
     * ad blocker, a VPN and a supermarket app all carry — so honouring it fills the
     * Social tab with things nobody would look for there. Every app that really is
     * social (the messengers, the feeds, the networks) is already in
     * [AppCategoryRules], which runs first anyway; what this loses is the long tail of
     * genuinely social apps nobody has added a keyword for, and those land in
     * [AppCategory.Other] or, if the user turns it on, with the model. Being visibly
     * unplaced beats being confidently misplaced.
     */
    private fun fromSystemCategory(category: Int): AppCategory? = when (category) {
        ApplicationInfo.CATEGORY_GAME -> AppCategory.Gaming
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.Productivity
        ApplicationInfo.CATEGORY_MAPS -> AppCategory.Driving
        else -> null
    }
}
