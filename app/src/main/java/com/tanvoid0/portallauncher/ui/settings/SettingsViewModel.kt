package com.tanvoid0.portallauncher.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tanvoid0.portallauncher.PortalLauncherApplication
import com.tanvoid0.portallauncher.R
import com.tanvoid0.portallauncher.ai.AiStatus
import com.tanvoid0.portallauncher.data.AiCategoryEntity
import com.tanvoid0.portallauncher.data.AppCategorizer
import com.tanvoid0.portallauncher.data.BackupCodec
import com.tanvoid0.portallauncher.data.RestoreResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Owns the one optional feature on this screen: letting the on-device model place
 * the apps that neither the system category nor the keyword rules could.
 *
 * Nothing here runs unless the user switched it on, and nothing the launcher does
 * waits on it — the classification result lands in Room and the home screen picks
 * it up through its normal flow, whenever it arrives.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PortalLauncherApplication
    private val preferences = app.preferencesRepository
    private val aiCategoryDao = app.database.aiCategoryDao()
    private val categorizer = app.aiCategorizer

    private val _aiStatus = MutableStateFlow(AiStatus.Unavailable)
    val aiStatus: StateFlow<AiStatus> = _aiStatus.asStateFlow()

    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()

    /** Last backup or restore outcome, for the message the user sees. Cleared on read. */
    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = _backupMessage.asStateFlow()

    fun clearBackupMessage() { _backupMessage.value = null }

    val aiEnabled: StateFlow<Boolean> = preferences.aiCategoriesEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            _aiStatus.value = categorizer.status()
            // Opening settings is also how apps installed since the last run get
            // classified.
            // ponytail: no background job — new apps wait until the user next opens
            // this screen. Move the sweep behind LauncherApps.onPackageAdded if that
            // lag ever shows up in practice.
            if (preferences.aiCategoriesEnabled.first()) sweep()
        }
    }

    fun setAiEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setAiCategoriesEnabled(enabled)
            if (!enabled) {
                // Off means off: drop what the model decided so categories revert to
                // exactly what the rules alone produce. Re-enabling re-runs it.
                aiCategoryDao.clear()
                return@launch
            }
            if (_aiStatus.value == AiStatus.Downloadable) {
                _working.value = true
                _aiStatus.value = categorizer.download()
            }
            sweep()
        }
    }

    /**
     * Writes the whole configuration to [uri], which the user chose in the system
     * document picker — so this app never decides where a file lands.
     */
    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val text = BackupCodec.encode(app.backupRepository.export())
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(text.toByteArray())
                    } ?: error(app.getString(R.string.backup_cannot_write))
                }
            }
            _backupMessage.value = result.fold(
                onSuccess = { app.getString(R.string.backup_saved) },
                // The reason, not just "failed": a picker can hand back a read-only
                // location or a URI whose permission has already lapsed.
                onFailure = {
                    app.getString(
                        R.string.backup_failed,
                        it.message ?: app.getString(R.string.backup_unknown_error)
                    )
                }
            )
        }
    }

    /**
     * Replaces the configuration with the contents of [uri].
     *
     * The file came from a document picker, so it can be anything on the device. Every
     * failure mode gets its own message — a launcher that dies on a wrong pick cannot be
     * recovered from, because it *is* the recovery surface.
     */
    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val text = runCatching {
                withContext(Dispatchers.IO) {
                    app.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().decodeToString()
                    } ?: error("could not open the chosen file")
                }
            }.getOrElse {
                _backupMessage.value = app.getString(
                    R.string.restore_failed,
                    it.message ?: app.getString(R.string.restore_cannot_read)
                )
                return@launch
            }

            val backup = BackupCodec.decode(text).getOrElse {
                _backupMessage.value = app.getString(R.string.restore_not_portal)
                return@launch
            }

            _backupMessage.value = when (val result = app.backupRepository.restore(backup)) {
                is RestoreResult.Success -> app.resources.getQuantityString(
                    R.plurals.restore_success,
                    result.profileCount,
                    result.profileCount
                )
                is RestoreResult.TooNew -> app.getString(
                    R.string.restore_too_new,
                    result.fileVersion,
                    result.supported
                )
                RestoreResult.NotAPortalBackup -> app.getString(R.string.restore_not_portal)
            }
        }
    }

    /** Classifies only packages nothing else could place, and only once each. */
    private suspend fun sweep() {
        _working.value = true
        try {
            val alreadyAsked = aiCategoryDao.classifiedPackages().toSet()
            val unplaced = app.appRepository.apps.first().filter {
                it.packageName !in alreadyAsked && AppCategorizer.needsClassification(it)
            }
            val classified = categorizer.classify(unplaced)
            if (classified.isNotEmpty()) {
                aiCategoryDao.upsert(
                    classified.map { (packageName, category) ->
                        AiCategoryEntity(packageName, category.id)
                    }
                )
            }
        } finally {
            _working.value = false
        }
    }
}
