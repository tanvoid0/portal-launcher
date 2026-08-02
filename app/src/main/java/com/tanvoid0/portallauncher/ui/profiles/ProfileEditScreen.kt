package com.tanvoid0.portallauncher.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanvoid0.portallauncher.data.ProfileType
import com.tanvoid0.portallauncher.ui.kit.ChoiceChips
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

    if (state.loading) {
        // Centred, not pinned to the top-left corner as it was before.
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
                // Eight equal options, so eight chips — not the stack of full-width
                // buttons this used to be, which read as eight separate commands.
                ChoiceChips(
                    options = ProfileType.entries,
                    selected = state.type,
                    onSelect = viewModel::updateType,
                    label = { it.name },
                    modifier = Modifier.padding(horizontal = Spacing.gutter)
                )
            }

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
