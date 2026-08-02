package com.tanvoid0.portallauncher.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * One app the user placed on the home screen of one profile, at one position.
 *
 * Per-profile by design: the point of a profile is a different home screen, so the
 * layout belongs to the profile, not the device. Deleting a profile takes its layout
 * with it via the cascade.
 *
 * [userSerial] rather than a [android.os.UserHandle] because this outlives the
 * process: `UserHandle` is only meaningful for the current boot, while
 * [android.os.UserManager.getSerialNumberForUser] is stable, which is what a pinned
 * work-profile app needs to still resolve tomorrow.
 */
@Entity(
    tableName = "home_item",
    primaryKeys = ["profileId", "packageName", "activityName", "userSerial"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId")]
)
data class HomeItemEntity(
    val profileId: String,
    val packageName: String,
    val activityName: String,
    val userSerial: Long,
    val position: Int
)

@Dao
interface HomeItemDao {

    @Query("SELECT * FROM home_item WHERE profileId = :profileId ORDER BY position ASC")
    fun observeForProfile(profileId: String): Flow<List<HomeItemEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM home_item WHERE profileId = :profileId")
    suspend fun nextPosition(profileId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: HomeItemEntity)

    @Query(
        "DELETE FROM home_item WHERE profileId = :profileId AND packageName = :packageName " +
            "AND activityName = :activityName AND userSerial = :userSerial"
    )
    suspend fun remove(
        profileId: String,
        packageName: String,
        activityName: String,
        userSerial: Long
    )

    /**
     * Rewrites the whole profile's layout in one transaction.
     *
     * Replace-all rather than per-row position updates: a reorder is one intent, and
     * writing it as N updates leaves the table holding duplicate positions if the
     * process dies partway.
     */
    @Transaction
    suspend fun replaceForProfile(profileId: String, items: List<HomeItemEntity>) {
        deleteForProfile(profileId)
        items.forEachIndexed { index, item -> upsert(item.copy(position = index)) }
    }

    @Query("DELETE FROM home_item WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: String)
}
