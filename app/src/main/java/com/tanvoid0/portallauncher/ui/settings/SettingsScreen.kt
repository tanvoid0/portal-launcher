package com.tanvoid0.portallauncher.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.StringRes
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tanvoid0.portallauncher.data.ColorSource
import com.tanvoid0.portallauncher.data.DefaultHomeStatus
import com.tanvoid0.portallauncher.data.ThemeMode
import com.tanvoid0.portallauncher.ui.kit.ChoiceChips
import com.tanvoid0.portallauncher.ui.kit.IconShape
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
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.RadioButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import com.tanvoid0.portallauncher.data.GestureAction
import com.tanvoid0.portallauncher.data.LauncherGesture
import com.tanvoid0.portallauncher.R
import com.tanvoid0.portallauncher.ai.AiStatus
import com.tanvoid0.portallauncher.ui.assistant.AiAssistantSheet
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
    val assistantEnabled by viewModel.assistantEnabled.collectAsStateWithLifecycle()
    val working by viewModel.working.collectAsStateWithLifecycle()
    val backupMessage by viewModel.backupMessage.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val colorSource by viewModel.colorSource.collectAsStateWithLifecycle()
    val iconShape by viewModel.iconShape.collectAsStateWithLifecycle()
    val homeLabels by viewModel.homeLabels.collectAsStateWithLifecycle()
    val gestureActions by viewModel.gestureActions.collectAsStateWithLifecycle()
    // Which gesture's action picker is open, if any.
    var bindingGesture by remember { mutableStateOf<LauncherGesture?>(null) }
    var showAssistant by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val dynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // Re-read on resume: the role is granted in the system's own UI, which reports
    // nothing back.
    var isDefaultHome by remember { mutableStateOf(DefaultHomeStatus.isDefaultHome(context)) }
    LifecycleResumeEffect(Unit) {
        isDefaultHome = DefaultHomeStatus.isDefaultHome(context)
        onPauseOrDispose { }
    }

    // The system document picker, so Portal never decides where a file goes and needs no
    // storage permission at all.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::exportBackup) }
    // Restore replaces every profile the instant it runs, so the picked file only becomes
    // pending here — the actual restore call waits for the confirm dialog below.
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { pendingRestoreUri = it } }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text(stringResource(R.string.restore_confirm_title)) },
            text = { Text(stringResource(R.string.restore_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.importBackup(uri)
                    pendingRestoreUri = null
                }) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

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
                PortalRow(
                    title = stringResource(R.string.ai_assistant_title),
                    subtitle = stringResource(assistantSummaryRes(aiStatus, assistantEnabled, working)),
                    icon = Icons.Default.SmartToy,
                    onClick = if (assistantEnabled && aiStatus == AiStatus.Ready) {
                        { showAssistant = true }
                    } else {
                        null
                    },
                    trailing = {
                        Switch(
                            checked = assistantEnabled,
                            onCheckedChange = viewModel::setAssistantEnabled,
                            enabled = aiStatus != AiStatus.Unavailable && !working
                        )
                    }
                )
            }
            PortalGroup(title = stringResource(R.string.settings_appearance)) {
                PortalRow(
                    title = stringResource(R.string.wallpaper),
                    subtitle = stringResource(R.string.wallpaper_subtitle),
                    icon = Icons.Default.Wallpaper,
                    // The system picker, not one of our own: the wallpaper belongs to
                    // the device, and every launcher that tries to own it ends up with
                    // a worse gallery than the one already installed.
                    onClick = { context.startWallpaperPicker() }
                )
                ChoiceRow(title = stringResource(R.string.theme)) {
                    ChoiceChips(
                        options = ThemeMode.entries,
                        selected = themeMode,
                        onSelect = viewModel::setThemeMode,
                        label = { context.getString(it.labelRes) }
                    )
                }
                ChoiceRow(
                    title = stringResource(R.string.accent),
                    // Dynamic colour is an Android 12 feature. Saying so beats a
                    // control that silently does nothing on an older phone.
                    subtitle = if (dynamicColorSupported) {
                        null
                    } else {
                        stringResource(R.string.accent_wallpaper_unsupported)
                    }
                ) {
                    ChoiceChips(
                        options = if (dynamicColorSupported) {
                            ColorSource.entries
                        } else {
                            listOf(ColorSource.PORTAL)
                        },
                        selected = colorSource,
                        onSelect = viewModel::setColorSource,
                        label = { context.getString(it.labelRes) },
                        perRow = 2
                    )
                }
                ChoiceRow(title = stringResource(R.string.icon_shape)) {
                    ChoiceChips(
                        options = IconShape.entries,
                        selected = iconShape,
                        onSelect = viewModel::setIconShape,
                        label = { context.getString(it.labelRes) }
                    )
                }
                PortalRow(
                    title = stringResource(R.string.home_labels),
                    subtitle = stringResource(R.string.home_labels_subtitle),
                    icon = Icons.Default.TextFields,
                    trailing = {
                        Switch(checked = homeLabels, onCheckedChange = viewModel::setHomeLabels)
                    }
                )
            }
            PortalGroup(title = stringResource(R.string.settings_gestures)) {
                LauncherGesture.entries.forEach { gesture ->
                    val action = gestureActions[gesture] ?: GestureAction.None
                    PortalRow(
                        title = stringResource(gesture.labelRes),
                        subtitle = stringResource(action.labelRes),
                        icon = gesture.icon,
                        onClick = { bindingGesture = gesture }
                    )
                }
            }
            PortalGroup(title = stringResource(R.string.settings_system)) {
                // The way back if the user ever points home at another launcher. There
                // was none: setup asked once and settings never mentioned it again.
                PortalRow(
                    title = stringResource(R.string.default_home_app),
                    subtitle = stringResource(
                        if (isDefaultHome) {
                            R.string.default_home_app_on
                        } else {
                            R.string.default_home_app_off
                        }
                    ),
                    icon = Icons.Default.Home,
                    onClick = if (isDefaultHome) {
                        null
                    } else {
                        { context.requestDefaultHome() }
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

    if (showAssistant) {
        AiAssistantSheet(onDismiss = { showAssistant = false })
    }

    bindingGesture?.let { gesture ->
        GestureActionPicker(
            gesture = gesture,
            selected = gestureActions[gesture] ?: GestureAction.None,
            onSelect = { action ->
                viewModel.setGestureAction(gesture, action)
                bindingGesture = null
            },
            onDismiss = { bindingGesture = null }
        )
    }
}

/**
 * One gesture, every action it can be bound to.
 *
 * A dialog rather than the reference launcher's own full screen: Portal's action list
 * is eight entries with no sub-tabs. That screen exists there because it also lists
 * every installed app and every app shortcut, which Portal does not offer as gesture
 * targets.
 */
@Composable
private fun GestureActionPicker(
    gesture: LauncherGesture,
    selected: GestureAction,
    onSelect: (GestureAction) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(gesture.labelRes)) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .selectableGroup()
            ) {
                GestureAction.entries.forEach { action ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = action == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(action) }
                            )
                            .padding(vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        RadioButton(selected = action == selected, onClick = null)
                        Text(stringResource(action.labelRes))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@get:StringRes
private val LauncherGesture.labelRes: Int
    get() = when (this) {
        LauncherGesture.SwipeUp -> R.string.gesture_swipe_up
        LauncherGesture.SwipeDown -> R.string.gesture_swipe_down
        LauncherGesture.DoubleTap -> R.string.gesture_double_tap
    }

private val LauncherGesture.icon: ImageVector
    get() = when (this) {
        LauncherGesture.SwipeUp -> Icons.Default.KeyboardArrowUp
        LauncherGesture.SwipeDown -> Icons.Default.KeyboardArrowDown
        LauncherGesture.DoubleTap -> Icons.Default.TouchApp
    }

@get:StringRes
private val GestureAction.labelRes: Int
    get() = when (this) {
        GestureAction.None -> R.string.gesture_action_none
        GestureAction.AppDrawer -> R.string.gesture_action_app_drawer
        GestureAction.Search -> R.string.gesture_action_search
        GestureAction.NextProfile -> R.string.gesture_action_next_profile
        GestureAction.PreviousProfile -> R.string.gesture_action_previous_profile
        GestureAction.DefaultProfile -> R.string.gesture_action_default_profile
        GestureAction.Profiles -> R.string.gesture_action_profiles
        GestureAction.Settings -> R.string.gesture_action_settings
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

/**
 * Same shape as [aiSummaryRes], for the assistant row. No shared composable existed
 * for this status messaging -- [SettingsViewModel]'s AI section is the only other
 * place it appears -- so this stays a sibling function rather than a speculative
 * extraction, reusing [R.string.ai_downloading] where the wording is generic enough.
 */
private fun assistantSummaryRes(status: AiStatus, enabled: Boolean, working: Boolean): Int = when {
    working -> R.string.ai_downloading
    status == AiStatus.Unavailable -> R.string.ai_assistant_unavailable
    !enabled -> R.string.ai_assistant_off
    status == AiStatus.Downloadable -> R.string.ai_assistant_needs_download
    status == AiStatus.Downloading -> R.string.ai_downloading
    else -> R.string.ai_assistant_on
}

/**
 * A [PortalRow]-shaped slot for a control that is wider than a trailing widget — a
 * chip group, here. Same surface and padding, so a row of chips sits inside a
 * [PortalGroup] without looking like it escaped from another screen.
 */
@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }
}

@get:StringRes
private val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
    }

@get:StringRes
private val ColorSource.labelRes: Int
    get() = when (this) {
        ColorSource.PORTAL -> R.string.accent_portal
        ColorSource.WALLPAPER -> R.string.accent_wallpaper
    }

@get:StringRes
private val IconShape.labelRes: Int
    get() = when (this) {
        IconShape.System -> R.string.icon_shape_system
        IconShape.Circle -> R.string.icon_shape_circle
        IconShape.Squircle -> R.string.icon_shape_squircle
        IconShape.Rounded -> R.string.icon_shape_rounded
        IconShape.Teardrop -> R.string.icon_shape_teardrop
    }

/**
 * Both of these open somebody else's screen, and a device that ships without it must
 * not take the settings screen down with it.
 */
private fun Context.startWallpaperPicker() {
    runCatching {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SET_WALLPAPER),
                getString(R.string.wallpaper)
            )
        )
    }
}

private fun Context.requestDefaultHome() {
    runCatching {
        startActivity(DefaultHomeStatus.requestIntent(this) ?: DefaultHomeStatus.homeSettingsIntent())
    }
}
