package com.tanvoid0.portallauncher.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Every version bump needs a [Migration] here and an exported schema under
 * `app/schemas`. There is deliberately **no** `fallbackToDestructiveMigration`: it
 * turns a forgotten migration from a build-time failure into silently deleting every
 * profile the user built, on their device, with no way back. A missing migration
 * should stop us, not cost them their setup.
 */
@Database(
    entities = [
        ProfileEntity::class,
        AutomationConfigEntity::class,
        AiCategoryEntity::class,
        HomeItemEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : androidx.room.RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun automationConfigDao(): AutomationConfigDao
    abstract fun aiCategoryDao(): AiCategoryDao
    abstract fun homeItemDao(): HomeItemDao

    companion object {
        private const val DB_NAME = "portal_launcher.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                .addMigrations(*AppDatabaseMigrations.all)
                .build()
    }
}

/**
 * Kept out of the companion so the migration test can run a single migration
 * directly against a database built from the previous version's exported schema.
 */
object AppDatabaseMigrations {

    /**
     * Adds the AI category cache. Written by hand rather than left to destructive
     * fallback, because dropping the database to add a *cache* would take the user's
     * profiles with it.
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `ai_category` " +
                    "(`packageName` TEXT NOT NULL, `categoryId` TEXT NOT NULL, " +
                    "PRIMARY KEY(`packageName`))"
            )
        }
    }

    /**
     * Adds the home layout table: which apps the user put on which profile's home
     * screen, in what order.
     *
     * This SQL has to match what Room generates for [HomeItemEntity] exactly —
     * column order, the composite primary key, the foreign key clause and the index
     * name. `runMigrationsAndValidate` diffs the result against the exported schema
     * and fails on any difference, which is the point of exporting them.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `home_item` (" +
                    "`profileId` TEXT NOT NULL, " +
                    "`packageName` TEXT NOT NULL, " +
                    "`activityName` TEXT NOT NULL, " +
                    "`userSerial` INTEGER NOT NULL, " +
                    "`position` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`profileId`, `packageName`, `activityName`, `userSerial`), " +
                    "FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_home_item_profileId` ON `home_item` (`profileId`)"
            )
        }
    }

    val all: Array<Migration> get() = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
