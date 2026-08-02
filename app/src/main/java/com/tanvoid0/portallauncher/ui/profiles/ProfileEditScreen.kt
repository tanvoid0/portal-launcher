package com.tanvoid0.portallauncher.ui.profiles

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanvoid0.portallauncher.automation.AutomationAvailability
import com.tanvoid0.portallauncher.automation.AutomationRegistry
import com.tanvoid0.portallauncher.automation.GreyscaleAutomation
import com.tanvoid0.portallauncher.data.AppCategory
import com.tanvoid0.portallauncher.data.AutomationIds
import com.tanvoid0.portallauncher.data.ProfileType
import com.tanvoid0.portallauncher.ui.kit.ChoiceChips
import com.tanvoid0.portallauncher.ui.kit.MultiChoiceChips
import com.tanvoid0.portallauncher.ui.kit.PortalGroup
import com.tanvoid0.portallauncher.ui.kit.PortalRow
import com.tanvoid0.portallauncher.ui.kit.PortalScreen
import com.tanvoid0.portallauncher.ui.kit.SectionHeader
import com.tanvoid0.portallauncher.ui.kit.Spacing

@Composable
fun ProfileEditScreen(
    profileId: String?,
    modifier: Modifier = Modifier,
    onSaved: () -> Unit = {},
    viewModel: ProfileEditViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val isNew = profileId == null || profileId == "new"

    LaunchedEffect(profileId) {
        viewModel.loadProfile(profileId)
    }
    // Permission grants happen in another app's screen; coming back is when the
    // answer can have changed.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshAvailability()
        onPauseOrDispose { }
    }

    if (state.loading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    PortalScreen(
        title = if (isNew) "New profile" else "Edit profile",
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::updateName,
                label = { Text("Profile name") },
                supportingText = { Text("Leave empty to use the type's name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.gutter)
            )

            Column {
                SectionHeader("Type", Modifier.padding(horizontal = Spacing.gutter))
                ChoiceChips(
                    options = ProfileType.entries,
                    selected = state.type,
                    onSelect = viewModel::updateType,
                    label = { it.name },
                    modifier = Modifier.padding(horizontal = Spacing.gutter)
                )
            }

            Column {
                SectionHeader("Apps on home", Modifier.padding(horizontal = Spacing.gutter))
                Text(
                    text = "Categories this profile puts on the home screen. " +
                        "Nothing selected shows every app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = Spacing.gutter,
                        end = Spacing.gutter,
                        bottom = Spacing.sm
                    )
                )
                MultiChoiceChips(
                    options = AppCategory.entries.map { it.id },
                    selected = state.primaryCategories,
                    onToggle = viewModel::toggleCategory,
                    label = { id -> AppCategory.fromId(id).name },
                    modifier = Modifier.padding(horizontal = Spacing.gutter)
                )
            }

            AutomationsSection(state = state, viewModel = viewModel)

            Button(
                onClick = { viewModel.saveProfile(onSaved) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.gutter)
            ) {
                Text("Save")
            }
        }
    }
}

/**
 * One card per registered automation: a switch when it can run, the reason and a fix
 * when it cannot. An automation that is off still shows its settings — they persist,
 * so the user can set things up before switching it on.
 */
@Composable
private fun AutomationsSection(
    state: ProfileEditState,
    viewModel: ProfileEditViewModel
) {
    val context = LocalContext.current
    var showNotificationPicker by remember { mutableStateOf(false) }
    var showBlockerPicker by remember { mutableStateOf(false) }

    PortalGroup(title = "Automations") {
        AutomationRegistry.all.forEach { automation ->
            val availability = state.availability[automation.id]
                ?: AutomationAvailability.Ready
            val enabled = automation.id in state.enabledAutomationIds

            when (availability) {
                is AutomationAvailability.Unsupported -> PortalRow(
                    title = automation.title,
                    subtitle = availability.reason
                )
                is AutomationAvailability.NeedsPermission -> PortalRow(
                    title = automation.title,
                    subtitle = availability.explanation,
                    trailing = {
                        TextButton(onClick = {
                            try {
                                context.startActivity(availability.settingsIntent)
                            } catch (_: ActivityNotFoundException) {
                                // Nowhere to send them on this device; the row already
                                // explains what is missing.
                            }
                        }) { Text("Grant") }
                    }
                )
                is AutomationAvailability.Ready -> {
                    PortalRow(
                        title = automation.title,
                        subtitle = automation.summary,
                        trailing = {
                            Switch(
                                checked = enabled,
                                onCheckedChange = {
                                    viewModel.setAutomationEnabled(automation.id, it)
                                }
                            )
                        }
                    )
                    when (automation.id) {
                        AutomationIds.GREYSCALE -> GreyscaleSettings(state, viewModel)
                        AutomationIds.NOTIFICATION_FILTER -> NotificationSettings(
                            state = state,
                            viewModel = viewModel,
                            onPickApps = { showNotificationPicker = true }
                        )
                        AutomationIds.APP_BLOCKER -> BlockerSettings(
                            state = state,
                            onPickApps = { showBlockerPicker = true }
                        )
                    }
                }
            }
        }
    }

    if (showNotificationPicker) {
        AppPickerDialog(
            title = "Hold back notifications from",
            viewModel = viewModel,
            selected = state.notification.blockedPackageNames.toSet(),
            onToggle = viewModel::toggleBlockedPackage,
            onDismiss = { showNotificationPicker = false }
        )
    }
    if (showBlockerPicker) {
        AppPickerDialog(
            title = "Apps to block",
            viewModel = viewModel,
            selected = state.blocker.blockedPackageNames.toSet(),
            onToggle = viewModel::toggleBlockerPackage,
            onDismiss = { showBlockerPicker = false }
        )
    }
}

@Composable
private fun BlockerSettings(state: ProfileEditState, onPickApps: () -> Unit) {
    val blockedCount = state.blocker.blockedPackageNames.size
    PortalRow(
        title = "Apps to block",
        subtitle = if (blockedCount == 0) {
            "None chosen yet — pick the apps this profile should pause"
        } else {
            "$blockedCount chosen"
        },
        onClick = onPickApps
    )
}

@Composable
private fun GreyscaleSettings(state: ProfileEditState, viewModel: ProfileEditViewModel) {
    val context = LocalContext.current
    GroupPanel {
        Text(
            text = "Intensity",
            style = MaterialTheme.typography.titleSmall
        )
        Slider(
            value = state.greyscale.intensity,
            onValueChange = viewModel::setGreyscaleIntensity
        )
        TextButton(onClick = {
            try {
                context.startActivity(GreyscaleAutomation.systemGreyscaleIntent())
            } catch (_: ActivityNotFoundException) {
                // Developer options can be absent or policy-locked; nothing to open.
            }
        }) {
            Text("Whole device: Developer options → Simulate colour space")
        }
    }
}

@Composable
private fun NotificationSettings(
    state: ProfileEditState,
    viewModel: ProfileEditViewModel,
    onPickApps: () -> Unit
) {
    val blockedCount = state.notification.blockedPackageNames.size
    PortalRow(
        title = "Apps to hold back",
        subtitle = if (blockedCount == 0) {
            "None chosen yet — pick the apps this profile should quiet"
        } else {
            "$blockedCount chosen"
        },
        onClick = onPickApps
    )
    PortalRow(
        title = "Dismiss instead of snoozing",
        // The difference is data loss; it has to be spelled out at the switch.
        subtitle = "Snoozed notifications come back when the profile changes. " +
            "Dismissed ones are gone for good.",
        trailing = {
            Switch(
                checked = state.notification.cancelOnFilter,
                onCheckedChange = viewModel::setNotificationCancel
            )
        }
    )
}

@Composable
private fun AppPickerDialog(
    title: String,
    viewModel: ProfileEditViewModel,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val apps by viewModel.packages.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text(title) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                items(apps, key = { it.packageName }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = app.packageName in selected,
                                role = Role.Checkbox,
                                onValueChange = { onToggle(app.packageName) }
                            )
                            .padding(vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        // State comes from the row's toggleable; the checkbox only shows it.
                        Checkbox(checked = app.packageName in selected, onCheckedChange = null)
                        Text(text = app.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    )
}

/** Inline settings row under an automation's switch, matching [PortalRow]'s surface. */
@Composable
private fun GroupPanel(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            content()
        }
    }
}
