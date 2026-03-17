package com.tanvoid0.portallauncher.ui.profiles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tanvoid0.portallauncher.PortalLauncherApplication
import com.tanvoid0.portallauncher.data.AppVisibilityConfig
import com.tanvoid0.portallauncher.data.AutomationConfigEntity
import com.tanvoid0.portallauncher.data.AutomationIds
import com.tanvoid0.portallauncher.data.ConfigJson
import com.tanvoid0.portallauncher.data.ProfileEntity
import com.tanvoid0.portallauncher.data.ProfileType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileEditState(
    val id: String = "",
    val name: String = "",
    val type: ProfileType = ProfileType.Custom,
    val iconResName: String = "default",
    val primaryCategories: List<String> = emptyList(),
    val loading: Boolean = true
)

class ProfileEditViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PortalLauncherApplication
    private val profileRepository = app.profileRepository

    private val _state = MutableStateFlow(ProfileEditState())
    val state: StateFlow<ProfileEditState> = _state.asStateFlow()

    fun loadProfile(profileId: String?) {
        viewModelScope.launch {
            if (profileId == null || profileId == "new") {
                _state.update {
                    it.copy(
                        id = "new",
                        name = "",
                        type = ProfileType.Custom,
                        loading = false
                    )
                }
                return@launch
            }
            val profile = profileRepository.getProfileById(profileId).first()
            if (profile != null) {
                val visibility = profileRepository.getVisibilityConfigForProfile(profile.id).first() ?: AppVisibilityConfig()
                _state.update {
                    it.copy(
                        id = profile.id,
                        name = profile.name,
                        type = ProfileType.entries.find { e -> e.name == profile.type } ?: ProfileType.Custom,
                        iconResName = profile.iconResName,
                        primaryCategories = visibility.primaryCategoryIds,
                        loading = false
                    )
                }
            }
        }
    }

    fun updateName(name: String) {
        _state.update { it.copy(name = name) }
    }

    fun updateType(type: ProfileType) {
        _state.update { it.copy(type = type) }
    }

    fun saveProfile(onSaved: () -> Unit) {
        viewModelScope.launch {
            val s = _state.value
            val id = if (s.id == "new") "profile_${System.currentTimeMillis()}" else s.id
            val profile = ProfileEntity(
                id = id,
                name = s.name.ifBlank { s.type.name },
                iconResName = s.iconResName,
                type = s.type.name,
                enabledAutomationIds = listOf(AutomationIds.APP_VISIBILITY),
                sortOrder = 0
            )
            profileRepository.insertProfile(profile)
            val visibilityConfig = AppVisibilityConfig(primaryCategoryIds = s.primaryCategories)
            profileRepository.saveAutomationConfig(
                AutomationConfigEntity(
                    profileId = id,
                    automationId = AutomationIds.APP_VISIBILITY,
                    configJson = ConfigJson.appVisibilityToStr(visibilityConfig)
                )
            )
            onSaved()
        }
    }
}
