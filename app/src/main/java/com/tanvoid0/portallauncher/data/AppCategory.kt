package com.tanvoid0.portallauncher.data

/**
 * App categories for visibility and blocking. Must match profile visibility config keys.
 */
enum class AppCategory(val id: String) {
    Study("study"),
    Social("social"),
    Productivity("productivity"),
    Gaming("gaming"),
    Other("other");

    companion object {
        fun fromId(id: String): AppCategory = entries.find { it.id == id } ?: Other
    }
}
