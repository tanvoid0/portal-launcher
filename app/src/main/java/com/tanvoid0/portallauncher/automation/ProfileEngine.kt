package com.tanvoid0.portallauncher.automation

import android.content.Context
import com.tanvoid0.portallauncher.data.ActiveProfileConfig
import com.tanvoid0.portallauncher.data.ActiveProfileSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Every automation the launcher knows about.
 *
 * A list, not a `when` over profile types. Profiles stay data: adding an automation is
 * one entry here plus one implementation, and no profile code changes at all.
 *
 * App visibility is deliberately **absent**. It is not an apply/revert side effect — it
 * is how the home screen decides what to draw, read at render time from the active
 * profile. Forcing it through this interface would mean an [Automation] whose `apply`
 * did nothing and whose real behaviour lived somewhere else entirely.
 */
object AutomationRegistry {

    val all: List<Automation> = listOf(
        GreyscaleAutomation(),
        NotificationFilterAutomation(),
        AppBlockerAutomation()
    )

    fun byId(id: String): Automation? = all.find { it.id == id }
}

/**
 * Applies and reverts automations as the active profile changes.
 *
 * Diffs against the last transition rather than reapplying everything: applying an
 * automation that is already on is wasted work at best, and for anything that talks to a
 * system service it is a visible flicker. Reverting is driven by what the *outgoing*
 * profile had on, which is why the engine has to remember it — the incoming profile
 * cannot tell you what to undo.
 *
 * Every call is wrapped: one automation that throws must not stop the others, or a
 * profile switch could leave the device half-configured with no way to notice.
 */
class ProfileEngine(
    private val context: Context,
    private val activeProfileSource: ActiveProfileSource,
    private val automations: List<Automation> = AutomationRegistry.all
) {

    private var appliedIds: Set<String> = emptySet()

    fun start(scope: CoroutineScope) {
        activeProfileSource.activeConfigs
            .onEach { transitionTo(it) }
            .launchIn(scope)
    }

    private suspend fun transitionTo(active: ActiveProfileConfig) {
        val wanted = automations
            .filter { active.isEnabled(it.id) && it.availability(context) is AutomationAvailability.Ready }
            .map { it.id }
            .toSet()

        (appliedIds - wanted).forEach { id ->
            automations.find { it.id == id }?.let { runCatching { it.revert(context) } }
        }
        (wanted - appliedIds).forEach { id ->
            automations.find { it.id == id }?.let {
                runCatching { it.apply(context, active.configJson(id)) }
            }
        }
        appliedIds = wanted
    }
}
