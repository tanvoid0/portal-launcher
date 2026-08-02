package com.tanvoid0.portallauncher.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ProfileEntity::class, AutomationConfigEntity::class, AiCategoryEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : androidx.room.RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun automationConfigDao(): AutomationConfigDao
    abstract fun aiCategoryDao(): AiCategoryDao

    companion object {
        private const val DB_NAME = "portal_launcher.db"

        /**
         * Adds the AI category cache. A real migration rather than letting
         * [fallbackToDestructiveMigration] handle it, because destructive fallback
         * would take the user's profiles with it — a cache is not worth that.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ai_category` " +
                        "(`packageName` TEXT NOT NULL, `categoryId` TEXT NOT NULL, " +
                        "PRIMARY KEY(`packageName`))"
                )
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
    }
}
