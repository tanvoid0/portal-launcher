package com.tanvoid0.portallauncher.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class ProfileRepository(
    private val profileDao: ProfileDao,
    private val automationConfigDao: AutomationConfigDao
) {

    fun getAllProfiles(): Flow<List<ProfileEntity>> = profileDao.getAllProfiles()

    fun getProfileById(id: String): Flow<ProfileEntity?> = profileDao.getProfileById(id)

    suspend fun insertProfile(profile: ProfileEntity) = profileDao.insert(profile)

    suspend fun insertProfiles(profiles: List<ProfileEntity>) = profileDao.insertAll(profiles)

    suspend fun deleteProfile(id: String) {
        profileDao.deleteById(id)
        automationConfigDao.deleteByProfileId(id)
    }

    suspend fun updateSortOrder(id: String, order: Int) = profileDao.updateSortOrder(id, order)

    /**
     * Inserts every [BuiltInProfiles] entry that is neither [alreadySeeded] nor
     * already in the table, with its app-visibility config plus whatever else
     * [BuiltInProfile.automations] lists.
     *
     * Missing-only rather than insert-all: an install from before the built-ins
     * existed already has a "default" row, and overwriting it would throw away
     * whatever the user had done to it. [alreadySeeded] is what stops a profile the
     * user deleted from coming back — the in-table check alone cannot tell "never
     * seeded" from "seeded, then deleted", and this can now run more than once, as
     * new built-in templates are added to [BuiltInProfiles.all] on an existing
     * install — see [PreferencesRepository.seededBuiltInProfileIds].
     *
     * [resolveName] turns a built-in's name resource into text — the caller has the
     * Context, this repository does not. Resolved exactly once, here: after seeding,
     * a profile's name is the user's data, the same as a rename.
     */
    suspend fun seedBuiltInProfiles(alreadySeeded: Set<String>, resolveName: (BuiltInProfile) -> String) {
        val existing = profileDao.getAllProfiles().first().mapTo(mutableSetOf()) { it.id }
        // Index into `all`, not into the filtered remainder: the sort order has to
        // match the declared order even when some of the list is already present.
        BuiltInProfiles.all.forEachIndexed { index, builtIn ->
            if (builtIn.id in alreadySeeded || builtIn.id in existing) return@forEachIndexed
            profileDao.insert(
                ProfileEntity(
                    id = builtIn.id,
                    name = resolveName(builtIn),
                    iconResName = builtIn.id,
                    type = builtIn.type.name,
                    enabledAutomationIds = listOf(AutomationIds.APP_VISIBILITY) +
                        builtIn.automations.map { it.automationId },
                    sortOrder = index
                )
            )
            automationConfigDao.insert(
                AutomationConfigEntity(
                    profileId = builtIn.id,
                    automationId = AutomationIds.APP_VISIBILITY,
                    configJson = ConfigCodec.encode(BuiltInProfiles.visibilityConfig(builtIn))
                )
            )
            builtIn.automations.forEach { seed ->
                automationConfigDao.insert(AutomationConfigEntity(builtIn.id, seed.automationId, seed.configJson))
            }
        }
    }

    fun getAutomationConfig(profileId: String, automationId: String): Flow<AutomationConfigEntity?> =
        automationConfigDao.getConfig(profileId, automationId)

    fun getAutomationConfigsForProfile(profileId: String): Flow<List<AutomationConfigEntity>> =
        automationConfigDao.getConfigsForProfile(profileId)

    suspend fun saveAutomationConfig(config: AutomationConfigEntity) =
        automationConfigDao.insert(config)

    fun getVisibilityConfigForProfile(profileId: String): Flow<AppVisibilityConfig?> =
        automationConfigDao.getConfig(profileId, AutomationIds.APP_VISIBILITY).map { entity ->
            entity?.let { ConfigCodec.decodeOr(it.configJson, AppVisibilityConfig()) }
        }
}

/**
 * The profile actually in effect, given the stored id.
 *
 * The stored id can point at nothing: deleting the active profile leaves the
 * preference dangling, and a fresh install has an id before it has rows. Both used
 * to resolve to null, which silently disabled all filtering and left no profile
 * selected in the list. Falling back keeps a profile in effect at all times.
 *
 * Pure so both the home screen and the profile list resolve it identically — if they
 * disagreed, the list would show one profile selected while another was applied.
 */
fun resolveActiveProfile(profiles: List<ProfileEntity>, activeId: String?): ProfileEntity? =
    profiles.find { it.id == activeId }
        ?: profiles.find { it.id == BuiltInProfiles.DEFAULT_ID }
        ?: profiles.firstOrNull()
