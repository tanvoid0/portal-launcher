package com.tanvoid0.portallauncher.data

/**
 * App categories for visibility and blocking. Must match profile visibility config keys.
 *
 * This enum is the single axis the whole product turns on: [AppCategorizer] resolves
 * every installed app to one of these, and a profile is little more than the subset
 * it puts on the home screen. Adding an entry here therefore extends three things at
 * once — the rules, the profiles that can reference it, and the on-device model's
 * prompt, which enumerates these entries rather than hard-coding a list.
 *
 * [Wellness] and [Driving] have no `ApplicationInfo.category` equivalent and only a
 * short keyword list, so they lean on the model harder than the rest. That is the
 * intended split: the free sources place what they can, the model fills the tail.
 */
enum class AppCategory(val id: String) {
    Study("study"),
    Social("social"),
    Productivity("productivity"),
    Gaming("gaming"),
    Wellness("wellness"),
    Driving("driving"),
    Other("other");

    companion object {
        fun fromId(id: String): AppCategory = entries.find { it.id == id } ?: Other
    }
}
