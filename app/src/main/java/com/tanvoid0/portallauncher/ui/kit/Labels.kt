package com.tanvoid0.portallauncher.ui.kit

import androidx.annotation.StringRes
import com.tanvoid0.portallauncher.R
import com.tanvoid0.portallauncher.data.AppCategory
import com.tanvoid0.portallauncher.data.ProfileType

/**
 * Display names for the storage enums. Kept out of `data`: the ids those enums carry
 * are storage keys and must never change, while what the user reads localizes.
 */

@get:StringRes
val AppCategory.labelRes: Int
    get() = when (this) {
        AppCategory.Study -> R.string.category_study
        AppCategory.Social -> R.string.category_social
        AppCategory.Productivity -> R.string.category_productivity
        AppCategory.Gaming -> R.string.category_gaming
        AppCategory.Wellness -> R.string.category_wellness
        AppCategory.Driving -> R.string.category_driving
        AppCategory.Other -> R.string.category_other
    }

@get:StringRes
val ProfileType.labelRes: Int
    get() = when (this) {
        ProfileType.Study -> R.string.type_study
        ProfileType.Social -> R.string.type_social
        ProfileType.Productivity -> R.string.type_productivity
        ProfileType.Gaming -> R.string.type_gaming
        ProfileType.Focus -> R.string.type_focus
        ProfileType.Wellness -> R.string.type_wellness
        ProfileType.Driving -> R.string.type_driving
        ProfileType.Custom -> R.string.type_custom
    }

/**
 * Label for a profile's stored `type` string. The column holds an enum name, but a
 * hand-edited backup can hold anything — show that as-is rather than crashing on it.
 */
fun profileTypeLabelRes(stored: String): Int? =
    ProfileType.entries.find { it.name == stored }?.labelRes
