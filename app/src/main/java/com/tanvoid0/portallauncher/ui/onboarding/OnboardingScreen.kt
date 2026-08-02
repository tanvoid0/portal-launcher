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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanvoid0.portallauncher.R
import com.tanvoid0.portallauncher.data.BuiltInProfiles
import com.tanvoid0.portallauncher.data.DefaultHomeStatus
import com.tanvoid0.portallauncher.ui.kit.PortalGroup
import com.tanvoid0.portallauncher.ui.kit.SelectableRow
import com.tanvoid0.portallauncher.ui.kit.Spacing

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
                .padding(horizontal = Spacing.gutter)
        ) {
            StepProgress(
                stepIndex = stepIndex,
                stepCount = steps.size,
                modifier = Modifier.padding(top = Spacing.xl)
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
                        .padding(vertical = Spacing.xxl)
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
            text = stringResource(R.string.step_of, stepIndex + 1, stepCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.sm))
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
            .padding(bottom = Spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // No step blocks Continue. Everything here is either optional or granted
        // somewhere we do not control, and a wizard that traps someone on a screen
        // they cannot satisfy is worse than a launcher with one thing switched off.
        if (isFirst) {
            TextButton(onClick = onSkipAll, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.skip_setup))
            }
        } else {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.back))
            }
        }
        Button(onClick = onNext, modifier = Modifier.weight(1f)) {
            Text(stringResource(if (isLast) R.string.finish else R.string.continue_label))
        }
    }
}

@Composable
private fun StepHeader(title: String, body: String) {
    Text(text = title, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(Spacing.md))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(Spacing.xxl))
}

@Composable
private fun WelcomeStep() {
    StepHeader(
        title = stringResource(R.string.welcome_title),
        body = stringResource(R.string.welcome_body)
    )
    Highlight(
        Icons.Default.Home,
        stringResource(R.string.welcome_home_title),
        stringResource(R.string.welcome_home_body)
    )
    Spacer(Modifier.height(Spacing.lg))
    Highlight(
        Icons.Default.Person,
        stringResource(R.string.welcome_profiles_title),
        stringResource(R.string.welcome_profiles_body)
    )
    Spacer(Modifier.height(Spacing.lg))
    Highlight(
        Icons.Default.NotificationsOff,
        stringResource(R.string.welcome_quiet_title),
        stringResource(R.string.welcome_quiet_body)
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
        title = stringResource(R.string.home_step_title),
        body = stringResource(R.string.home_step_body)
    )

    if (isDefaultHome) {
        GrantedCard(stringResource(R.string.home_granted))
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
        Text(stringResource(R.string.set_as_home))
    }

    if (roleRequestFailed) {
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.home_role_failed),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.sm))
        OutlinedButton(
            onClick = { context.startActivitySafely(DefaultHomeStatus.homeSettingsIntent()) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.open_home_settings))
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
        title = stringResource(R.string.profile_step_title),
        body = stringResource(R.string.profile_step_body)
    )
    // The kit group rounds the ends and keeps the options reading as one list, rather
    // than as a stack of unrelated cards. No gutter of its own — the setup Column
    // already provides the page margin, and a second one would indent the options
    // past the text above them.
    PortalGroup(modifier = Modifier.selectableGroup(), gutter = 0.dp) {
        profiles.forEach { profile ->
            SelectableRow(
                title = profile.name,
                selected = profile.id == selectedId,
                onSelect = { viewModel.selectProfile(profile.id) }
            )
        }
    }
}

@Composable
private fun NotificationsStep(granted: Boolean) {
    val context = LocalContext.current
    StepHeader(
        title = stringResource(R.string.notifications_step_title),
        body = stringResource(R.string.notifications_step_body)
    )
    if (granted) {
        GrantedCard(stringResource(R.string.notifications_granted))
        return
    }
    Button(
        onClick = {
            context.startActivitySafely(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.grant_notification_access))
    }
    Spacer(Modifier.height(Spacing.sm))
    Text(
        text = stringResource(R.string.skip_is_fine),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun GrantedCard(text: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
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
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
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
