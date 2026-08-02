package com.tanvoid0.portallauncher

import android.app.Application
import com.tanvoid0.portallauncher.ai.GeminiNanoCategorizer
import com.tanvoid0.portallauncher.data.AppDatabase
import com.tanvoid0.portallauncher.data.AppRepository
import com.tanvoid0.portallauncher.data.PreferencesRepository
import com.tanvoid0.portallauncher.data.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    /**
     * Application-lifetime scope for work that outlives any one screen. Supervised so
     * one failed job cannot cancel the others, and held here rather than created ad
     * hoc so there is a single place that owns background work.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Writes the shipped profiles the first time this install runs.
     *
     * Not moved into a `RoomDatabase.Callback` as the plan originally said to: that
     * callback hands you a raw SupportSQLiteDatabase, so seeding there means
     * re-expressing every [BuiltInProfiles] entry as execSQL and keeping the two in
     * step by hand. The race it would close is one frame of an unfiltered home
     * screen, which is exactly what the "All apps" profile shows anyway — and
     * [com.tanvoid0.portallauncher.data.resolveActiveProfile] now keeps a profile in
     * effect even before the seed lands. Duplicated SQL is the worse trade.
     */
    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            if (!preferencesRepository.builtInProfilesSeeded.first()) {
                profileRepository.seedBuiltInProfiles()
                preferencesRepository.setBuiltInProfilesSeeded(true)
            }
        }
    }
}
