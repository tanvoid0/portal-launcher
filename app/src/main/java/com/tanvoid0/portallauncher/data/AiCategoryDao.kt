package com.tanvoid0.portallauncher.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One category the on-device model picked for one package.
 *
 * Cached forever because the answer cannot change unless the app itself does: this
 * is what keeps inference off the hot path. A package is asked about once, ever.
 */
@Entity(tableName = "ai_category")
data class AiCategoryEntity(
    @PrimaryKey val packageName: String,
    val categoryId: String
)

@Dao
interface AiCategoryDao {

    @Query("SELECT * FROM ai_category")
    fun observeAll(): Flow<List<AiCategoryEntity>>

    @Query("SELECT packageName FROM ai_category")
    suspend fun classifiedPackages(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rows: List<AiCategoryEntity>)

    @Query("DELETE FROM ai_category")
    suspend fun clear()
}
