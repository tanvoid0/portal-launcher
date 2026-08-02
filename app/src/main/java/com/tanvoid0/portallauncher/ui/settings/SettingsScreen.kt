package com.tanvoid0.portallauncher.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanvoid0.portallauncher.ai.AiStatus

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val aiStatus by viewModel.aiStatus.collectAsStateWithLifecycle()
    val aiEnabled by viewModel.aiEnabled.collectAsStateWithLifecycle()
    val working by viewModel.working.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        )

        ListItem(
            headlineContent = { Text("App categories") },
            supportingContent = { Text("Customize how apps are grouped", style = MaterialTheme.typography.bodySmall) },
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Sort unknown apps with on-device AI") },
            supportingContent = {
                Text(
                    text = aiSummary(aiStatus, aiEnabled, working),
                    style = MaterialTheme.typography.bodySmall
                )
            },
            trailingContent = {
                Switch(
                    checked = aiEnabled,
                    onCheckedChange = viewModel::setAiEnabled,
                    // Nothing to switch on where the model cannot run. The row stays
                    // visible and says why, rather than vanishing and leaving the
                    // user wondering whether the app is missing a feature.
                    enabled = aiStatus != AiStatus.Unavailable && !working
                )
            },
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Backup & restore") },
            supportingContent = { Text("Export or restore profiles and preferences", style = MaterialTheme.typography.bodySmall) },
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text("Scheduler") },
            supportingContent = { Text("Switch profiles by time or automation", style = MaterialTheme.typography.bodySmall) },
            modifier = Modifier.padding(horizontal = 8.dp)
        )
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
