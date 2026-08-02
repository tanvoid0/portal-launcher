package com.tanvoid0.portallauncher

import android.app.Application
import com.tanvoid0.portallauncher.ai.GeminiNanoCategorizer
import com.tanvoid0.portallauncher.automation.ProfileEngine
import com.tanvoid0.portallauncher.automation.ProfileScheduler
import com.tanvoid0.portallauncher.data.ActiveProfileSource
import com.tanvoid0.portallauncher.data.AppDatabase
import com.tanvoid0.portallauncher.data.AppRepository
import com.tanvoid0.portallauncher.data.IconCache
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

    /**
     * Application-scoped so it survives navigation: a ViewModel-scoped icon cache is
     * discarded exactly when the drawer is about to be reopened.
     */
    val iconCache: IconCache by lazy { IconCache(this, appRepository, appScope) }
    val profileRepository: ProfileRepository by lazy {
        ProfileRepository(database.profileDao(), database.automationConfigDao())
    }

    /**
     * The single answer to "which profile is in effect". Shared by the launcher UI, the
     * automation engine, the notification listener, the scheduler and the tile, so they
     * cannot disagree about what is active.
     */
    val activeProfileSource: ActiveProfileSource by lazy {
        ActiveProfileSource(profileRepository, preferencesRepository)
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
            // After seeding, so the first sync sees the profiles it may need to switch to.
            ProfileScheduler(this@PortalLauncherApplication).sync()
        }
        // Applies and reverts automations for the lifetime of the process. Started here
        // rather than from an Activity because a profile's effects must not depend on the
        // launcher UI being on screen.
        ProfileEngine(this, activeProfileSource).start(appScope)
    }
}
