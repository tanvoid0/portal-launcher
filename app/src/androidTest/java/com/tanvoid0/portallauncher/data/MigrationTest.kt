package com.tanvoid0.portallauncher.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The test that makes shipping a schema change safe.
 *
 * There is no `fallbackToDestructiveMigration` any more, so a wrong migration is a
 * crash on launch rather than silent data loss — but a *lossy* migration would still
 * be silent. This asserts the thing users actually care about: their profiles are
 * still there afterwards.
 *
 * `runMigrationsAndValidate` additionally diffs the resulting schema against the
 * exported JSON, so a migration whose SQL drifts from the entity fails here instead
 * of on a device.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate2To3_addsHomeItemAndKeepsUserData() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO profiles (id, name, iconResName, type, enabledAutomationIds, sortOrder) " +
                    "VALUES ('study', 'My Study Setup', 'study', 'Study', 'app_visibility', 1)"
            )
            db.execSQL(
                "INSERT INTO automation_config (profileId, automationId, configJson) " +
                    "VALUES ('study', 'app_visibility', '{\"primaryCategoryIds\":[\"study\"]}')"
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            AppDatabaseMigrations.MIGRATION_2_3
        )

        // The renamed profile is what proves nothing was recreated from defaults.
        db.query("SELECT name, sortOrder FROM profiles WHERE id = 'study'").use { c ->
            assertTrue("profile row was lost by the migration", c.moveToFirst())
            assertEquals("My Study Setup", c.getString(0))
            assertEquals(1, c.getInt(1))
        }
        db.query("SELECT configJson FROM automation_config WHERE profileId = 'study'").use { c ->
            assertTrue("automation config was lost by the migration", c.moveToFirst())
            assertEquals("{\"primaryCategoryIds\":[\"study\"]}", c.getString(0))
        }
        // New table exists and is usable, including the cascade back to profiles.
        db.query("SELECT count(*) FROM home_item").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
    }

    @Test
    fun migrate3To4_addsOverridesAndKeepsHomeLayout() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO profiles (id, name, iconResName, type, enabledAutomationIds, sortOrder) " +
                    "VALUES ('study', 'Study', 'study', 'Study', '', 0)"
            )
            db.execSQL(
                "INSERT INTO home_item (profileId, packageName, activityName, userSerial, position) " +
                    "VALUES ('study', 'com.example.notes', 'com.example.notes.Main', 0, 3)"
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            4,
            true,
            AppDatabaseMigrations.MIGRATION_3_4
        )

        // A pinned app at a non-default position is what proves the layout survived
        // rather than being recreated empty.
        db.query("SELECT packageName, position FROM home_item WHERE profileId = 'study'").use { c ->
            assertTrue("home layout was lost by the migration", c.moveToFirst())
            assertEquals("com.example.notes", c.getString(0))
            assertEquals(3, c.getInt(1))
        }
        // The new table takes a null customLabel, which is the only nullable column
        // in the schema and therefore the one a hand-written migration gets wrong.
        db.execSQL(
            "INSERT INTO app_override (packageName, activityName, userSerial, hidden, customLabel) " +
                "VALUES ('com.example.spam', 'com.example.spam.Main', 0, 1, NULL)"
        )
        db.query("SELECT hidden, customLabel FROM app_override").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
            assertTrue("customLabel should be nullable", c.isNull(1))
        }
    }

    @Test
    fun migrate4To5_addsCategoryOverrideAndKeepsHidesAndRenames() {
        helper.createDatabase(TEST_DB, 4).use { db ->
            db.execSQL(
                "INSERT INTO app_override (packageName, activityName, userSerial, hidden, customLabel) " +
                    "VALUES ('com.example.mail', 'com.example.mail.Main', 0, 1, 'Inbox')"
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            5,
            true,
            AppDatabaseMigrations.MIGRATION_4_5
        )

        // ALTER TABLE ADD COLUMN must not disturb the existing rows: a hide and a
        // rename the user already made have to survive the added column.
        db.query("SELECT hidden, customLabel, categoryId FROM app_override").use { c ->
            assertTrue("override row was lost by the migration", c.moveToFirst())
            assertEquals(1, c.getInt(0))
            assertEquals("Inbox", c.getString(1))
            assertTrue("categoryId should default to null", c.isNull(2))
        }
    }

    @Test
    fun migrate5To6_convertsTheFlatLayoutIntoPagedCells() {
        helper.createDatabase(TEST_DB, 5).use { db ->
            db.execSQL(
                "INSERT INTO profiles (id, name, iconResName, type, enabledAutomationIds, sortOrder) " +
                    "VALUES ('study', 'Study', 'study', 'Study', '', 0)"
            )
            // Position 0 is the top-left cell, 5 is the second cell of the second row,
            // and 20 is the first cell of the second page.
            listOf(0, 5, 20).forEach { position ->
                db.execSQL(
                    "INSERT INTO home_item (profileId, packageName, activityName, userSerial, position) " +
                        "VALUES ('study', 'com.example.a$position', 'com.example.a$position.Main', 0, $position)"
                )
            }
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            6,
            true,
            AppDatabaseMigrations.MIGRATION_5_6
        )

        // The layout is converted, not dropped. Getting this wrong empties the home
        // screen of everyone who upgrades, which no crash would announce.
        db.query(
            "SELECT packageName, page, cellX, cellY, spanX, spanY, kind, appWidgetId " +
                "FROM home_cell ORDER BY page, cellY, cellX"
        ).use { c ->
            assertTrue("home layout was lost by the migration", c.moveToFirst())
            assertEquals("com.example.a0", c.getString(0))
            assertEquals(0, c.getInt(1))
            assertEquals(0, c.getInt(2))
            assertEquals(0, c.getInt(3))
            assertEquals(1, c.getInt(4))
            assertEquals(1, c.getInt(5))
            assertEquals("app", c.getString(6))
            assertEquals(0, c.getInt(7))

            assertTrue(c.moveToNext())
            assertEquals("com.example.a5", c.getString(0))
            assertEquals(0, c.getInt(1))
            assertEquals(1, c.getInt(2))
            assertEquals(1, c.getInt(3))

            assertTrue(c.moveToNext())
            assertEquals("com.example.a20", c.getString(0))
            assertEquals(1, c.getInt(1))
            assertEquals(0, c.getInt(2))
            assertEquals(0, c.getInt(3))

            assertFalse("the migration invented rows", c.moveToNext())
        }

        // Two tables both claiming to be the home layout is how one of them goes stale.
        db.query(
            "SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name = 'home_item'"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("home_item should have been dropped", 0, c.getInt(0))
        }
    }

    @Test
    fun deletingAProfileTakesItsHomeCellsWithIt() {
        helper.createDatabase(TEST_DB, 5).use { db ->
            db.execSQL(
                "INSERT INTO profiles (id, name, iconResName, type, enabledAutomationIds, sortOrder) " +
                    "VALUES ('gaming', 'Gaming', 'gaming', 'Gaming', '', 0)"
            )
        }
        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            6,
            true,
            AppDatabaseMigrations.MIGRATION_5_6
        )
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL(
            "INSERT INTO home_cell (profileId, page, cellX, cellY, spanX, spanY, kind, " +
                "packageName, activityName, userSerial, appWidgetId) " +
                "VALUES ('gaming', 0, 0, 0, 1, 1, 'app', 'com.example.game', " +
                "'com.example.game.Main', 0, 0)"
        )

        db.execSQL("DELETE FROM profiles WHERE id = 'gaming'")

        db.query("SELECT count(*) FROM home_cell").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("home_cell outlived its profile — the cascade is not wired", 0, c.getInt(0))
        }
    }

    @Test
    fun deletingAProfileTakesItsLegacyHomeLayoutWithIt() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO profiles (id, name, iconResName, type, enabledAutomationIds, sortOrder) " +
                    "VALUES ('gaming', 'Gaming', 'gaming', 'Gaming', '', 0)"
            )
        }
        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            AppDatabaseMigrations.MIGRATION_2_3
        )
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL(
            "INSERT INTO home_item (profileId, packageName, activityName, userSerial, position) " +
                "VALUES ('gaming', 'com.example.game', 'com.example.game.Main', 0, 0)"
        )

        db.execSQL("DELETE FROM profiles WHERE id = 'gaming'")

        db.query("SELECT count(*) FROM home_item").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("home_item outlived its profile — the cascade is not wired", 0, c.getInt(0))
        }
    }
}

private const val TEST_DB = "migration-test.db"
