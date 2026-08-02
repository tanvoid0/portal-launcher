package com.tanvoid0.portallauncher.ui.settings

import android.app.Application
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanvoid0.portallauncher.PortalLauncherApplication
import com.tanvoid0.portallauncher.R
import com.tanvoid0.portallauncher.automation.ProfileScheduler
import com.tanvoid0.portallauncher.data.ConfigCodec
import com.tanvoid0.portallauncher.data.ProfileEntity
import com.tanvoid0.portallauncher.data.ScheduleSlot
import com.tanvoid0.portallauncher.data.SchedulerConfig
import com.tanvoid0.portallauncher.ui.kit.ChoiceChips
import com.tanvoid0.portallauncher.ui.kit.EmptyState
import com.tanvoid0.portallauncher.ui.kit.PortalGroup
import com.tanvoid0.portallauncher.ui.kit.PortalRow
import com.tanvoid0.portallauncher.ui.kit.PortalScreen
import com.tanvoid0.portallauncher.ui.kit.Spacing
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PortalLauncherApplication

    val profiles: StateFlow<List<ProfileEntity>> = app.profileRepository.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val slots: StateFlow<List<ScheduleSlot>> = app.preferencesRepository.scheduleJson
        .map { ConfigCodec.decodeOr(it, SchedulerConfig()).slots }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addSlot(slot: ScheduleSlot) = write(slots.value + slot)

    fun removeSlot(slot: ScheduleSlot) = write(slots.value - slot)

    /**
     * Persists and re-arms in one step, so the alarm can never describe a timetable
     * other than the stored one.
     */
    private fun write(slots: List<ScheduleSlot>) {
        viewModelScope.launch {
            app.preferencesRepository.setScheduleJson(
                if (slots.isEmpty()) null else ConfigCodec.encode(SchedulerConfig(slots = slots))
            )
            ProfileScheduler(app).sync()
        }
    }
}

@Composable
fun ScheduleScreen(
    modifier: Modifier = Modifier,
    viewModel: ScheduleViewModel = viewModel()
) {
    val slots by viewModel.slots.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    var showAdd by rememberSaveable { mutableStateOf(false) }

    PortalScreen(
        title = stringResource(R.string.schedule),
        subtitle = stringResource(R.string.schedule_subtitle),
        modifier = modifier,
        floatingAction = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_slot))
            }
        }
    ) {
        if (slots.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.no_schedule_yet),
                body = stringResource(R.string.no_schedule_body),
                icon = Icons.Default.Schedule,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xxl)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = Spacing.xxl)
            ) {
                PortalGroup {
                    slots.forEach { slot ->
                        PortalRow(
                            title = "${formatTime(slot.startTimeMinutes)} – " +
                                formatTime(slot.endTimeMinutes),
                            subtitle = profiles.find { it.id == slot.profileId }?.name
                                ?: stringResource(R.string.slot_deleted_profile),
                            trailing = {
                                IconButton(onClick = { viewModel.removeSlot(slot) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.remove_slot)
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddSlotDialog(
            profiles = profiles,
            onAdd = { viewModel.addSlot(it); showAdd = false },
            onDismiss = { showAdd = false }
        )
    }
}

@Composable
private fun AddSlotDialog(
    profiles: List<ProfileEntity>,
    onAdd: (ScheduleSlot) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var start by remember { mutableIntStateOf(9 * 60) }
    var end by remember { mutableIntStateOf(17 * 60) }
    var profileId by remember { mutableStateOf(profiles.firstOrNull()?.id) }

    fun pickTime(current: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onPicked(hour * 60 + minute) },
            current / 60,
            current % 60,
            DateFormat.is24HourFormat(context)
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_slot)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedButton(
                        onClick = { pickTime(start) { start = it } },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.slot_from, formatTime(start))) }
                    OutlinedButton(
                        onClick = { pickTime(end) { end = it } },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.slot_to, formatTime(end))) }
                }
                if (end < start) {
                    Text(
                        text = stringResource(R.string.ends_next_day),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = stringResource(R.string.switch_to),
                    style = MaterialTheme.typography.titleSmall
                )
                profileId?.let { selected ->
                    ChoiceChips(
                        options = profiles.map { it.id },
                        selected = selected,
                        onSelect = { profileId = it },
                        label = { id -> profiles.find { it.id == id }?.name ?: id },
                        perRow = 2
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    profileId?.let {
                        onAdd(ScheduleSlot(startTimeMinutes = start, endTimeMinutes = end, profileId = it))
                    }
                },
                // A slot that starts and ends at the same minute covers nothing.
                enabled = profileId != null && start != end
            ) { Text(stringResource(R.string.add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

private fun formatTime(minutes: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", minutes / 60, minutes % 60)
