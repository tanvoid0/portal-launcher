package com.tanvoid0.portallauncher.data

import java.text.Normalizer

/**
 * How well [query] matches [text]. Lower is better; null means no match at all.
 *
 * The tiers exist because a launcher search is judged on the *first* result after two
 * or three characters, not on recall. "ca" has to give Calendar and Camera before
 * Vlc — a plain `contains` puts them in package-name order and feels broken.
 *
 * Kept as a function of two strings so it is testable on the JVM and so it can be run
 * against both an app's own name and a user's rename without knowing about either.
 */
fun matchScore(query: String, text: String): Int? {
    val q = query.normalizeForSearch()
    val t = text.normalizeForSearch()
    if (q.isEmpty()) return MATCH_CONTAINS
    if (t == q) return MATCH_EXACT
    if (t.startsWith(q)) return MATCH_PREFIX
    if (t.words().any { it.startsWith(q) }) return MATCH_WORD_PREFIX
    if (t.initials().startsWith(q)) return MATCH_INITIALS
    if (t.contains(q)) return MATCH_CONTAINS
    if (t.containsSubsequence(q)) return MATCH_SUBSEQUENCE
    return null
}

const val MATCH_EXACT = 0
const val MATCH_PREFIX = 1
const val MATCH_WORD_PREFIX = 2

/** "gm" finds Google Maps. The reason two letters is usually enough to launch. */
const val MATCH_INITIALS = 3
const val MATCH_CONTAINS = 4

/** Last resort: every query letter appears in order. Catches typos and abbreviations. */
const val MATCH_SUBSEQUENCE = 5

/**
 * Apps matching [query], best first.
 *
 * Scores against the user's rename *and* the app's own name, taking whichever is
 * better: someone who renamed Gmail to "Work" should still find it by typing either.
 * Ties break on the shorter name, so "Clock" beats "Clock Widget Pro" for "clock".
 */
fun searchApps(query: String, apps: List<LaunchableApp>): List<LaunchableApp> {
    if (query.isBlank()) return apps
    return apps
        .mapNotNull { app ->
            val score = listOfNotNull(
                matchScore(query, app.displayLabel),
                app.customLabel?.let { matchScore(query, app.label) }
            ).minOrNull() ?: return@mapNotNull null
            Triple(app, score, app.displayLabel.length)
        }
        .sortedWith(
            compareBy({ it.second }, { it.third }, { it.first.displayLabel.lowercase() })
        )
        .map { it.first }
}

/**
 * Lowercased and stripped of accents, so "Pokémon" is reachable by typing "pokemon"
 * on a keyboard that has no é.
 */
private fun String.normalizeForSearch(): String =
    Normalizer.normalize(trim(), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase()

private val COMBINING_MARKS = Regex("\\p{Mn}+")
private val WORD_SEPARATORS = Regex("[\\s\\-_.:/&()\\[\\]]+")

private fun String.words(): List<String> = split(WORD_SEPARATORS).filter { it.isNotEmpty() }

private fun String.initials(): String = words().map { it.first() }.joinToString("")

/** True when every character of [query] appears in order, not necessarily adjacent. */
private fun String.containsSubsequence(query: String): Boolean {
    var index = 0
    for (char in this) {
        if (index < query.length && char == query[index]) index++
    }
    return index == query.length
}
