package com.tanvoid0.portallauncher.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tanvoid0.portallauncher.PortalLauncherApplication
import com.tanvoid0.portallauncher.ai.AiStatus
import com.tanvoid0.portallauncher.data.AiCategoryEntity
import com.tanvoid0.portallauncher.data.AppCategorizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owns the one optional feature on this screen: letting the on-device model place
 * the apps that neither the system category nor the keyword rules could.
 *
 * Nothing here runs unless the user switched it on, and nothing the launcher does
 * waits on it — the classification result lands in Room and the home screen picks
 * it up through its normal flow, whenever it arrives.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PortalLauncherApplication
    private val preferences = app.preferencesRepository
    private val aiCategoryDao = app.database.aiCategoryDao()
    private val categorizer = app.aiCategorizer

    private val _aiStatus = MutableStateFlow(AiStatus.Unavailable)
    val aiStatus: StateFlow<AiStatus> = _aiStatus.asStateFlow()

    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()

    val aiEnabled: StateFlow<Boolean> = preferences.aiCategoriesEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            _aiStatus.value = categorizer.status()
            // Opening settings is also how apps installed since the last run get
            // classified.
            // ponytail: no background job — new apps wait until the user next opens
            // this screen. Move the sweep behind LauncherApps.onPackageAdded if that
            // lag ever shows up in practice.
            if (preferences.aiCategoriesEnabled.first()) sweep()
        }
    }

    fun setAiEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAiCategoriesEnabled(enabled)
            if (!enabled) {
                // Off means off: drop what the model decided so categories revert to
                // exactly what the rules alone produce. Re-enabling re-runs it.
                aiCategoryDao.clear()
                return@launch
            }
            if (_aiStatus.value == AiStatus.Downloadable) {
                _working.value = true
                _aiStatus.value = categorizer.download()
            }
            sweep()
        }
    }

    /** Classifies only packages nothing else could place, and only once each. */
    private suspend fun sweep() {
        _working.value = true
        try {
            val alreadyAsked = aiCategoryDao.classifiedPackages().toSet()
            val unplaced = app.appRepository.apps.first().filter {
                it.packageName !in alreadyAsked && AppCategorizer.needsClassification(it)
            }
            val classified = categorizer.classify(unplaced)
            if (classified.isNotEmpty()) {
                aiCategoryDao.upsert(
                    classified.map { (packageName, category) ->
                        AiCategoryEntity(packageName, category.id)
                    }
                )
            }
        } finally {
            _working.value = false
        }
    }
}
