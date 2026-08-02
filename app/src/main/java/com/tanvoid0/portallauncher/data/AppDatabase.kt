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
        HomeCellEntity::class,
        AppOverrideEntity::class
    ],
    version = 6,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : androidx.room.RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun automationConfigDao(): AutomationConfigDao
    abstract fun aiCategoryDao(): AiCategoryDao
    abstract fun homeCellDao(): HomeCellDao
    abstract fun appOverrideDao(): AppOverrideDao

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
     * This SQL has to match what Room generated for the entity of the day exactly —
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

    /**
     * Adds the per-app overrides: hidden apps and renames.
     *
     * `hidden` is stored as INTEGER because SQLite has no boolean, which is what Room
     * generates for a Kotlin `Boolean`; `customLabel` is the one nullable column in
     * the schema, so it is the only one without NOT NULL.
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `app_override` (" +
                    "`packageName` TEXT NOT NULL, " +
                    "`activityName` TEXT NOT NULL, " +
                    "`userSerial` INTEGER NOT NULL, " +
                    "`hidden` INTEGER NOT NULL, " +
                    "`customLabel` TEXT, " +
                    "PRIMARY KEY(`packageName`, `activityName`, `userSerial`))"
            )
        }
    }

    /**
     * Adds the manual category override.
     *
     * `ALTER TABLE ADD COLUMN` rather than a rebuild: the column is nullable with no
     * default, which is the one shape SQLite can add in place, so existing overrides
     * keep their hidden flag and rename untouched.
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `app_override` ADD COLUMN `categoryId` TEXT")
        }
    }

    /**
     * Replaces the flat home layout with a paged grid that can also hold widgets.
     *
     * `home_item` stored a single `position: Int`, which cannot describe a rectangle and
     * therefore cannot describe a widget. `home_cell` stores a page and a top-left cell
     * with spans — see [HomeCellEntity] for why apps and widgets share one table.
     *
     * The existing layout is *converted*, not discarded: a position becomes a cell by
     * the reading-order arithmetic in [LegacyLayout], which a backup written by an older
     * build reads the same way.
     *
     * `home_item` is dropped rather than left behind. Two tables that both claim to be
     * the home layout is exactly how one of them goes stale.
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `home_cell` (" +
                    "`profileId` TEXT NOT NULL, " +
                    "`page` INTEGER NOT NULL, " +
                    "`cellX` INTEGER NOT NULL, " +
                    "`cellY` INTEGER NOT NULL, " +
                    "`spanX` INTEGER NOT NULL, " +
                    "`spanY` INTEGER NOT NULL, " +
                    "`kind` TEXT NOT NULL, " +
                    "`packageName` TEXT NOT NULL, " +
                    "`activityName` TEXT NOT NULL, " +
                    "`userSerial` INTEGER NOT NULL, " +
                    "`appWidgetId` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`profileId`, `page`, `cellX`, `cellY`), " +
                    "FOREIGN KEY(`profileId`) REFERENCES `profiles`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_home_cell_profileId` ON `home_cell` (`profileId`)"
            )
            // Positions are unique within a profile (they are only ever written as
            // max+1 or as a full reindex), so the derived primary key cannot collide.
            db.execSQL(
                "INSERT INTO `home_cell` (`profileId`, `page`, `cellX`, `cellY`, `spanX`, " +
                    "`spanY`, `kind`, `packageName`, `activityName`, `userSerial`, `appWidgetId`) " +
                    "SELECT `profileId`, `position` / ${LegacyLayout.PER_PAGE}, " +
                    "(`position` % ${LegacyLayout.PER_PAGE}) % ${LegacyLayout.COLUMNS}, " +
                    "(`position` % ${LegacyLayout.PER_PAGE}) / ${LegacyLayout.COLUMNS}, " +
                    "1, 1, 'app', `packageName`, `activityName`, `userSerial`, 0 " +
                    "FROM `home_item`"
            )
            db.execSQL("DROP TABLE `home_item`")
        }
    }

    val all: Array<Migration>
        get() = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6
        )
}
