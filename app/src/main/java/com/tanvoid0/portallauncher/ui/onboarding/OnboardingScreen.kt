package com.tanvoid0.portallauncher.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tanvoid0.portallauncher.data.DefaultHomeStatus

/**
 * First-run screen. Its only job right now is getting the app set as the home app —
 * until that happens nothing else about a launcher can be evaluated.
 *
 * Per-automation permission requests (overlay, notification access, usage access)
 * belong here too, and land with the automations that need them in phase 6.
 */
@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {}
) {
    val context = LocalContext.current
    val isDefaultHome = remember { mutableStateOf(DefaultHomeStatus.isDefaultHome(context)) }

    // On API 26–28 the user makes the choice in a settings screen that returns no
    // result, and even the API 29+ role dialog can be dismissed. Re-check on resume
    // rather than trusting a callback that may never arrive.
    LifecycleResumeEffect(Unit) {
        isDefaultHome.value = DefaultHomeStatus.isDefaultHome(context)
        onPauseOrDispose { }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Portal Launcher",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Profiles change which apps your home screen shows, and what your " +
                "phone lets you do. Set Portal as your home app to start.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))

        if (isDefaultHome.value) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text("Portal is your home app", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Continue")
            }
        } else {
            Button(
                onClick = {
                    DefaultHomeStatus.requestIntent(context)?.let(context::startActivity)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Set as home app")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Skip for now")
            }
        }
    }
}
