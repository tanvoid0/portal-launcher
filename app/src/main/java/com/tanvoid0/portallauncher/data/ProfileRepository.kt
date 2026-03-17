package com.tanvoid0.portallauncher.data

import kotlinx.coroutines.flow.Flow
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

    fun getAutomationConfig(profileId: String, automationId: String): Flow<AutomationConfigEntity?> =
        automationConfigDao.getConfig(profileId, automationId)

    fun getAutomationConfigsForProfile(profileId: String): Flow<List<AutomationConfigEntity>> =
        automationConfigDao.getConfigsForProfile(profileId)

    suspend fun saveAutomationConfig(config: AutomationConfigEntity) =
        automationConfigDao.insert(config)

    fun getVisibilityConfigForProfile(profileId: String): Flow<AppVisibilityConfig?> =
        automationConfigDao.getConfig(profileId, AutomationIds.APP_VISIBILITY).map { entity ->
            entity?.let { ConfigJson.parseAppVisibility(it.configJson) }
        }
}
