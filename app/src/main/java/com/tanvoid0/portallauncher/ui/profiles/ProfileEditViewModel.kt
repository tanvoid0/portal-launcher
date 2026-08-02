package com.tanvoid0.portallauncher.ui.profiles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tanvoid0.portallauncher.PortalLauncherApplication
import com.tanvoid0.portallauncher.automation.AutomationAvailability
import com.tanvoid0.portallauncher.automation.AutomationRegistry
import com.tanvoid0.portallauncher.data.AppVisibilityConfig
import com.tanvoid0.portallauncher.data.AutomationConfigEntity
import com.tanvoid0.portallauncher.data.AutomationIds
import com.tanvoid0.portallauncher.data.ConfigCodec
import com.tanvoid0.portallauncher.data.GreyscaleConfig
import com.tanvoid0.portallauncher.data.LaunchableApp
import com.tanvoid0.portallauncher.data.NotificationFilterConfig
import com.tanvoid0.portallauncher.data.ProfileEntity
import com.tanvoid0.portallauncher.data.ProfileType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileEditState(
    val id: String = "",
    val name: String = "",
    val type: ProfileType = ProfileType.Custom,
    val iconResName: String = "default",
    /** Categories whose apps this profile puts on the home screen. Empty = all apps. */
    val primaryCategories: Set<String> = emptySet(),
    /** Fields of the visibility config this editor does not touch, carried through. */
    val visibilityRest: AppVisibilityConfig = AppVisibilityConfig(),
    val enabledAutomationIds: Set<String> = emptySet(),
    val greyscale: GreyscaleConfig = GreyscaleConfig(),
    val notification: NotificationFilterConfig = NotificationFilterConfig(),
    /** Automation id → whether it can run, refreshed when the screen resumes. */
    val availability: Map<String, AutomationAvailability> = emptyMap(),
    val loading: Boolean = true
)

class ProfileEditViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PortalLauncherApplication
    private val profileRepository = app.profileRepository

    private val _state = MutableStateFlow(ProfileEditState())
    val state: StateFlow<ProfileEditState> = _state.asStateFlow()

    /**
     * One entry per package for the notification-filter picker: the filter matches by
     * package, so listing every activity and work-profile copy would offer the same
     * choice twice.
     */
    val packages: StateFlow<List<LaunchableApp>> = app.appRepository.apps
        .map { apps -> apps.distinctBy { it.packageName }.sortedBy { it.label.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun loadProfile(profileId: String?) {
        viewModelScope.launch {
            if (profileId == null || profileId == "new") {
                _state.update {
                    ProfileEditState(
                        id = "new",
                        enabledAutomationIds = setOf(AutomationIds.APP_VISIBILITY),
                        availability = currentAvailability(),
                        loading = false
                    )
                }
                return@launch
            }
            val profile = profileRepository.getProfileById(profileId).first() ?: return@launch
            val configs = profileRepository.getAutomationConfigsForProfile(profile.id).first()
                .associate { it.automationId to it.configJson }
            val visibility = ConfigCodec.decodeOr(
                configs[AutomationIds.APP_VISIBILITY],
                AppVisibilityConfig()
            )
            _state.update {
                ProfileEditState(
                    id = profile.id,
                    name = profile.name,
                    type = ProfileType.entries.find { e -> e.name == profile.type }
                        ?: ProfileType.Custom,
                    iconResName = profile.iconResName,
                    primaryCategories = visibility.primaryCategoryIds.toSet(),
                    visibilityRest = visibility,
                    enabledAutomationIds = profile.enabledAutomationIds.toSet(),
                    greyscale = ConfigCodec.decodeOr(
                        configs[AutomationIds.GREYSCALE],
                        GreyscaleConfig()
                    ),
                    notification = ConfigCodec.decodeOr(
                        configs[AutomationIds.NOTIFICATION_FILTER],
                        NotificationFilterConfig()
                    ),
                    availability = currentAvailability(),
                    loading = false
                )
            }
        }
    }

    /**
     * Re-asks every automation whether it can run. Called when the screen resumes,
     * because the answer changes in another app's UI — the user comes back from the
     * notification-access screen and the "grant" card must become a switch.
     */
    fun refreshAvailability() {
        _state.update { it.copy(availability = currentAvailability()) }
    }

    private fun currentAvailability(): Map<String, AutomationAvailability> =
        AutomationRegistry.all.associate { it.id to it.availability(app) }

    fun updateName(name: String) = _state.update { it.copy(name = name) }

    fun updateType(type: ProfileType) = _state.update { it.copy(type = type) }

    fun toggleCategory(categoryId: String) = _state.update {
        it.copy(
            primaryCategories = if (categoryId in it.primaryCategories) {
                it.primaryCategories - categoryId
            } else {
                it.primaryCategories + categoryId
            }
        )
    }

    fun setAutomationEnabled(automationId: String, enabled: Boolean) = _state.update {
        it.copy(
            enabledAutomationIds = if (enabled) {
                it.enabledAutomationIds + automationId
            } else {
                it.enabledAutomationIds - automationId
            }
        )
    }

    fun setGreyscaleIntensity(intensity: Float) = _state.update {
        it.copy(greyscale = it.greyscale.copy(intensity = intensity.coerceIn(0f, 1f)))
    }

    fun setNotificationCancel(cancel: Boolean) = _state.update {
        it.copy(notification = it.notification.copy(cancelOnFilter = cancel))
    }

    fun toggleBlockedPackage(packageName: String) = _state.update {
        val blocked = it.notification.blockedPackageNames
        it.copy(
            notification = it.notification.copy(
                blockedPackageNames = if (packageName in blocked) {
                    blocked - packageName
                } else {
                    blocked + packageName
                }
            )
        )
    }

    fun saveProfile(onSaved: () -> Unit) {
        viewModelScope.launch {
            val s = _state.value
            val id = if (s.id == "new") "profile_${System.currentTimeMillis()}" else s.id
            profileRepository.insertProfile(
                ProfileEntity(
                    id = id,
                    name = s.name.ifBlank { s.type.name },
                    iconResName = s.iconResName,
                    type = s.type.name,
                    // Visibility is how the home screen works, so it is always on. The
                    // rest is whatever the user switched — including ids this editor
                    // does not know about, which loading kept in the set untouched.
                    enabledAutomationIds =
                        (s.enabledAutomationIds + AutomationIds.APP_VISIBILITY).toList(),
                    sortOrder = 0
                )
            )
            // Configs are written even for automations currently off, so switching one
            // back on finds its settings where the user left them.
            val configs = mapOf(
                AutomationIds.APP_VISIBILITY to ConfigCodec.encode(
                    s.visibilityRest.copy(primaryCategoryIds = s.primaryCategories.toList())
                ),
                AutomationIds.GREYSCALE to ConfigCodec.encode(
                    s.greyscale.copy(enabled = AutomationIds.GREYSCALE in s.enabledAutomationIds)
                ),
                AutomationIds.NOTIFICATION_FILTER to ConfigCodec.encode(s.notification)
            )
            configs.forEach { (automationId, json) ->
                profileRepository.saveAutomationConfig(
                    AutomationConfigEntity(id, automationId, json)
                )
            }
            onSaved()
        }
    }
}
