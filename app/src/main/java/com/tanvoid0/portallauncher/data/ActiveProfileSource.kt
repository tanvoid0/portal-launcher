package com.tanvoid0.portallauncher.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * The one place that answers "which profile is in effect, and what is it configured to
 * do".
 *
 * Both the launcher UI and the automation engine need this, and they must not disagree:
 * a home screen showing Study while the engine has applied Gaming's rules is worse than
 * either being wrong on its own. Everything derives from [activeProfile] here rather
 * than from the stored preference — see [resolveActiveProfile] for why the raw
 * preference is not enough.
 */
class ActiveProfileSource(
    private val profileRepository: ProfileRepository,
    private val preferencesRepository: PreferencesRepository
) {

    val activeProfile: Flow<ProfileEntity?> = combine(
        preferencesRepository.activeProfileId,
        profileRepository.getAllProfiles()
    ) { activeId, profiles -> resolveActiveProfile(profiles, activeId) }
        .distinctUntilChanged()

    /**
     * Every automation config for the profile in effect, keyed by automation id, along
     * with whether the profile has that automation switched on.
     *
     * An automation that is *off* still needs to be visible to the engine, because
     * leaving a profile that had it on is what triggers the revert.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val activeConfigs: Flow<ActiveProfileConfig> = activeProfile.flatMapLatest { profile ->
        if (profile == null) {
            flowOf(ActiveProfileConfig(null, emptyMap()))
        } else {
            profileRepository.getAutomationConfigsForProfile(profile.id).map { rows ->
                ActiveProfileConfig(
                    profile = profile,
                    configJsonByAutomationId = rows.associate { it.automationId to it.configJson }
                )
            }
        }
    }
}

/** The profile in effect plus its stored automation configs. */
data class ActiveProfileConfig(
    val profile: ProfileEntity?,
    val configJsonByAutomationId: Map<String, String>
) {
    fun isEnabled(automationId: String): Boolean =
        profile != null && automationId in profile.enabledAutomationIds

    fun configJson(automationId: String): String? = configJsonByAutomationId[automationId]
}
