package com.tanvoid0.portallauncher.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

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
// Serializable so it goes straight into a backup: the row *is* the thing worth keeping,
// and a parallel DTO would be two shapes to keep in step for no gain.
@Serializable
data class AppOverrideEntity(
    val packageName: String,
    val activityName: String,
    val userSerial: Long,
    val hidden: Boolean = false,
    val customLabel: String? = null,
    /**
     * [AppCategory.id] the user assigned, overriding every automatic source.
     *
     * The manual escape hatch for the categoriser being wrong, which it will be: the
     * Study profile is empty on a stock device because Calendar and Drive both declare
     * `CATEGORY_PRODUCTIVITY`. Null means "keep deciding automatically", which is not
     * the same as any particular category.
     */
    val categoryId: String? = null
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
     *
     * Every nullable field has to appear here. Miss one and that column's value is
     * silently deleted the next time an unrelated override is cleared.
     */
    @Query(
        "DELETE FROM app_override WHERE hidden = 0 " +
            "AND (customLabel IS NULL OR customLabel = '') " +
            "AND categoryId IS NULL"
    )
    suspend fun pruneEmpty()
}
