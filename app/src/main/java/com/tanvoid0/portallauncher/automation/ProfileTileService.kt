package com.tanvoid0.portallauncher.automation

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tanvoid0.portallauncher.PortalLauncherApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Quick Settings tile that cycles to the next profile.
 *
 * The point of a profile is switching it often, and going home → drawer → Profiles →
 * tap is four steps for something that should be one. A tile is reachable from inside
 * any app, which is exactly where the user is when they realise they are in the wrong
 * mode.
 *
 * Cycles rather than opening a picker: a tile has room for one action, and
 * [android.service.quicksettings.TileService.startActivityAndCollapse] to show a chooser
 * is heavily restricted on newer releases. The tile label always names the profile that
 * is active, so cycling is legible.
 */
class ProfileTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartListening() {
        super.onStartListening()
        refreshLabel()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        val app = application as? PortalLauncherApplication ?: return
        scope.launch {
            val profiles = app.profileRepository.getAllProfiles().first()
            if (profiles.isEmpty()) return@launch
            val activeId = app.activeProfileSource.activeProfile.first()?.id
            val index = profiles.indexOfFirst { it.id == activeId }
            // -1 wraps to 0, so an unknown active profile lands on the first one rather
            // than crashing on an out-of-range index.
            val next = profiles[(index + 1).mod(profiles.size)]
            app.preferencesRepository.setActiveProfileId(next.id)
            refreshLabel()
        }
    }

    private fun refreshLabel() {
        val app = application as? PortalLauncherApplication ?: return
        scope.launch {
            val active = app.activeProfileSource.activeProfile.first()
            qsTile?.apply {
                label = active?.name ?: "Portal"
                state = if (active != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                updateTile()
            }
        }
    }
}
