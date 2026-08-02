package com.tanvoid0.portallauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A restore is the recovery path, so it has to fail *legibly* on anything it is handed.
 * The file comes from a system document picker, which means it can be any file on the
 * device — and a launcher that crashes on a wrong pick has destroyed the only surface
 * the user could have recovered from.
 */
class BackupCodecTest {

    private val backup = LauncherBackup(
        profiles = listOf(
            BackupProfile(
                id = "study",
                name = "Study",
                iconResName = "study",
                type = "Study",
                enabledAutomationIds = listOf("greyscale", "app_visibility"),
                sortOrder = 2,
                automationConfigs = mapOf("greyscale" to """{"enabled":true,"intensity":1.0}"""),
                homeItems = listOf(BackupHomeItem("com.example.notes", "com.example.notes.Main", 0, 0))
            )
        ),
        overrides = listOf(
            AppOverrideEntity("com.example.spam", "com.example.spam.Main", 0, hidden = true),
            AppOverrideEntity(
                "com.example.mail",
                "com.example.mail.Main",
                0,
                customLabel = "Inbox",
                categoryId = "productivity"
            )
        ),
        activeProfileId = "study",
        customLayoutProfileIds = listOf("study")
    )

    @Test
    fun `a backup round-trips whole`() {
        assertEquals(backup, BackupCodec.decode(BackupCodec.encode(backup)).getOrThrow())
    }

    @Test
    fun `arbitrary files are rejected rather than half-read`() {
        assertTrue(BackupCodec.decode("").isFailure)
        assertTrue(BackupCodec.decode("not json at all").isFailure)
        // Valid JSON, wrong shape: a settings file from some other app.
        assertTrue(BackupCodec.decode("""{"volume":11}""").isFailure)
        // Parses, but carries no version, so it is not one of ours.
        assertTrue(BackupCodec.decode("""{"schemaVersion":0}""").isFailure)
    }

    @Test
    fun `a backup from a newer build is refused with both version numbers`() {
        val fromFuture = backup.copy(schemaVersion = LauncherBackup.CURRENT_SCHEMA_VERSION + 1)
        val result = BackupCodec.validate(fromFuture)
        assertTrue(result is RestoreResult.TooNew)
        result as RestoreResult.TooNew
        assertEquals(LauncherBackup.CURRENT_SCHEMA_VERSION + 1, result.fileVersion)
        assertEquals(LauncherBackup.CURRENT_SCHEMA_VERSION, result.supported)
    }

    @Test
    fun `the current version validates`() {
        assertNull(BackupCodec.validate(backup))
    }

    @Test
    fun `a field added by a newer build does not stop an older one reading the rest`() {
        val withExtra = """
            {"schemaVersion":1,"profiles":[],"overrides":[],"somethingNewer":{"a":1}}
        """.trimIndent()
        assertEquals(1, BackupCodec.decode(withExtra).getOrThrow().schemaVersion)
    }

    @Test
    fun `an empty backup is valid, because deleting every profile is allowed`() {
        val empty = LauncherBackup()
        assertEquals(empty, BackupCodec.decode(BackupCodec.encode(empty)).getOrThrow())
        assertNull(BackupCodec.validate(empty))
    }
}
