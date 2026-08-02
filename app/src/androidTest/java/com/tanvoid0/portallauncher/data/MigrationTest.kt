package com.tanvoid0.portallauncher.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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
    fun deletingAProfileTakesItsHomeLayoutWithIt() {
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
