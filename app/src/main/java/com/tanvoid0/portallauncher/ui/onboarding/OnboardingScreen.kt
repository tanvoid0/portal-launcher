package com.tanvoid0.portallauncher.ui.onboarding

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun OnboardingScreen(modifier: Modifier = Modifier) {
    Text(
        modifier = modifier,
        text = "Onboarding – launcher default, overlay, notification access"
    )
}
