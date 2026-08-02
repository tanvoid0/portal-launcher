package com.tanvoid0.portallauncher.data

import kotlinx.coroutines.flow.first

/**
 * Builds a backup from the database, and puts one back.
 *
 * Restore **replaces** rather than merges. Merging two sets of profiles produces a result
 * neither the file nor the device describes, and there is no way for the user to predict
 * it; "this file is now your setup" is a promise that can be kept.
 */
class BackupRepository(
    private val profileDao: ProfileDao,
    private val automationConfigDao: AutomationConfigDao,
    private val homeItemDao: HomeItemDao,
    private val appOverrideDao: AppOverrideDao,
    private val preferencesRepository: PreferencesRepository
) {

    suspend fun export(): LauncherBackup {
        val profiles = profileDao.getAllProfiles().first()
        val customLayouts = preferencesRepository.customLayoutProfileIds.first()
        return LauncherBackup(
            profiles = profiles.map { profile ->
                BackupProfile(
                    id = profile.id,
                    name = profile.name,
                    iconResName = profile.iconResName,
                    type = profile.type,
                    enabledAutomationIds = profile.enabledAutomationIds,
                    sortOrder = profile.sortOrder,
                    automationConfigs = automationConfigDao
                        .getConfigsForProfile(profile.id).first()
                        .associate { it.automationId to it.configJson },
                    homeItems = homeItemDao.observeForProfile(profile.id).first().map {
                        BackupHomeItem(it.packageName, it.activityName, it.userSerial, it.position)
                    }
                )
            },
            overrides = appOverrideDao.observeAll().first(),
            activeProfileId = preferencesRepository.activeProfileId.first(),
            customLayoutProfileIds = customLayouts.toList()
        )
    }

    /**
     * Replaces the current configuration with [backup].
     *
     * Deletes by id from the *existing* set rather than truncating tables, because
     * `home_item` and `automation_config` cascade from `profiles` and a truncate would
     * have to get the order right to avoid leaving orphans.
     */
    suspend fun restore(backup: LauncherBackup): RestoreResult {
        BackupCodec.validate(backup)?.let { return it }

        profileDao.getAllProfiles().first().forEach { existing ->
            // Cascades take automation_config and home_item with each profile.
            profileDao.deleteById(existing.id)
        }

        backup.profiles.forEach { profile ->
            profileDao.insert(
                ProfileEntity(
                    id = profile.id,
                    name = profile.name,
                    iconResName = profile.iconResName,
                    type = profile.type,
                    enabledAutomationIds = profile.enabledAutomationIds,
                    sortOrder = profile.sortOrder
                )
            )
            profile.automationConfigs.forEach { (automationId, configJson) ->
                automationConfigDao.insert(
                    AutomationConfigEntity(profile.id, automationId, configJson)
                )
            }
            homeItemDao.replaceForProfile(
                profile.id,
                profile.homeItems.map {
                    HomeItemEntity(profile.id, it.packageName, it.activityName, it.userSerial, it.position)
                }
            )
            preferencesRepository.setLayoutCustomised(
                profile.id,
                profile.id in backup.customLayoutProfileIds
            )
        }

        // Overrides are keyed by package, so they restore whether or not the app is
        // installed here. An override for something absent costs one row and starts
        // working again if the app is ever installed.
        backup.overrides.forEach { appOverrideDao.upsert(it) }

        // Only if the profile it names actually came back; resolveActiveProfile would
        // fall back anyway, but storing a dangling id is how that fallback gets exercised
        // in production instead of in a test.
        backup.activeProfileId
            ?.takeIf { id -> backup.profiles.any { it.id == id } }
            ?.let { preferencesRepository.setActiveProfileId(it) }

        // A restored setup is the user's own, so the built-ins must not be seeded over
        // the top of it on next launch.
        preferencesRepository.setBuiltInProfilesSeeded(true)

        return RestoreResult.Success(backup.profiles.size, backup.overrides.size)
    }
}
