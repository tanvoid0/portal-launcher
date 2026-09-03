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
    private val homeCellDao: HomeCellDao,
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
                    // Widgets are per device and are not exported — see BackupProfile.
                    homeCells = homeCellDao.getForProfile(profile.id)
                        .filterNot { it.isWidget }
                        .map {
                            BackupHomeCell(
                                packageName = it.packageName,
                                activityName = it.activityName,
                                userSerial = it.userSerial,
                                page = it.page,
                                cellX = it.cellX,
                                cellY = it.cellY
                            )
                        }
                )
            },
            overrides = appOverrideDao.observeAll().first(),
            activeProfileId = preferencesRepository.activeProfileId.first(),
            customLayoutProfileIds = customLayouts.toList(),
            scheduleJson = preferencesRepository.scheduleJson.first()
        )
    }

    /**
     * Replaces the current configuration with [backup].
     *
     * Deletes by id from the *existing* set rather than truncating tables, because
     * `home_cell` and `automation_config` cascade from `profiles` and a truncate would
     * have to get the order right to avoid leaving orphans.
     *
     * Widgets the old layout held are freed by
     * [com.tanvoid0.portallauncher.widgets.LauncherWidgetHost.sweepOrphans] on the next
     * start: the cascade takes their rows without telling the widget host, and there is
     * no Context here to tell it.
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
            homeCellDao.replaceForProfile(profile.id, profile.homeCellsForRestore())
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

        // Slots naming a profile the file does not contain are dropped, same as the
        // dangling-active-id rule above: a slot that fires and points at nothing would
        // exercise the fallback in production instead of doing what the timetable says.
        val restoredIds = backup.profiles.mapTo(mutableSetOf()) { it.id }
        val schedule = ConfigCodec.decodeOr(backup.scheduleJson, SchedulerConfig())
            .let { it.copy(slots = it.slots.filter { slot -> slot.profileId in restoredIds }) }
        preferencesRepository.setScheduleJson(
            if (schedule.slots.isEmpty()) null else ConfigCodec.encode(schedule)
        )

        // A restored setup is the user's own, so the built-ins must not be seeded over
        // the top of it on next launch — including any a newer build's BuiltInProfiles
        // added after this backup was made, which the restore correctly left out.
        preferencesRepository.setBuiltInProfilesSeeded(true)
        preferencesRepository.addSeededBuiltInProfileIds(BuiltInProfiles.all.map { it.id }.toSet())

        return RestoreResult.Success(backup.profiles.size, backup.overrides.size)
    }
}
