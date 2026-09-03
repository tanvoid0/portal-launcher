package com.tanvoid0.portallauncher.ui.profiles

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tanvoid0.portallauncher.PortalLauncherApplication
import com.tanvoid0.portallauncher.automation.AutomationAvailability
import com.tanvoid0.portallauncher.automation.AutomationRegistry
import com.tanvoid0.portallauncher.data.AppBlockerConfig
import com.tanvoid0.portallauncher.data.AppVisibilityConfig
import com.tanvoid0.portallauncher.data.AutomationConfigEntity
import com.tanvoid0.portallauncher.data.AutomationIds
import com.tanvoid0.portallauncher.data.BrightnessMode
import com.tanvoid0.portallauncher.data.ConfigCodec
import com.tanvoid0.portallauncher.data.DisplayComfortConfig
import com.tanvoid0.portallauncher.data.DndConfig
import com.tanvoid0.portallauncher.data.DndFilterLevel
import com.tanvoid0.portallauncher.data.GreyscaleConfig
import com.tanvoid0.portallauncher.data.LaunchableApp
import com.tanvoid0.portallauncher.data.NotificationFilterConfig
import com.tanvoid0.portallauncher.data.PowerSaverConfig
import com.tanvoid0.portallauncher.data.PowerSaverIntensity
import com.tanvoid0.portallauncher.data.ProfileEntity
import com.tanvoid0.portallauncher.data.ProfileType
import com.tanvoid0.portallauncher.data.RefreshRateMode
import com.tanvoid0.portallauncher.ui.kit.labelRes
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileEditState(
    val id: String = "",
    val name: String = "",
    val type: ProfileType = ProfileType.Custom,
    val iconResName: String = "default",
    /**
     * Carried through unedited from load: this screen has no reordering UI of its own,
     * and autosaving a hardcoded 0 here would collapse a profile to the front of the
     * list the moment any field on it was touched.
     */
    val sortOrder: Int = 0,
    /** Categories whose apps this profile puts on the home screen. Empty = all apps. */
    val primaryCategories: Set<String> = emptySet(),
    /** Fields of the visibility config this editor does not touch, carried through. */
    val visibilityRest: AppVisibilityConfig = AppVisibilityConfig(),
    val enabledAutomationIds: Set<String> = emptySet(),
    val greyscale: GreyscaleConfig = GreyscaleConfig(),
    val notification: NotificationFilterConfig = NotificationFilterConfig(),
    val blocker: AppBlockerConfig = AppBlockerConfig(),
    val displayComfort: DisplayComfortConfig = DisplayComfortConfig(),
    val dnd: DndConfig = DndConfig(),
    val powerSaver: PowerSaverConfig = PowerSaverConfig(),
    /** Automation id → whether it can run, refreshed when the screen resumes. */
    val availability: Map<String, AutomationAvailability> = emptyMap(),
    val loading: Boolean = true
)

/**
 * Every field on this screen autosaves — see [mutate] and [updateName] — so there is no
 * separate "unsaved changes" state to lose on the way out. [saveProfile] still exists
 * for the Save button, but it is no longer the only thing standing between an edit and
 * losing it.
 */
class ProfileEditViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PortalLauncherApplication
    private val profileRepository = app.profileRepository

    private val _state = MutableStateFlow(ProfileEditState())
    val state: StateFlow<ProfileEditState> = _state.asStateFlow()

    /** Pending debounced write from [updateName]; flushed by [flushPendingEdits]. */
    private var nameDebounceJob: Job? = null

    /**
     * One entry per package for the notification-filter picker: the filter matches by
     * package, so listing every activity and work-profile copy would offer the same
     * choice twice.
     */
    val packages: StateFlow<List<LaunchableApp>> = app.appRepository.apps
        .map { apps -> apps.distinctBy { it.packageName }.sortedBy { it.label.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun loadProfile(profileId: String?) {
        nameDebounceJob?.cancel()
        nameDebounceJob = null
        viewModelScope.launch {
            if (profileId == null || profileId == "new") {
                _state.update {
                    ProfileEditState(
                        // Generated once, here, rather than at save time: autosave
                        // needs a stable id from the first edit onward, or every write
                        // for a still-unsaved profile would insert a new row.
                        id = "profile_${System.currentTimeMillis()}",
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
                    sortOrder = profile.sortOrder,
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
                    blocker = ConfigCodec.decodeOr(
                        configs[AutomationIds.APP_BLOCKER],
                        AppBlockerConfig()
                    ),
                    displayComfort = ConfigCodec.decodeOr(
                        configs[AutomationIds.DISPLAY_COMFORT],
                        DisplayComfortConfig()
                    ),
                    dnd = ConfigCodec.decodeOr(configs[AutomationIds.DND], DndConfig()),
                    powerSaver = ConfigCodec.decodeOr(
                        configs[AutomationIds.POWER_SAVER],
                        PowerSaverConfig()
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

    // ---- field updates ----
    //
    // Every one of these persists — see [mutate] — so leaving the screen never loses an
    // edit, autosaved rather than held only in memory until a Save button is tapped.
    // updateName is the one exception: a text field fires on every keystroke, so it
    // debounces instead of writing per character.

    fun updateName(name: String) {
        _state.update { it.copy(name = name) }
        nameDebounceJob?.cancel()
        nameDebounceJob = viewModelScope.launch {
            delay(NAME_DEBOUNCE_MILLIS)
            persist()
        }
    }

    fun updateType(type: ProfileType) = mutate { it.copy(type = type) }

    fun toggleCategory(categoryId: String) = mutate {
        it.copy(
            primaryCategories = if (categoryId in it.primaryCategories) {
                it.primaryCategories - categoryId
            } else {
                it.primaryCategories + categoryId
            }
        )
    }

    fun setAutomationEnabled(automationId: String, enabled: Boolean) = mutate {
        it.copy(
            enabledAutomationIds = if (enabled) {
                it.enabledAutomationIds + automationId
            } else {
                it.enabledAutomationIds - automationId
            }
        )
    }

    fun setGreyscaleIntensity(intensity: Float) = mutate {
        it.copy(greyscale = it.greyscale.copy(intensity = intensity.coerceIn(0f, 1f)))
    }

    fun setNotificationCancel(cancel: Boolean) = mutate {
        it.copy(notification = it.notification.copy(cancelOnFilter = cancel))
    }

    fun toggleBlockedPackage(packageName: String) = mutate {
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

    fun toggleBlockerPackage(packageName: String) = mutate {
        val blocked = it.blocker.blockedPackageNames
        it.copy(
            blocker = it.blocker.copy(
                blockedPackageNames = if (packageName in blocked) {
                    blocked - packageName
                } else {
                    blocked + packageName
                }
            )
        )
    }

    fun setDisplayBrightnessMode(mode: BrightnessMode) = mutate {
        it.copy(displayComfort = it.displayComfort.copy(brightnessMode = mode))
    }

    fun setDisplayBrightnessPercent(percent: Float) = mutate {
        it.copy(displayComfort = it.displayComfort.copy(brightnessPercent = percent.toInt().coerceIn(0, 100)))
    }

    fun setDisplayRefreshRateMode(mode: RefreshRateMode) = mutate {
        it.copy(displayComfort = it.displayComfort.copy(refreshRateMode = mode))
    }

    fun setDisplayRefreshRateHz(hz: Float) = mutate {
        it.copy(displayComfort = it.displayComfort.copy(refreshRateHz = hz))
    }

    fun setDndFilterLevel(level: DndFilterLevel) = mutate { it.copy(dnd = it.dnd.copy(filterLevel = level)) }

    fun setPowerSaverIntensity(intensity: PowerSaverIntensity) = mutate {
        it.copy(powerSaver = it.powerSaver.copy(intensity = intensity))
    }

    fun toggleExcludedPackage(packageName: String) = mutate {
        val excluded = it.powerSaver.excludedPackages
        it.copy(
            powerSaver = it.powerSaver.copy(
                excludedPackages = if (packageName in excluded) excluded - packageName else excluded + packageName
            )
        )
    }

    /**
     * Applies [block] and writes the result immediately. Every setter but [updateName]
     * calls this — a toggle or a chip pick is one discrete change, not a stream, so
     * there is nothing to debounce.
     */
    private fun mutate(block: (ProfileEditState) -> ProfileEditState) {
        _state.update(block)
        viewModelScope.launch { persist() }
    }

    /**
     * Writes a name edit still waiting on its debounce. Called when the screen leaves
     * composition — see ProfileEditScreen's `LifecycleResumeEffect` — so typing a name
     * and immediately navigating away does not lose it to the timer.
     */
    fun flushPendingEdits() {
        val hadPendingEdit = nameDebounceJob != null
        nameDebounceJob?.cancel()
        nameDebounceJob = null
        if (hadPendingEdit) viewModelScope.launch { persist() }
    }

    /** The Save button. Autosave already covers every field; this only flushes the
     * name debounce immediately rather than making the tap wait on the timer. */
    fun saveProfile(onSaved: () -> Unit) {
        nameDebounceJob?.cancel()
        nameDebounceJob = null
        viewModelScope.launch {
            persist()
            onSaved()
        }
    }

    private suspend fun persist() {
        // Guards a flush racing loadProfile's own first write — nothing to persist
        // before the initial load has produced a real state.
        if (_state.value.loading) return
        val s = _state.value
        // NonCancellable: viewModelScope is torn down the moment this screen is
        // popped, which is exactly when a mutate()-triggered write is most likely
        // still in flight — the point of autosave is that leaving does not race it.
        withContext(NonCancellable) {
            profileRepository.insertProfile(
                ProfileEntity(
                    id = s.id,
                    name = s.name.ifBlank { app.getString(s.type.labelRes) },
                    iconResName = s.iconResName,
                    type = s.type.name,
                    // Visibility is how the home screen works, so it is always on. The
                    // rest is whatever the user switched — including ids this editor
                    // does not know about, which loading kept in the set untouched.
                    enabledAutomationIds =
                        (s.enabledAutomationIds + AutomationIds.APP_VISIBILITY).toList(),
                    sortOrder = s.sortOrder
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
                AutomationIds.NOTIFICATION_FILTER to ConfigCodec.encode(s.notification),
                AutomationIds.APP_BLOCKER to ConfigCodec.encode(s.blocker),
                AutomationIds.DISPLAY_COMFORT to ConfigCodec.encode(s.displayComfort),
                AutomationIds.DND to ConfigCodec.encode(s.dnd),
                AutomationIds.POWER_SAVER to ConfigCodec.encode(s.powerSaver)
            )
            configs.forEach { (automationId, json) ->
                profileRepository.saveAutomationConfig(
                    AutomationConfigEntity(s.id, automationId, json)
                )
            }
        }
    }

    private companion object {
        const val NAME_DEBOUNCE_MILLIS = 500L
    }
}
