package com.tanvoid0.portallauncher

import android.app.Application
import com.tanvoid0.portallauncher.ai.GeminiNanoCategorizer
import com.tanvoid0.portallauncher.data.AppDatabase
import com.tanvoid0.portallauncher.data.AppRepository
import com.tanvoid0.portallauncher.data.PreferencesRepository
import com.tanvoid0.portallauncher.data.ProfileEntity
import com.tanvoid0.portallauncher.data.ProfileRepository
import com.tanvoid0.portallauncher.data.ProfileType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PortalLauncherApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val preferencesRepository: PreferencesRepository by lazy { PreferencesRepository(this) }
    val appRepository: AppRepository by lazy { AppRepository(this) }
    val profileRepository: ProfileRepository by lazy {
        ProfileRepository(database.profileDao(), database.automationConfigDao())
    }

    /**
     * Optional. Constructed lazily and never touched on the start-up path, so a
     * device with no AICore support pays nothing for its existence.
     */
    val aiCategorizer: GeminiNanoCategorizer by lazy { GeminiNanoCategorizer() }

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            val profiles = profileRepository.getAllProfiles().first()
            if (profiles.isEmpty()) {
                val default = ProfileEntity(
                    id = "default",
                    name = "All apps",
                    iconResName = "default",
                    type = ProfileType.Custom.name,
                    enabledAutomationIds = emptyList(),
                    sortOrder = 0
                )
                profileRepository.insertProfile(default)
                preferencesRepository.setActiveProfileId("default")
            }
        }
    }
}
