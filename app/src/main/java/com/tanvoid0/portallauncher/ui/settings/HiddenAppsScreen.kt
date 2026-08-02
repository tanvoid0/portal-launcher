package com.tanvoid0.portallauncher.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tanvoid0.portallauncher.PortalLauncherApplication
import com.tanvoid0.portallauncher.R
import com.tanvoid0.portallauncher.data.AppOverrideEntity
import com.tanvoid0.portallauncher.data.LaunchableApp
import com.tanvoid0.portallauncher.ui.kit.EmptyState
import com.tanvoid0.portallauncher.ui.kit.PortalGroup
import com.tanvoid0.portallauncher.ui.kit.PortalRow
import com.tanvoid0.portallauncher.ui.kit.PortalScreen
import com.tanvoid0.portallauncher.ui.kit.Spacing
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The way back from hiding an app.
 *
 * Without this screen "Hide app" is a one-way door: a hidden app is filtered out of the
 * home screen, the drawer and search, so no surface is left that could offer to unhide
 * it. The context menu tells the user this screen exists, so it has to.
 */
@Composable
fun HiddenAppsScreen(
    modifier: Modifier = Modifier,
    viewModel: HiddenAppsViewModel = viewModel()
) {
    val hidden by viewModel.hiddenApps.collectAsStateWithLifecycle()

    PortalScreen(
        title = stringResource(R.string.hidden_apps),
        subtitle = stringResource(R.string.hidden_apps_subtitle),
        modifier = modifier
    ) {
        if (hidden.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.nothing_hidden),
                body = stringResource(R.string.nothing_hidden_body),
                icon = Icons.Default.VisibilityOff,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            return@PortalScreen
        }

        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = Spacing.xxl)
            ) {
                PortalGroup {
                    hidden.forEach { app ->
                        PortalRow(
                            title = app.displayLabel,
                            subtitle = app.packageName,
                            trailing = {
                                TextButton(onClick = { viewModel.unhide(app) }) {
                                    Text(stringResource(R.string.unhide))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

class HiddenAppsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PortalLauncherApplication
    private val appOverrideDao = app.database.appOverrideDao()

    /**
     * Reads the **unfiltered** app list on purpose. Everywhere else consumes the list
     * with overrides already applied, which by definition excludes exactly the apps
     * this screen exists to show.
     */
    val hiddenApps: StateFlow<List<LaunchableApp>> = combine(
        app.appRepository.apps,
        appOverrideDao.observeAll()
    ) { apps, overrides ->
        val hiddenKeys = overrides
            .filter { it.hidden }
            .mapTo(mutableSetOf()) { "${it.packageName}/${it.activityName}/${it.userSerial}" }
        apps.filter { it.key in hiddenKeys }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun unhide(app: LaunchableApp) {
        viewModelScope.launch {
            appOverrideDao.upsert(
                AppOverrideEntity(
                    packageName = app.packageName,
                    activityName = app.activityName,
                    userSerial = app.userSerial,
                    hidden = false,
                    // A rename survives an unhide; they are independent decisions.
                    customLabel = app.customLabel
                )
            )
            appOverrideDao.pruneEmpty()
        }
    }
}
