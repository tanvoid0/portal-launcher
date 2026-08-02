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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanvoid0.portallauncher.R
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
                TextButton(onClick = viewModel::clearBackupMessage) {
                    Text(stringResource(R.string.ok))
                }
            },
            text = { Text(message) }
        )
    }

    PortalScreen(title = stringResource(R.string.nav_settings), modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            PortalGroup(title = stringResource(R.string.settings_apps)) {
                // No onClick, so it exposes no click action to a screen reader either.
                // Changing a category lives on the app itself rather than behind a
                // settings screen listing every app twice; this row is here so the
                // feature is findable, not so it does something.
                PortalRow(
                    title = stringResource(R.string.app_categories),
                    subtitle = stringResource(R.string.app_categories_subtitle),
                    icon = Icons.Default.Category
                )
                PortalRow(
                    title = stringResource(R.string.hidden_apps),
                    subtitle = stringResource(R.string.hidden_apps_row_subtitle),
                    icon = Icons.Default.VisibilityOff,
                    onClick = onOpenHiddenApps
                )
                PortalRow(
                    title = stringResource(R.string.ai_sort_title),
                    subtitle = stringResource(aiSummaryRes(aiStatus, aiEnabled, working)),
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
            PortalGroup(title = stringResource(R.string.settings_backup)) {
                PortalRow(
                    title = stringResource(R.string.back_up),
                    subtitle = stringResource(R.string.back_up_subtitle),
                    icon = Icons.Default.Backup,
                    onClick = { exportLauncher.launch("portal-launcher-backup.json") }
                )
                PortalRow(
                    title = stringResource(R.string.restore),
                    // Says what it does before they pick the file, not after.
                    subtitle = stringResource(R.string.restore_subtitle),
                    icon = Icons.Default.Restore,
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }
                )
            }
            PortalGroup(title = stringResource(R.string.settings_schedule)) {
                PortalRow(
                    title = stringResource(R.string.schedule),
                    subtitle = stringResource(R.string.schedule_row_subtitle),
                    icon = Icons.Default.Schedule,
                    onClick = onOpenSchedule
                )
            }
        }
    }
}

private fun aiSummaryRes(status: AiStatus, enabled: Boolean, working: Boolean): Int = when {
    working -> R.string.ai_working
    status == AiStatus.Unavailable -> R.string.ai_unavailable
    status == AiStatus.Downloading -> R.string.ai_downloading
    status == AiStatus.Downloadable && enabled -> R.string.ai_needs_download
    status == AiStatus.Downloadable -> R.string.ai_downloadable
    enabled -> R.string.ai_on
    else -> R.string.ai_off
}
