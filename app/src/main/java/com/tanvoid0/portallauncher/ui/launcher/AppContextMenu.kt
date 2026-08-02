package com.tanvoid0.portallauncher.ui.launcher

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.selection.selectableGroup
import com.tanvoid0.portallauncher.data.AppCategory
import com.tanvoid0.portallauncher.data.LaunchableApp
import com.tanvoid0.portallauncher.ui.kit.PortalGroup
import com.tanvoid0.portallauncher.ui.kit.PortalRow
import com.tanvoid0.portallauncher.ui.kit.SelectableRow
import com.tanvoid0.portallauncher.ui.kit.Spacing

/**
 * What long-pressing an app offers. One sheet used from both the home grid and the
 * drawer, so the actions cannot drift apart between them.
 *
 * Uninstall is only shown when it would actually work — see
 * [com.tanvoid0.portallauncher.data.AppRepository.canUninstall]. An entry that
 * silently does nothing for system and work-profile apps is worse than no entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContextMenu(
    app: LaunchableApp,
    viewModel: LauncherViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()
    var renaming by remember { mutableStateOf(false) }
    var choosingCategory by remember { mutableStateOf(false) }
    val onHome = remember(app.key) { viewModel.isOnHomeScreen(app) }
    val resolvedCategory = viewModel.categoryOf(app)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = Spacing.lg)
        ) {
            Text(
                text = app.displayLabel,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md)
            )
            // Renaming shows the app's real name, so someone who forgot what they
            // renamed can still tell what they are looking at.
            if (app.customLabel != null) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xl)
                )
            }
            Spacer(Modifier.height(Spacing.sm))

            if (renaming) {
                RenameRow(
                    initial = app.displayLabel,
                    originalLabel = app.label,
                    onCancel = { renaming = false },
                    onConfirm = { newLabel ->
                        viewModel.rename(app, newLabel)
                        onDismiss()
                    }
                )
                return@Column
            }

            if (choosingCategory) {
                CategoryPicker(
                    current = app.categoryOverride,
                    automatic = resolvedCategory,
                    onPick = { picked ->
                        viewModel.setCategory(app, picked)
                        onDismiss()
                    },
                    onCancel = { choosingCategory = false }
                )
                return@Column
            }

            PortalGroup {
                PortalRow(
                    icon = Icons.Default.PushPin,
                    title = if (onHome) "Remove from home" else "Add to home",
                    onClick = {
                        viewModel.toggleOnHome(app)
                        onDismiss()
                    }
                )
                PortalRow(
                    icon = Icons.Default.Edit,
                    title = "Rename",
                    onClick = { renaming = true }
                )
                PortalRow(
                    icon = Icons.Default.Category,
                    title = "Category",
                    // Says what is in effect *and* where it came from, so the user can
                    // tell an automatic guess from their own choice before changing it.
                    subtitle = app.categoryOverride
                        ?.let { "${it.displayName()} — set by you" }
                        ?: "${resolvedCategory.displayName()} — chosen automatically",
                    onClick = { choosingCategory = true }
                )
                PortalRow(
                    icon = Icons.Default.VisibilityOff,
                    title = "Hide app",
                    subtitle = "Hidden everywhere until you unhide it in Settings",
                    onClick = {
                        viewModel.setHidden(app, true)
                        onDismiss()
                    }
                )
                PortalRow(
                    icon = Icons.Default.Info,
                    title = "App info",
                    onClick = {
                        viewModel.openAppInfo(app)
                        onDismiss()
                    }
                )
                if (viewModel.canUninstall(app)) {
                    PortalRow(
                        icon = Icons.Default.Delete,
                        title = "Uninstall",
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_DELETE,
                                    Uri.fromParts("package", app.packageName, null)
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RenameRow(
    initial: String,
    originalLabel: String,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    Column(modifier = Modifier.padding(Spacing.xl)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            // Clearing the field is how you undo a rename; saying so beats a third button.
            text = "Leave empty to restore \"$originalLabel\"",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Button(onClick = { onConfirm(text) }) { Text("Save") }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

/**
 * Reassigns an app's category by hand.
 *
 * "Choose automatically" is a first-class option rather than a missing selection: the
 * user needs a way back to the automatic result, and null is not the same as any
 * particular category.
 */
@Composable
private fun CategoryPicker(
    current: AppCategory?,
    automatic: AppCategory,
    onPick: (AppCategory?) -> Unit,
    onCancel: () -> Unit
) {
    Column {
        PortalGroup(modifier = Modifier.selectableGroup()) {
            SelectableRow(
                title = "Choose automatically",
                subtitle = "Currently ${automatic.displayName()}",
                selected = current == null,
                onSelect = { onPick(null) }
            )
            AppCategory.entries.forEach { category ->
                SelectableRow(
                    title = category.displayName(),
                    selected = current == category,
                    onSelect = { onPick(category) }
                )
            }
        }
        TextButton(
            onClick = onCancel,
            modifier = Modifier.padding(horizontal = Spacing.gutter, vertical = Spacing.sm)
        ) {
            Text("Cancel")
        }
    }
}
