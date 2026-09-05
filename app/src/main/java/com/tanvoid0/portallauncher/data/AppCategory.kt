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
 * [Wellness], [Driving], [Shopping] and [Utilities] have no `ApplicationInfo.category`
 * equivalent and only a short keyword list, so they lean on the model harder than the
 * rest. That is the intended split: the free sources place what they can, the model
 * fills the tail.
 *
 * Order is the order the drawer's tabs and section headers appear in, so it reads as a
 * deliberate sequence rather than however the enum happened to grow. [Other] stays
 * last: it is the "we could not place these" bucket, not a category anyone looks for.
 */
enum class AppCategory(val id: String) {
    Study("study"),
    Social("social"),
    Productivity("productivity"),
    Gaming("gaming"),
    // Added after a pass on a real phone: with only the seven above, a browser, a VPN,
    // an authenticator, a supermarket and a music app all landed in the same "Everything
    // else" bucket, which on a loaded device is most of the drawer. These four are the
    // ones the reference launcher's category bar has that Portal did not.
    Internet("internet"),
    Media("media"),
    Shopping("shopping"),
    Utilities("utilities"),
    Wellness("wellness"),
    Driving("driving"),
    Other("other");

    companion object {
        fun fromId(id: String): AppCategory = entries.find { it.id == id } ?: Other
    }
}
