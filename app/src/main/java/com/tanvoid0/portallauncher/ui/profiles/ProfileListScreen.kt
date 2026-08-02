package com.tanvoid0.portallauncher.ui.profiles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanvoid0.portallauncher.ui.kit.EmptyState
import com.tanvoid0.portallauncher.ui.kit.PortalGroup
import com.tanvoid0.portallauncher.ui.kit.PortalScreen
import com.tanvoid0.portallauncher.ui.kit.SelectableRow
import com.tanvoid0.portallauncher.ui.kit.Spacing

@Composable
fun ProfileListScreen(
    modifier: Modifier = Modifier,
    onNavigateToEdit: (String) -> Unit = {},
    viewModel: ProfileListViewModel = viewModel()
) {
    val profiles by viewModel.profiles.collectAsState()
    val activeId by viewModel.activeProfileId.collectAsState()

    PortalScreen(
        title = "Profiles",
        subtitle = "The active profile decides which apps your home screen leads with.",
        modifier = modifier,
        floatingAction = if (profiles.isEmpty()) {
            null
        } else {
            {
                FloatingActionButton(onClick = { onNavigateToEdit("new") }) {
                    Icon(Icons.Default.Add, contentDescription = "Add profile")
                }
            }
        }
    ) {
        if (profiles.isEmpty()) {
            EmptyState(
                title = "No profiles yet",
                body = "Create a profile to switch between Study, Social, Productivity, " +
                    "Gaming, and more.",
                icon = Icons.Default.Person,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                action = {
                    Button(onClick = { onNavigateToEdit("new") }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(Spacing.sm))
                        Text("Create profile")
                    }
                }
            )
        } else {
            // ponytail: plain scroll, not lazy — a profile list is a handful of rows and
            // PortalGroup needs its children in one column to round the ends. Move to a
            // LazyColumn of groups if profiles ever become unbounded.
            Box(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 96.dp)
                ) {
                    PortalGroup(modifier = Modifier.selectableGroup()) {
                        profiles.forEach { profile ->
                            // Tapping the row activates the profile; the pencil edits it.
                            // Previously the row edited and the radio button activated,
                            // which put two unrelated actions on one row with no label
                            // saying which was which.
                            SelectableRow(
                                title = profile.name,
                                subtitle = profile.type,
                                selected = profile.id == activeId,
                                onSelect = { viewModel.setActiveProfile(profile.id) },
                                trailing = {
                                    IconButton(onClick = { onNavigateToEdit(profile.id) }) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit ${profile.name}"
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
