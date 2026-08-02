package com.tanvoid0.portallauncher.automation

import android.content.Context
import android.content.Intent
import com.tanvoid0.portallauncher.data.AutomationIds

/**
 * Why an automation cannot run right now.
 *
 * A separate type rather than a boolean because the *reason* is what the UI has to
 * show: "your phone cannot do this" and "you have not granted this yet" need different
 * words and different buttons, and an automation that silently does nothing is the
 * failure mode this whole design exists to avoid.
 */
sealed interface AutomationAvailability {
    /** Ready to apply. */
    data object Ready : AutomationAvailability

    /**
     * Needs a permission the user grants outside this app. [settingsIntent] opens the
     * screen where they grant it; [explanation] is shown before we send them there.
     */
    data class NeedsPermission(
        val explanation: String,
        val settingsIntent: Intent
    ) : AutomationAvailability

    /** Cannot work on this device or OS version at all. Say so; do not offer a button. */
    data class Unsupported(val reason: String) : AutomationAvailability
}

/**
 * One reusable behaviour a profile can switch on.
 *
 * The engine only ever sees this interface, which is what keeps profiles as *data*:
 * adding an eighth automation means one new implementation and one registry entry, not
 * a new branch in the profile code. See [AutomationIds] for the stored ids.
 *
 * [apply] and [revert] must both be idempotent. The engine calls [revert] on the
 * outgoing profile and [apply] on the incoming one, and a switch that is interrupted
 * halfway — the process dying mid-transition — must be recoverable by simply running
 * the transition again.
 */
interface Automation {

    /** Stable id, stored in `automation_config.automationId`. */
    val id: String

    /** Short human name, for the profile editor. A resource so it localizes. */
    val titleRes: Int

    /** One line on what enabling it does, for the profile editor. */
    val summaryRes: Int

    /** Whether this can run, and if not, what to tell the user. */
    fun availability(context: Context): AutomationAvailability

    /** Puts the behaviour into effect with the given stored config JSON. */
    suspend fun apply(context: Context, configJson: String?)

    /** Takes the behaviour back off. Called when leaving a profile that had it on. */
    suspend fun revert(context: Context)
}
