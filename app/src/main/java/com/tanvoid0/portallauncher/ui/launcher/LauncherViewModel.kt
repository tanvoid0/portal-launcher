package com.tanvoid0.portallauncher.ui.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tanvoid0.portallauncher.PortalLauncherApplication
import com.tanvoid0.portallauncher.data.AppCategoryRules
import com.tanvoid0.portallauncher.data.AppVisibilityConfig
import com.tanvoid0.portallauncher.data.ProfileEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LauncherUiState(
    val activeProfile: ProfileEntity? = null,
    val profiles: List<ProfileEntity> = emptyList(),
    val appItems: List<AppItem> = emptyList(),
    val loading: Boolean = true
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PortalLauncherApplication
    private val profileRepository = app.profileRepository
    private val preferencesRepository = app.preferencesRepository

    private val _appItemsRaw = MutableStateFlow<List<AppItem>>(emptyList())

    private val visibilityConfigFlow = preferencesRepository.defaultProfileId.flatMapLatest { profileId ->
        if (profileId != null) profileRepository.getVisibilityConfigForProfile(profileId)
        else flowOf(null)
    }

    val uiState: StateFlow<LauncherUiState> = combine(
        preferencesRepository.defaultProfileId,
        profileRepository.getAllProfiles(),
        visibilityConfigFlow,
        _appItemsRaw
    ) { defaultId, profiles, visibilityConfig, apps ->
        val active = defaultId?.let { id -> profiles.find { it.id == id } }
        val filtered = filterAppsByProfile(apps, active, visibilityConfig)
        LauncherUiState(
            activeProfile = active,
            profiles = profiles,
            appItems = filtered,
            loading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LauncherUiState(loading = true)
    )

    fun setAppItems(items: List<AppItem>) {
        _appItemsRaw.update { items }
    }

    fun setDefaultProfile(profileId: String) {
        viewModelScope.launch {
            preferencesRepository.setDefaultProfileId(profileId)
        }
    }

    private fun filterAppsByProfile(
        apps: List<AppItem>,
        profile: ProfileEntity?,
        visibilityConfig: AppVisibilityConfig?
    ): List<AppItem> {
        if (profile == null || visibilityConfig == null) return apps
        val primary = visibilityConfig.primaryCategoryIds.toSet()
        if (primary.isEmpty()) return apps
        return apps.filter { app ->
            val category = AppCategoryRules.categoryFor(app.packageName)
            category.id in primary
        }
    }
}
