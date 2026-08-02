package com.tanvoid0.portallauncher.ui.nav

object Routes {
    const val LAUNCHER_HOME = "launcher_home"
    const val PROFILE_LIST = "profile_list"
    const val PROFILE_EDIT = "profile_edit"
    const val ONBOARDING = "onboarding"
    const val SETTINGS = "settings"
    const val HIDDEN_APPS = "hidden_apps"
    const val SCHEDULE = "schedule"

    fun profileEdit(id: String?) = "profile_edit/${id ?: "new"}"
}
