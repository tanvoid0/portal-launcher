package com.tanvoid0.portallauncher.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationConfigDao {

    @Query("SELECT * FROM automation_config WHERE profileId = :profileId")
    fun getConfigsForProfile(profileId: String): Flow<List<AutomationConfigEntity>>

    @Query("SELECT * FROM automation_config WHERE profileId = :profileId AND automationId = :automationId LIMIT 1")
    fun getConfig(profileId: String, automationId: String): Flow<AutomationConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: AutomationConfigEntity)

    @Query("DELETE FROM automation_config WHERE profileId = :profileId")
    suspend fun deleteByProfileId(profileId: String)
}
