package com.tanvoid0.portallauncher.data

/**
 * Maps package names to categories using predefined rules (package prefix / name contains).
 * User overrides can be added later via Room or DataStore.
 */
object AppCategoryRules {

    private val rules: List<Pair<(String) -> Boolean, AppCategory>> = listOf(
        // Wellness and Driving come first because the lists below lead with broad
        // keywords — "drive" is a Study rule, "game" a Gaming one — and a substring
        // list gives the win to whichever rule appears earlier. These keywords are
        // narrow enough that being first costs nothing.
        // Wellness
        { pkg: String -> pkg.contains("health") || pkg.contains("fitness") || pkg.contains("fitbit") } to AppCategory.Wellness,
        { pkg: String -> pkg.contains("calm") || pkg.contains("headspace") || pkg.contains("meditat") } to AppCategory.Wellness,
        { pkg: String -> pkg.contains("sleep") || pkg.contains("yoga") || pkg.contains("workout") } to AppCategory.Wellness,
        { pkg: String -> pkg.contains("strava") || pkg.contains("runkeeper") || pkg.contains("garmin") } to AppCategory.Wellness,
        // Driving
        { pkg: String -> pkg.contains("maps") || pkg.contains("waze") || pkg.contains("navigation") } to AppCategory.Driving,
        // Android Auto ships as "projection.gearhead"; neither half says "auto",
        // and matching "auto" itself would swallow Autodesk and Automattic.
        { pkg: String -> pkg.contains("gearhead") || pkg.contains("androidauto") } to AppCategory.Driving,
        { pkg: String -> pkg.contains("ubercab") || pkg.contains("lyft") || pkg.contains("parking") } to AppCategory.Driving,
        // Study
        { pkg: String -> pkg.contains("adobe") || pkg.contains("reader") } to AppCategory.Study,
        { pkg: String -> pkg.contains("drive") || pkg.contains("docs") } to AppCategory.Study,
        { pkg: String -> pkg.contains("duolingo") } to AppCategory.Study,
        { pkg: String -> pkg.contains("notes") || pkg.contains("notepad") || pkg.contains("evernote") } to AppCategory.Study,
        { pkg: String -> pkg.contains("calendar") } to AppCategory.Study,
        { pkg: String -> pkg.contains("pdf") || pkg.contains("xodo") } to AppCategory.Study,
        // Social
        { pkg: String -> pkg.contains("facebook") || pkg.contains("messenger") || pkg.contains("instagram") } to AppCategory.Social,
        { pkg: String -> pkg.contains("whatsapp") || pkg.contains("telegram") || pkg.contains("discord") } to AppCategory.Social,
        { pkg: String -> pkg.contains("twitter") || pkg.contains("x.com") || pkg.contains("tiktok") } to AppCategory.Social,
        { pkg: String -> pkg.contains("linkedin") || pkg.contains("reddit") } to AppCategory.Social,
        // Productivity
        { pkg: String -> pkg.contains("gmail") || pkg.contains("outlook") || pkg.contains("mail") } to AppCategory.Productivity,
        { pkg: String -> pkg.contains("slack") || pkg.contains("teams") || pkg.contains("zoom") } to AppCategory.Productivity,
        { pkg: String -> pkg.contains("office") || pkg.contains("word") || pkg.contains("excel") || pkg.contains("sheets") } to AppCategory.Productivity,
        { pkg: String -> pkg.contains("todo") || pkg.contains("tasks") || pkg.contains("trello") } to AppCategory.Productivity,
        // Gaming
        { pkg: String -> pkg.contains("game") || pkg.contains("play.games") } to AppCategory.Gaming,
        { pkg: String -> pkg.contains("epic") || pkg.contains("steam") || pkg.contains("xbox") } to AppCategory.Gaming,
    )

    fun categoryFor(packageName: String): AppCategory {
        val lower = packageName.lowercase()
        return rules.find { it.first(lower) }?.second ?: AppCategory.Other
    }
}
