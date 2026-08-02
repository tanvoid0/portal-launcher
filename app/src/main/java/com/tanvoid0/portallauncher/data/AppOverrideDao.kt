package com.tanvoid0.portallauncher.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * What the user changed about one app: hidden it, renamed it, or both.
 *
 * Deliberately **not** per profile, unlike [HomeItemEntity]. Per-profile visibility is
 * already what categories do; "hide this app" and "call it something else" are
 * statements about the app itself, so scoping them per profile would mean the user
 * hiding the same thing seven times.
 *
 * Identified the same way as a home item — package, activity and user serial — so a
 * work-profile app and its personal twin can be treated separately.
 */
@Entity(
    tableName = "app_override",
    primaryKeys = ["packageName", "activityName", "userSerial"]
)
data class AppOverrideEntity(
    val packageName: String,
    val activityName: String,
    val userSerial: Long,
    val hidden: Boolean = false,
    val customLabel: String? = null
)

@Dao
interface AppOverrideDao {

    @Query("SELECT * FROM app_override")
    fun observeAll(): Flow<List<AppOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(override: AppOverrideEntity)

    /**
     * Drops rows that no longer say anything, so an app the user hid and then unhid
     * stops occupying a row and cannot resurface as a stale override later.
     */
    @Query("DELETE FROM app_override WHERE hidden = 0 AND (customLabel IS NULL OR customLabel = '')")
    suspend fun pruneEmpty()
}
