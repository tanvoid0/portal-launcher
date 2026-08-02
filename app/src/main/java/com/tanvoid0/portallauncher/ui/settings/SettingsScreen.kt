package com.tanvoid0.portallauncher.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanvoid0.portallauncher.ai.AiStatus
import com.tanvoid0.portallauncher.ui.kit.PortalGroup
import com.tanvoid0.portallauncher.ui.kit.PortalRow
import com.tanvoid0.portallauncher.ui.kit.PortalScreen
import com.tanvoid0.portallauncher.ui.kit.Spacing

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onOpenHiddenApps: () -> Unit = {},
    onOpenSchedule: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val aiStatus by viewModel.aiStatus.collectAsStateWithLifecycle()
    val aiEnabled by viewModel.aiEnabled.collectAsStateWithLifecycle()
    val working by viewModel.working.collectAsStateWithLifecycle()
    val backupMessage by viewModel.backupMessage.collectAsStateWithLifecycle()

    // The system document picker, so Portal never decides where a file goes and needs no
    // storage permission at all.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::exportBackup) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importBackup) }

    backupMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearBackupMessage,
            confirmButton = {
                TextButton(onClick = viewModel::clearBackupMessage) { Text("OK") }
            },
            text = { Text(message) }
        )
    }

    PortalScreen(title = "Settings", modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            PortalGroup(title = "Apps") {
                // No onClick, so it exposes no click action to a screen reader either.
                // Changing a category lives on the app itself rather than behind a
                // settings screen listing every app twice; this row is here so the
                // feature is findable, not so it does something.
                PortalRow(
                    title = "App categories",
                    subtitle = "Long-press any app and choose Category to move it",
                    icon = Icons.Default.Category
                )
                PortalRow(
                    title = "Hidden apps",
                    subtitle = "Bring back apps you hid from the home screen and drawer",
                    icon = Icons.Default.VisibilityOff,
                    onClick = onOpenHiddenApps
                )
                PortalRow(
                    title = "Sort unknown apps with on-device AI",
                    subtitle = aiSummary(aiStatus, aiEnabled, working),
                    icon = Icons.Default.AutoAwesome,
                    trailing = {
                        Switch(
                            checked = aiEnabled,
                            onCheckedChange = viewModel::setAiEnabled,
                            // Nothing to switch on where the model cannot run. The row
                            // stays visible and says why, rather than vanishing and
                            // leaving the user wondering whether the app is missing a
                            // feature.
                            enabled = aiStatus != AiStatus.Unavailable && !working
                        )
                    }
                )
            }
            PortalGroup(title = "Portal") {
                PortalRow(
                    title = "Back up",
                    subtitle = "Save profiles, layouts and app overrides to a file",
                    icon = Icons.Default.Backup,
                    onClick = { exportLauncher.launch("portal-launcher-backup.json") }
                )
                PortalRow(
                    title = "Restore",
                    // Says what it does before they pick the file, not after.
                    subtitle = "Replaces your current profiles with a backup file",
                    icon = Icons.Default.Restore,
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }
                )
                PortalRow(
                    title = "Schedule",
                    subtitle = "Switch profiles by time of day",
                    icon = Icons.Default.Schedule,
                    onClick = onOpenSchedule
                )
            }
        }
    }
}

private fun aiSummary(status: AiStatus, enabled: Boolean, working: Boolean): String = when {
    working -> "Sorting apps…"
    status == AiStatus.Unavailable ->
        "Not supported on this device. Categories use the built-in rules."
    status == AiStatus.Downloading -> "Downloading the on-device model…"
    status == AiStatus.Downloadable && enabled -> "Needs a one-time model download."
    status == AiStatus.Downloadable -> "Available after a one-time model download."
    enabled -> "On. Runs on-device; apps and categories never leave the phone."
    else -> "Off. Apps the built-in rules can't place stay in Other."
}
