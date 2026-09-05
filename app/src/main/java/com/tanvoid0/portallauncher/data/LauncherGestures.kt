package com.tanvoid0.portallauncher.data

/**
 * The gestures the home screen's background responds to, and what they can be bound to.
 *
 * The reference launcher offers four directions plus a double tap. Portal offers three:
 * **left and right are missing on purpose.** The home canvas is a pager, and a
 * horizontal drag there is a page turn — a background gesture on the same axis either
 * never fires, because the pager consumed it, or steals page turns from the user. A
 * bindable gesture that fights the layout is worse than one that is not offered.
 *
 * The action list is deliberately everything Portal can do with no extra permission.
 * Notably absent is "open the notification shade", which every launcher's gesture list
 * has: since Android 12 an app can only pull the shade down through an
 * AccessibilityService, and asking for accessibility access — the most alarming grant
 * on the platform, and the one most abused — to save one swipe is not a trade worth
 * offering. It goes in when there is a reason to hold that permission anyway.
 */
enum class LauncherGesture(val id: String) {
    SwipeUp("swipe_up"),
    SwipeDown("swipe_down"),
    DoubleTap("double_tap");

    companion object {
        fun fromId(id: String): LauncherGesture? = entries.find { it.id == id }
    }
}

enum class GestureAction(val id: String) {
    None("none"),
    AppDrawer("app_drawer"),
    Search("search"),
    NextProfile("next_profile"),
    PreviousProfile("previous_profile"),
    DefaultProfile("default_profile"),
    Profiles("profiles"),
    Settings("settings");

    companion object {
        fun fromId(id: String): GestureAction = entries.find { it.id == id } ?: None
    }
}

/**
 * What each gesture does before the user changes anything.
 *
 * Swipe up stays the drawer because that is what it already did and what every launcher
 * does; making it configurable must not move it. Swipe down opens search rather than
 * being empty — it is the one direction with nothing else competing for it, and typing
 * a name is the fastest way to reach an app. Double tap starts unbound: it is the
 * gesture people trigger by accident.
 */
val DEFAULT_GESTURE_ACTIONS: Map<LauncherGesture, GestureAction> = mapOf(
    LauncherGesture.SwipeUp to GestureAction.AppDrawer,
    LauncherGesture.SwipeDown to GestureAction.Search,
    LauncherGesture.DoubleTap to GestureAction.None
)

/**
 * The profile a next/previous gesture lands on.
 *
 * Wraps at both ends, so repeating the gesture cycles rather than stopping dead at the
 * last profile with no feedback. Returns null when there is nothing to move to — no
 * profiles at all, or only the one that is already active.
 */
fun neighbourProfileId(profileIds: List<String>, activeId: String?, forward: Boolean): String? {
    if (profileIds.size < 2) return null
    val index = profileIds.indexOf(activeId)
    // An active profile that is not in the list (deleted, or not loaded yet) has no
    // neighbour to speak of; start from whichever end the gesture points at.
    if (index < 0) return if (forward) profileIds.first() else profileIds.last()
    val step = if (forward) 1 else -1
    return profileIds[(index + step + profileIds.size) % profileIds.size]
}
