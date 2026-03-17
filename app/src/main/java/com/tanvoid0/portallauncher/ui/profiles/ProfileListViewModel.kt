package com.tanvoid0.portallauncher.ui.profiles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tanvoid0.portallauncher.PortalLauncherApplication
import com.tanvoid0.portallauncher.data.ProfileEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileListViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PortalLauncherApplication
    private val profileRepository = app.profileRepository
    private val preferencesRepository = app.preferencesRepository

    val profiles: StateFlow<List<ProfileEntity>> = profileRepository.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val defaultProfileId: StateFlow<String?> = preferencesRepository.defaultProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setDefaultProfile(profileId: String) {
        viewModelScope.launch {
            preferencesRepository.setDefaultProfileId(profileId)
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            profileRepository.deleteProfile(id)
        }
    }
}
