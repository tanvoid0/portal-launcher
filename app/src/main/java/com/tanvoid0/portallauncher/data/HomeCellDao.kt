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
 * One thing the user placed on one page of one profile's home screen: an app, or a
 * widget.
 *
 * Replaces the old `home_item` table, which stored a flat `position: Int`. A linear
 * position cannot express a widget — widgets occupy a rectangle, and two of them on
 * the same page must not be allowed to overlap — so pages and widgets needed the same
 * change and got one table rather than two. Apps and widgets in *separate* tables
 * would mean two sources of truth for which cells are occupied, and a drag would only
 * have to fail once for them to disagree permanently.
 *
 * The primary key is the top-left cell, which is what makes "one thing starts here"
 * a database rule instead of a hope. Overlap between *spans* still has to be checked
 * in code — see [canPlace] — because SQLite cannot express it.
 *
 * [userSerial] rather than a [android.os.UserHandle] for the same reason as before:
 * a serial is stable across boots, a UserHandle is not.
 */
@Entity(
    tableName = "home_cell",
    primaryKeys = ["profileId", "page", "cellX", "cellY"],
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
data class HomeCellEntity(
    val profileId: String,
    val page: Int,
    val cellX: Int,
    val cellY: Int,
    val spanX: Int,
    val spanY: Int,
    /** [KIND_APP] or [KIND_WIDGET]. */
    val kind: String,
    /** The app's package, or the widget provider's package. */
    val packageName: String,
    /** The app's activity, or the widget provider's class. */
    val activityName: String,
    val userSerial: Long,
    /**
     * The id [android.appwidget.AppWidgetHost.allocateAppWidgetId] gave us, or
     * [NO_WIDGET] for an app.
     *
     * This is the widget's real identity — two copies of the same provider are two
     * different widgets with their own configuration — so it is stored rather than
     * derived, and deleting a row means telling the host to release it. See
     * [com.tanvoid0.portallauncher.widgets.LauncherWidgetHost.sweepOrphans].
     */
    val appWidgetId: Int
) {
    val isWidget: Boolean get() = kind == KIND_WIDGET

    /** Matches [LaunchableApp.key], so a cell and an app can be looked up by the same string. */
    val appKey: String get() = "$packageName/$activityName/$userSerial"

    companion object {
        const val KIND_APP = "app"
        const val KIND_WIDGET = "widget"
        const val NO_WIDGET = 0
    }
}

@Dao
interface HomeCellDao {

    @Query("SELECT * FROM home_cell WHERE profileId = :profileId ORDER BY page, cellY, cellX")
    fun observeForProfile(profileId: String): Flow<List<HomeCellEntity>>

    @Query("SELECT * FROM home_cell WHERE profileId = :profileId ORDER BY page, cellY, cellX")
    suspend fun getForProfile(profileId: String): List<HomeCellEntity>

    /**
     * Every widget id the database still refers to, across all profiles.
     *
     * The input to the orphan sweep: an id the host holds but no row names is a widget
     * the user cannot see and cannot remove, and it stays bound to its provider forever.
     */
    @Query("SELECT appWidgetId FROM home_cell WHERE kind = 'widget'")
    suspend fun allWidgetIds(): List<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cell: HomeCellEntity)

    @Query(
        "DELETE FROM home_cell WHERE profileId = :profileId AND page = :page " +
            "AND cellX = :cellX AND cellY = :cellY"
    )
    suspend fun deleteAt(profileId: String, page: Int, cellX: Int, cellY: Int)

    /** Removes an app from one profile's home screen wherever it sits. Never matches a widget. */
    @Query(
        "DELETE FROM home_cell WHERE profileId = :profileId AND kind = 'app' " +
            "AND packageName = :packageName AND activityName = :activityName " +
            "AND userSerial = :userSerial"
    )
    suspend fun removeApp(
        profileId: String,
        packageName: String,
        activityName: String,
        userSerial: Long
    )

    /**
     * Rewrites one profile's whole layout in a single transaction.
     *
     * Replace-all rather than per-row updates for the same reason the old table did it:
     * a drag is one intent, and expressing it as a delete plus an insert that can be
     * interrupted between them leaves the grid holding two things in one cell.
     */
    @Transaction
    suspend fun replaceForProfile(profileId: String, cells: List<HomeCellEntity>) {
        deleteForProfile(profileId)
        cells.forEach { upsert(it) }
    }

    @Query("DELETE FROM home_cell WHERE profileId = :profileId")
    suspend fun deleteForProfile(profileId: String)
}
