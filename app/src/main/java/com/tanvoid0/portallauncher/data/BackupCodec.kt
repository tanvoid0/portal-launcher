package com.tanvoid0.portallauncher.data

import kotlinx.serialization.Required
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Everything the user built, in one file.
 *
 * Deliberately **not** a database copy. A .db file is opaque, tied to a schema version,
 * and useless to anyone reading it — while a launcher configuration is exactly the kind
 * of thing people want to inspect, hand-edit and keep in a note. JSON also means a
 * backup taken today still restores after the schema moves on, which a raw database
 * cannot promise.
 *
 * The app list itself is not included: what is installed belongs to the device, and
 * restoring onto a phone without those apps is normal — [BackupRestore] drops references
 * to anything missing rather than failing.
 */
@Serializable
data class LauncherBackup(
    /**
     * Bumped only when a reader must reject the file. Adding a field does not need it:
     * `ignoreUnknownKeys` plus defaults means an older build reads a newer backup and
     * simply does not see what it cannot use.
     *
     * `@Required` even though it has a Kotlin default, because the default would
     * otherwise make *any* JSON object a valid backup — `{"volume":11}` decoded to an
     * empty one, and restoring that silently deleted every profile the user had. This is
     * the field that identifies the file as ours, so it has to be present in the file.
     */
    @Required
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val profiles: List<BackupProfile> = emptyList(),
    val overrides: List<AppOverrideEntity> = emptyList(),
    val activeProfileId: String? = null,
    val customLayoutProfileIds: List<String> = emptyList()
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class BackupProfile(
    val id: String,
    val name: String,
    val iconResName: String,
    val type: String,
    val enabledAutomationIds: List<String> = emptyList(),
    val sortOrder: Int = 0,
    /** Automation id to its config JSON, exactly as stored. */
    val automationConfigs: Map<String, String> = emptyMap(),
    /** Pinned home items, in order. Empty means the profile uses its category default. */
    val homeItems: List<BackupHomeItem> = emptyList()
)

@Serializable
data class BackupHomeItem(
    val packageName: String,
    val activityName: String,
    val userSerial: Long,
    val position: Int
)

/** Why a restore was refused. Shown to the user, so each case has to be actionable. */
sealed interface RestoreResult {
    data class Success(val profileCount: Int, val overrideCount: Int) : RestoreResult
    data object NotAPortalBackup : RestoreResult
    data class TooNew(val fileVersion: Int, val supported: Int) : RestoreResult
}

/**
 * Reads a backup file's bytes into a [LauncherBackup], or explains why it cannot.
 *
 * Validation is not optional here even though the file is nominally ours: the user picks
 * it with a document picker, so it can be any file on the device or in cloud storage, and
 * a launcher that crashes on a wrong pick is a launcher that cannot be recovered.
 */
object BackupCodec {

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun encode(backup: LauncherBackup): String = json.encodeToString(backup)

    fun decode(text: String): Result<LauncherBackup> = try {
        val backup = json.decodeFromString<LauncherBackup>(text)
        // A file that parses as JSON but carries no version is not one of ours; every
        // backup we write has one, and guessing would restore nonsense.
        if (backup.schemaVersion <= 0) {
            Result.failure(IllegalArgumentException("missing schemaVersion"))
        } else {
            Result.success(backup)
        }
    } catch (e: SerializationException) {
        Result.failure(e)
    } catch (e: IllegalArgumentException) {
        Result.failure(e)
    }

    /** Checks the version before anything is written. */
    fun validate(backup: LauncherBackup): RestoreResult? =
        if (backup.schemaVersion > LauncherBackup.CURRENT_SCHEMA_VERSION) {
            RestoreResult.TooNew(backup.schemaVersion, LauncherBackup.CURRENT_SCHEMA_VERSION)
        } else {
            null
        }
}
