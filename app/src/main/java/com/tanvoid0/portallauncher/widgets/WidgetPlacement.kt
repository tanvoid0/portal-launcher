package com.tanvoid0.portallauncher.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import kotlinx.coroutines.CompletableDeferred

/**
 * Adding a widget, from the UI's point of view: pick a provider, get an id back, or get
 * null because the user backed out.
 *
 * An interface because the work needs an [Activity] — two system screens have to run and
 * report a result — while the code that wants a widget is a composable inside a
 * ViewModel-driven screen. Passed down from the Activity rather than reached for through
 * a `LocalContext` cast, so a composable with no Activity behind it (a preview, a test)
 * gets a null it can handle instead of a class-cast crash.
 */
interface WidgetPlacement {
    /**
     * Binds [provider] to a fresh id, runs its configuration screen if it has one, and
     * returns the id ready to be stored. Null means nothing was added and nothing leaked.
     */
    suspend fun add(provider: AppWidgetProviderInfo): Int?
}

/**
 * The real implementation, owned by the Activity.
 *
 * Two results have to be waited for and they arrive through different mechanisms:
 *
 * - **Bind consent** is a normal Intent, so it uses [StartActivityForResult].
 * - **Configuration** is not. From API 31 a provider's configuration activity need not
 *   be exported, so launching it by Intent throws; [android.appwidget.AppWidgetHost.startAppWidgetConfigureActivityForResult]
 *   exists precisely to run it through a system-granted IntentSender, and its result
 *   comes back through [Activity.onActivityResult]. There is no ActivityResultContract
 *   equivalent, which is why the Activity still overrides that method.
 *
 * Every failure path deletes the allocated id. An id that is bound but named by no
 * `home_cell` row is invisible and unremovable, and the start-up sweep is the backstop
 * for the one case this cannot cover: the process dying while a system screen is up.
 */
class ActivityWidgetPlacement(
    private val activity: ComponentActivity,
    private val host: LauncherWidgetHost
) : WidgetPlacement {

    /**
     * The result the current [add] call is waiting for. Only ever one — [add] runs the
     * two screens in sequence, and the user cannot start a second placement while a
     * system dialog is in front of the launcher.
     */
    private var pending: CompletableDeferred<Boolean>? = null

    private val bindConsent =
        activity.registerForActivityResult(StartActivityForResult()) { result ->
            complete(result.resultCode == Activity.RESULT_OK)
        }

    override suspend fun add(provider: AppWidgetProviderInfo): Int? {
        val appWidgetId = host.allocateAppWidgetId()

        if (!host.bindIfAllowed(appWidgetId, provider.profile, provider.provider)) {
            // No BIND_APPWIDGET for us — see LauncherWidgetHost. The grant this asks for
            // is per app, so this dialog appears once in the life of the install.
            val consent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, provider.profile)
            }
            val granted = await { bindConsent.launch(consent) } &&
                host.bindIfAllowed(appWidgetId, provider.profile, provider.provider)
            if (!granted) {
                host.deleteAppWidgetId(appWidgetId)
                return null
            }
        }

        if (provider.configure != null) {
            val configured = await {
                host.startAppWidgetConfigureActivityForResult(
                    activity,
                    appWidgetId,
                    0,
                    REQUEST_CONFIGURE,
                    null
                )
            }
            // Cancelling configuration means the widget was never set up. Keeping it
            // would put an unconfigured widget on the home screen, which most providers
            // draw as an error card.
            if (!configured) {
                host.deleteAppWidgetId(appWidgetId)
                return null
            }
        }

        return appWidgetId
    }

    /** Called by the Activity for every legacy result; ignores the ones that are not ours. */
    fun onConfigureResult(requestCode: Int, resultCode: Int) {
        if (requestCode == REQUEST_CONFIGURE) complete(resultCode == Activity.RESULT_OK)
    }

    private suspend fun await(start: () -> Unit): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        pending = deferred
        return try {
            start()
            deferred.await()
        } catch (e: ActivityNotFoundException) {
            // A provider that names a configuration activity it does not ship, or a
            // device with no bind-consent screen. Treated as a refusal so the caller
            // cleans up, rather than taking the launcher down with it.
            pending = null
            false
        } catch (e: SecurityException) {
            pending = null
            false
        }
    }

    private fun complete(ok: Boolean) {
        pending?.complete(ok)
        pending = null
    }

    private companion object {
        const val REQUEST_CONFIGURE = 0x5743
    }
}
