package com.tanvoid0.portallauncher.ui.onboarding

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanvoid0.portallauncher.data.BuiltInProfiles
import com.tanvoid0.portallauncher.data.DefaultHomeStatus
import com.tanvoid0.portallauncher.data.ProfileEntity

/**
 * The steps, in order. The enum *is* the flow — there is no branching, so the
 * progress bar, the back button and the "last step" test all fall out of the
 * ordinal rather than being tracked separately.
 */
private enum class SetupStep { Welcome, HomeApp, Profile, Notifications }

/**
 * First-run setup. One decision per screen, and each one is checked against the
 * real system state rather than assumed to have worked.
 *
 * The two grants here are both made in *another app's* UI and neither reports back
 * usefully: the home-app role dialog can finish without drawing anything, and the
 * notification-access screen returns no result at all. So every step re-reads the
 * platform on resume, and the home step keeps a settings fallback for the case
 * where the role dialog never appeared — the old version fired that intent and
 * dropped the result, which is why the button could do nothing at all.
 *
 * Overlay and usage access are not asked for: nothing implements the automations
 * that need them yet, and a permission prompt with no feature behind it is how an
 * app teaches people to say no. They land with those automations in phase 6.
 */
@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    viewModel: SetupViewModel = viewModel(),
    onDone: () -> Unit = {}
) {
    val context = LocalContext.current
    val steps = remember { SetupStep.entries }
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[stepIndex]

    var isDefaultHome by remember { mutableStateOf(DefaultHomeStatus.isDefaultHome(context)) }
    var notificationAccess by remember { mutableStateOf(hasNotificationAccess(context)) }
    // Both grants happen outside this process. Re-reading on every resume is the
    // only reliable signal, and it costs one PackageManager query per return.
    LifecycleResumeEffect(Unit) {
        isDefaultHome = DefaultHomeStatus.isDefaultHome(context)
        notificationAccess = hasNotificationAccess(context)
        onPauseOrDispose { }
    }

    val finish = {
        viewModel.finish()
        onDone()
    }

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 24.dp)
        ) {
            StepProgress(
                stepIndex = stepIndex,
                stepCount = steps.size,
                modifier = Modifier.padding(top = 24.dp)
            )

            AnimatedContent(
                targetState = step,
                label = "setup step",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { current ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 32.dp)
                ) {
                    when (current) {
                        SetupStep.Welcome -> WelcomeStep()
                        SetupStep.HomeApp -> HomeAppStep(
                            isDefaultHome = isDefaultHome,
                            onGranted = { isDefaultHome = true }
                        )
                        SetupStep.Profile -> ProfileStep(viewModel)
                        SetupStep.Notifications -> NotificationsStep(granted = notificationAccess)
                    }
                }
            }

            StepButtons(
                isFirst = stepIndex == 0,
                isLast = stepIndex == steps.lastIndex,
                onBack = { stepIndex-- },
                onSkipAll = finish,
                onNext = { if (stepIndex == steps.lastIndex) finish() else stepIndex++ }
            )
        }
    }
}

@Composable
private fun StepProgress(stepIndex: Int, stepCount: Int, modifier: Modifier = Modifier) {
    // Animated so the bar reads as one continuous thing moving through the flow
    // rather than four unrelated states.
    val progress by animateFloatAsState(
        targetValue = (stepIndex + 1f) / stepCount,
        label = "setup progress"
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Step ${stepIndex + 1} of $stepCount",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun StepButtons(
    isFirst: Boolean,
    isLast: Boolean,
    onBack: () -> Unit,
    onSkipAll: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Edge-to-edge is mandatory from targetSdk 35, so the buttons sit under
            // the navigation bar without this.
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // No step blocks Continue. Everything here is either optional or granted
        // somewhere we do not control, and a wizard that traps someone on a screen
        // they cannot satisfy is worse than a launcher with one thing switched off.
        if (isFirst) {
            TextButton(onClick = onSkipAll, modifier = Modifier.weight(1f)) {
                Text("Skip setup")
            }
        } else {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text("Back")
            }
        }
        Button(onClick = onNext, modifier = Modifier.weight(1f)) {
            Text(if (isLast) "Finish" else "Continue")
        }
    }
}

@Composable
private fun StepHeader(title: String, body: String) {
    Text(text = title, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(12.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(32.dp))
}

@Composable
private fun WelcomeStep() {
    StepHeader(
        title = "Welcome to Portal",
        body = "Portal is a home screen that changes with what you are doing. " +
            "Four short steps and you are set up."
    )
    Highlight(
        Icons.Default.Home,
        "Your home screen",
        "Portal replaces the launcher you have now. You can switch back any time."
    )
    Spacer(Modifier.height(16.dp))
    Highlight(
        Icons.Default.Person,
        "Profiles",
        "Study, Focus, Driving and more — each one leads with the apps that fit it."
    )
    Spacer(Modifier.height(16.dp))
    Highlight(
        Icons.Default.NotificationsOff,
        "Fewer interruptions",
        "Optional. Portal can hold back notifications the active profile does not want."
    )
}

@Composable
private fun HomeAppStep(isDefaultHome: Boolean, onGranted: () -> Unit) {
    val context = LocalContext.current
    // True once a role request came back without the role. It means the dialog was
    // dismissed *or* never drew, which we cannot tell apart — so surface the
    // settings route instead of silently doing nothing, and let the user choose.
    var roleRequestFailed by remember { mutableStateOf(false) }
    val roleRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (DefaultHomeStatus.isDefaultHome(context)) onGranted() else roleRequestFailed = true
    }

    StepHeader(
        title = "Make Portal your home screen",
        body = "Nothing else in Portal has any effect until it is the app your home " +
            "button opens."
    )

    if (isDefaultHome) {
        GrantedCard("Portal is your home app")
        return
    }

    Button(
        onClick = {
            val intent = DefaultHomeStatus.requestIntent(context)
            when {
                // Null means the platform already considers us the home app.
                intent == null -> onGranted()
                // Below API 29 this is the settings screen, which returns no result;
                // the resume re-check is what picks the grant up there.
                else -> roleRequest.launch(intent)
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Set as home app")
    }

    if (roleRequestFailed) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = "No change yet. Some devices do not show that dialog — you can " +
                "pick Portal in system settings instead.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { context.startActivitySafely(DefaultHomeStatus.homeSettingsIntent()) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open home app settings")
        }
    }
}

@Composable
private fun ProfileStep(viewModel: SetupViewModel) {
    val profiles by viewModel.profiles.collectAsState()
    val activeId by viewModel.activeProfileId.collectAsState()
    // Nothing chosen yet resolves to the unfiltered profile, which is what the
    // launcher falls back to anyway — so the preselection matches what they would
    // get if they skipped this step.
    val selectedId = activeId ?: BuiltInProfiles.DEFAULT_ID

    StepHeader(
        title = "Pick a starting profile",
        body = "A profile decides which apps your home screen leads with. Everything " +
            "else stays in the app drawer, and you can switch or edit profiles later."
    )
    Column(
        modifier = Modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        profiles.forEach { profile ->
            ProfileOption(
                profile = profile,
                selected = profile.id == selectedId,
                onSelect = { viewModel.selectProfile(profile.id) }
            )
        }
    }
}

@Composable
private fun ProfileOption(
    profile: ProfileEntity,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onSelect,
        modifier = modifier
            .fillMaxWidth()
            // One selectable thing to a screen reader, not a card plus a radio button.
            .semantics(mergeDescendants = true) { role = Role.RadioButton },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The card carries the click; this is the state indicator only, which is
            // why it has no onClick of its own.
            RadioButton(selected = selected, onClick = null)
            Text(
                text = profile.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun NotificationsStep(granted: Boolean) {
    val context = LocalContext.current
    StepHeader(
        title = "Quiet the wrong notifications",
        body = "Optional. With notification access, Portal can hold back alerts from " +
            "apps the active profile does not include — so a Study profile stays " +
            "quiet. Portal never reads notification contents, and you can grant this " +
            "later from system settings."
    )
    if (granted) {
        GrantedCard("Notification access granted")
        return
    }
    Button(
        onClick = {
            context.startActivitySafely(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Grant notification access")
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Skip this and every other part of Portal still works.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun GrantedCard(text: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(text = text, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun Highlight(icon: ImageVector, title: String, body: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Whether the user has switched our [android.service.notification.NotificationListenerService] on. */
private fun hasNotificationAccess(context: Context): Boolean =
    context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

/**
 * Not every build ships every settings screen, and a missing one must not take the
 * setup flow down with it. Returns whether the screen opened.
 */
private fun Context.startActivitySafely(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
}
