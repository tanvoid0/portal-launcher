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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanvoid0.portallauncher.R
import com.tanvoid0.portallauncher.automation.AutomationAvailability
import com.tanvoid0.portallauncher.automation.AutomationRegistry
import com.tanvoid0.portallauncher.automation.GreyscaleAutomation
import com.tanvoid0.portallauncher.data.AppCategory
import com.tanvoid0.portallauncher.data.AutomationIds
import com.tanvoid0.portallauncher.data.BrightnessMode
import com.tanvoid0.portallauncher.data.DndFilterLevel
import com.tanvoid0.portallauncher.data.PowerSaverIntensity
import com.tanvoid0.portallauncher.data.ProfileType
import com.tanvoid0.portallauncher.data.RefreshRateMode
import com.tanvoid0.portallauncher.ui.kit.ChoiceChips
import com.tanvoid0.portallauncher.ui.kit.MultiChoiceChips
import com.tanvoid0.portallauncher.ui.kit.PortalGroup
import com.tanvoid0.portallauncher.ui.kit.PortalRow
import com.tanvoid0.portallauncher.ui.kit.PortalScreen
import com.tanvoid0.portallauncher.ui.kit.SectionHeader
import com.tanvoid0.portallauncher.ui.kit.Spacing
import com.tanvoid0.portallauncher.ui.kit.labelRes

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
        // Every field autosaves as it changes (see ProfileEditViewModel), except the
        // name field, which debounces — flush it here so leaving right after typing
        // does not lose it to the timer.
        onPauseOrDispose { viewModel.flushPendingEdits() }
    }

    if (state.loading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    PortalScreen(
        title = stringResource(if (isNew) R.string.new_profile else R.string.edit_profile),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            // Resolved before the chip lambdas: their `label` callbacks are not
            // composable scopes, so stringResource cannot be called inside them.
            val typeLabels = ProfileType.entries.associateWith { stringResource(it.labelRes) }
            val categoryLabels = AppCategory.entries.associate {
                it.id to stringResource(it.labelRes)
            }

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::updateName,
                label = { Text(stringResource(R.string.profile_name)) },
                supportingText = { Text(stringResource(R.string.profile_name_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.gutter)
            )

            Column {
                SectionHeader(
                    stringResource(R.string.type),
                    Modifier.padding(horizontal = Spacing.gutter)
                )
                ChoiceChips(
                    options = ProfileType.entries,
                    selected = state.type,
                    onSelect = viewModel::updateType,
                    label = { typeLabels[it] ?: it.name },
                    modifier = Modifier.padding(horizontal = Spacing.gutter)
                )
            }

            Column {
                SectionHeader(
                    stringResource(R.string.apps_on_home),
                    Modifier.padding(horizontal = Spacing.gutter)
                )
                Text(
                    text = stringResource(R.string.apps_on_home_hint),
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
                    label = { id -> categoryLabels[id] ?: id },
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
                Text(stringResource(R.string.save))
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
    var showPowerSaverPicker by remember { mutableStateOf(false) }

    PortalGroup(title = stringResource(R.string.automations)) {
        AutomationRegistry.all.forEach { automation ->
            val availability = state.availability[automation.id]
                ?: AutomationAvailability.Ready
            val enabled = automation.id in state.enabledAutomationIds

            when (availability) {
                is AutomationAvailability.Unsupported -> PortalRow(
                    title = stringResource(automation.titleRes),
                    subtitle = availability.reason
                )
                is AutomationAvailability.NeedsPermission -> {
                    PortalRow(
                        title = stringResource(automation.titleRes),
                        subtitle = availability.explanation,
                        trailing = {
                            if (enabled) {
                                // Was on before the grant was revoked (e.g. the user
                                // pulled it in system settings). Let them turn it off;
                                // turning it back on needs the grant first, below.
                                Switch(
                                    checked = true,
                                    onCheckedChange = { viewModel.setAutomationEnabled(automation.id, false) }
                                )
                            } else {
                                TextButton(onClick = {
                                    try {
                                        context.startActivity(availability.settingsIntent)
                                    } catch (_: ActivityNotFoundException) {
                                        // Nowhere to send them on this device; the row
                                        // already explains what is missing.
                                    }
                                }) { Text(stringResource(R.string.grant)) }
                            }
                        }
                    )
                }
                is AutomationAvailability.Ready -> {
                    PortalRow(
                        title = stringResource(automation.titleRes),
                        subtitle = stringResource(automation.summaryRes),
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
                        AutomationIds.DISPLAY_COMFORT -> DisplayComfortSettings(state, viewModel)
                        AutomationIds.DND -> DndSettings(state, viewModel)
                        AutomationIds.POWER_SAVER -> PowerSaverSettings(
                            state = state,
                            viewModel = viewModel,
                            onPickApps = { showPowerSaverPicker = true }
                        )
                    }
                }
            }
        }
    }

    if (showNotificationPicker) {
        AppPickerDialog(
            title = stringResource(R.string.hold_back_from),
            viewModel = viewModel,
            selected = state.notification.blockedPackageNames.toSet(),
            onToggle = viewModel::toggleBlockedPackage,
            onDismiss = { showNotificationPicker = false }
        )
    }
    if (showBlockerPicker) {
        AppPickerDialog(
            title = stringResource(R.string.apps_to_block),
            viewModel = viewModel,
            selected = state.blocker.blockedPackageNames.toSet(),
            onToggle = viewModel::toggleBlockerPackage,
            onDismiss = { showBlockerPicker = false }
        )
    }
    if (showPowerSaverPicker) {
        AppPickerDialog(
            title = stringResource(R.string.apps_to_keep_running),
            viewModel = viewModel,
            selected = state.powerSaver.excludedPackages,
            onToggle = viewModel::toggleExcludedPackage,
            onDismiss = { showPowerSaverPicker = false }
        )
    }
}

@Composable
private fun BlockerSettings(state: ProfileEditState, onPickApps: () -> Unit) {
    val blockedCount = state.blocker.blockedPackageNames.size
    PortalRow(
        title = stringResource(R.string.apps_to_block),
        subtitle = if (blockedCount == 0) {
            stringResource(R.string.apps_to_block_empty)
        } else {
            pluralStringResource(R.plurals.apps_chosen, blockedCount, blockedCount)
        },
        onClick = onPickApps
    )
}

@Composable
private fun DisplayComfortSettings(state: ProfileEditState, viewModel: ProfileEditViewModel) {
    val comfort = state.displayComfort
    // Resolved before the chip lambdas: their `label` callbacks are not composable
    // scopes, so stringResource cannot be called inside them — see the type chips
    // above in ProfileEditScreen.
    val brightnessLabels = BrightnessMode.entries.associateWith {
        stringResource(if (it == BrightnessMode.Auto) R.string.display_brightness_auto else R.string.display_brightness_manual)
    }
    val refreshLabels = RefreshRateMode.entries.associateWith {
        stringResource(
            when (it) {
                RefreshRateMode.Auto -> R.string.refresh_rate_auto
                RefreshRateMode.Min -> R.string.refresh_rate_min
                RefreshRateMode.Max -> R.string.refresh_rate_max
                RefreshRateMode.Custom -> R.string.refresh_rate_custom
            }
        )
    }
    GroupPanel {
        Text(stringResource(R.string.display_brightness), style = MaterialTheme.typography.titleSmall)
        ChoiceChips(
            options = BrightnessMode.entries,
            selected = comfort.brightnessMode,
            onSelect = viewModel::setDisplayBrightnessMode,
            label = { brightnessLabels[it] ?: it.name },
            perRow = 2
        )
        if (comfort.brightnessMode == BrightnessMode.Manual) {
            Slider(
                value = comfort.brightnessPercent.toFloat(),
                valueRange = 0f..100f,
                onValueChange = viewModel::setDisplayBrightnessPercent
            )
        }
        Text(stringResource(R.string.display_refresh_rate), style = MaterialTheme.typography.titleSmall)
        ChoiceChips(
            options = RefreshRateMode.entries,
            selected = comfort.refreshRateMode,
            onSelect = viewModel::setDisplayRefreshRateMode,
            label = { refreshLabels[it] ?: it.name }
        )
        if (comfort.refreshRateMode == RefreshRateMode.Custom) {
            Slider(
                value = comfort.refreshRateHz,
                valueRange = 30f..144f,
                onValueChange = viewModel::setDisplayRefreshRateHz
            )
        }
    }
}

@Composable
private fun DndSettings(state: ProfileEditState, viewModel: ProfileEditViewModel) {
    val levelLabels = DndFilterLevel.entries.associateWith {
        stringResource(
            when (it) {
                DndFilterLevel.PriorityOnly -> R.string.dnd_priority_only
                DndFilterLevel.AlarmsOnly -> R.string.dnd_alarms_only
                DndFilterLevel.TotalSilence -> R.string.dnd_total_silence
            }
        )
    }
    GroupPanel {
        Text(stringResource(R.string.dnd_filter_level), style = MaterialTheme.typography.titleSmall)
        ChoiceChips(
            options = DndFilterLevel.entries,
            selected = state.dnd.filterLevel,
            onSelect = viewModel::setDndFilterLevel,
            label = { levelLabels[it] ?: it.name }
        )
    }
}

@Composable
private fun PowerSaverSettings(
    state: ProfileEditState,
    viewModel: ProfileEditViewModel,
    onPickApps: () -> Unit
) {
    val intensityLabels = PowerSaverIntensity.entries.associateWith {
        stringResource(if (it == PowerSaverIntensity.Standard) R.string.power_saver_standard else R.string.power_saver_ultra)
    }
    GroupPanel {
        Text(stringResource(R.string.intensity), style = MaterialTheme.typography.titleSmall)
        ChoiceChips(
            options = PowerSaverIntensity.entries,
            selected = state.powerSaver.intensity,
            onSelect = viewModel::setPowerSaverIntensity,
            label = { intensityLabels[it] ?: it.name },
            perRow = 2
        )
    }
    val excludedCount = state.powerSaver.excludedPackages.size
    PortalRow(
        title = stringResource(R.string.apps_to_keep_running),
        subtitle = if (excludedCount == 0) {
            stringResource(R.string.apps_to_keep_running_empty)
        } else {
            pluralStringResource(R.plurals.apps_chosen, excludedCount, excludedCount)
        },
        onClick = onPickApps
    )
}

@Composable
private fun GreyscaleSettings(state: ProfileEditState, viewModel: ProfileEditViewModel) {
    val context = LocalContext.current
    GroupPanel {
        Text(
            text = stringResource(R.string.intensity),
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
            Text(stringResource(R.string.system_greyscale_hint))
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
        title = stringResource(R.string.apps_to_hold_back),
        subtitle = if (blockedCount == 0) {
            stringResource(R.string.apps_to_hold_back_empty)
        } else {
            pluralStringResource(R.plurals.apps_chosen, blockedCount, blockedCount)
        },
        onClick = onPickApps
    )
    PortalRow(
        title = stringResource(R.string.dismiss_instead),
        // The difference is data loss; it has to be spelled out at the switch.
        subtitle = stringResource(R.string.dismiss_instead_warning),
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
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        },
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
