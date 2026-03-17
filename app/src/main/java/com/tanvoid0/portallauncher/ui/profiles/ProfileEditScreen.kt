package com.tanvoid0.portallauncher.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanvoid0.portallauncher.data.ProfileType

@Composable
fun ProfileEditScreen(
    profileId: String?,
    modifier: Modifier = Modifier,
    onSaved: () -> Unit = {},
    viewModel: ProfileEditViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(profileId) {
        viewModel.loadProfile(profileId)
    }

    if (state.loading) {
        Column(
            modifier = modifier.padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::updateName,
            label = { Text("Profile name") },
            modifier = Modifier.fillMaxWidth()
        )
        Text("Type: ${state.type.name}")
        ProfileType.entries.forEach { type ->
            Button(
                onClick = { viewModel.updateType(type) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.type == type) "✓ ${type.name}" else type.name)
            }
        }
        Button(
            onClick = { viewModel.saveProfile(onSaved) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
        }
    }
}
