package com.tanvoid0.portallauncher.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tanvoid0.portallauncher.PortalLauncherApplication
import com.tanvoid0.portallauncher.data.ProfileEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The only state first-run setup owns that outlives a step: which profile the user
 * picked, and whether setup is over.
 *
 * Home-app and notification access are deliberately *not* here. Both are system
 * state granted in another app's UI, so the truth is whatever the platform says
 * when we resume — caching it in a ViewModel only creates a copy that can go stale.
 */
class SetupViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PortalLauncherApplication
    private val preferences = app.preferencesRepository

    /** Empty for the first frames of a fresh install — the seed runs in Application. */
    val profiles: StateFlow<List<ProfileEntity>> = app.profileRepository.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeProfileId: StateFlow<String?> = preferences.activeProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectProfile(profileId: String) {
        viewModelScope.launch { preferences.setActiveProfileId(profileId) }
    }

    /**
     * Setup is over — finished or skipped, the same thing as far as showing it
     * again goes. Persisted, so the next cold start opens on the home screen.
     */
    fun finish() {
        viewModelScope.launch { preferences.setSetupComplete(true) }
    }
}
