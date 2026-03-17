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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
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
