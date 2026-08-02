package com.tanvoid0.portallauncher.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * How many times the user has launched one app. The whole point is the
 * "most used" drawer sort — see [applyUsage] — so there is no reason to track
 * anything finer than a running count.
 */
@Entity(
    tableName = "app_usage",
    primaryKeys = ["packageName", "activityName", "userSerial"]
)
data class AppUsageEntity(
    val packageName: String,
    val activityName: String,
    val userSerial: Long,
    val launchCount: Int = 0
)

@Dao
interface AppUsageDao {

    @Query("SELECT * FROM app_usage")
    fun observeAll(): Flow<List<AppUsageEntity>>

    /**
     * One statement for "first launch or the hundredth": SQLite's own upsert means the
     * caller never has to read the row first to decide insert vs. update.
     */
    @Query(
        "INSERT INTO app_usage (packageName, activityName, userSerial, launchCount) " +
            "VALUES (:packageName, :activityName, :userSerial, 1) " +
            "ON CONFLICT(packageName, activityName, userSerial) " +
            "DO UPDATE SET launchCount = launchCount + 1"
    )
    suspend fun recordLaunch(packageName: String, activityName: String, userSerial: Long)
}
