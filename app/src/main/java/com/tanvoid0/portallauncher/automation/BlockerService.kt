package com.tanvoid0.portallauncher.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.telecom.TelecomManager
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.tanvoid0.portallauncher.PortalLauncherApplication
import com.tanvoid0.portallauncher.R
import com.tanvoid0.portallauncher.data.AppBlockerConfig
import com.tanvoid0.portallauncher.data.AutomationIds
import com.tanvoid0.portallauncher.data.ConfigCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Watches the foreground app and covers blocked ones with a pause screen.
 *
 * Started by [AppBlockerAutomation.apply] when a profile with the blocker on becomes
 * active, stopped by its revert. While running it reads the active profile's
 * [AppBlockerConfig] itself, so editing the blocked list takes effect on the next poll
 * with no restart — the same observe-the-source pattern as the notification listener.
 *
 * The overlay is plain views, not Compose: a ComposeView in a service window needs a
 * hand-rolled lifecycle owner, and this screen is two lines of text and three buttons.
 *
 * ponytail: polls UsageStatsManager once a second while the screen is on. ~1s detection
 * latency, small battery cost. The instant alternative is an AccessibilityService, which
 * PRODUCTION_PLAN §1.2 defers on Play-policy grounds; add it as an opt-in upgrade if the
 * latency actually bothers users.
 */
class BlockerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val windowManager by lazy { getSystemService(WindowManager::class.java) }
    private val powerManager by lazy { getSystemService(PowerManager::class.java) }
    private val usageStats by lazy { getSystemService(UsageStatsManager::class.java) }

    private var blocked: Set<String> = emptySet()
    private val allowedUntil = mutableMapOf<String, Long>()

    private var currentForeground: String? = null
    private var queryFrom: Long = 0

    private var overlay: View? = null
    private var overlayFor: String? = null

    /**
     * Never covered, whatever the config says: ourselves (an overlay on top of the home
     * screen is a lockout with no surface left to undo it) and the dialer (a pause
     * screen over an emergency call is indefensible).
     */
    private val neverBlock: Set<String> by lazy {
        setOfNotNull(
            packageName,
            runCatching { getSystemService(TelecomManager::class.java)?.defaultDialerPackage }
                .getOrNull()
        )
    }

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        queryFrom = System.currentTimeMillis() - INITIAL_LOOKBACK_MILLIS

        val app = application as PortalLauncherApplication
        scope.launch {
            app.activeProfileSource.activeConfigs.collect { active ->
                if (!active.isEnabled(AutomationIds.APP_BLOCKER)) {
                    // The engine stops us on profile change; this covers a sticky
                    // restart after process death with a profile that no longer blocks.
                    stopSelf()
                    return@collect
                }
                blocked = ConfigCodec.decodeOr(
                    active.configJson(AutomationIds.APP_BLOCKER),
                    AppBlockerConfig()
                ).blockedPackageNames.toSet()
                // A new profile is a new session: reprieves granted under the old
                // rules do not carry over.
                allowedUntil.clear()
            }
        }
        scope.launch {
            while (isActive) {
                if (powerManager.isInteractive) checkForeground()
                delay(POLL_MILLIS)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        hideOverlay()
        scope.cancel()
        super.onDestroy()
    }

    private fun checkForeground() {
        val now = System.currentTimeMillis()
        val events = usageStats.queryEvents(queryFrom, now)
        // Overlap the next window by one poll rather than trusting the clock edge;
        // seeing an event twice is harmless, missing one leaves a blocked app open.
        queryFrom = now - POLL_MILLIS
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == FOREGROUND_EVENT) currentForeground = event.packageName
        }

        val target = currentForeground
        scope.launch(Dispatchers.Main) {
            if (BlockerPolicy.shouldBlock(target, blocked, allowedUntil, now, neverBlock)) {
                showOverlay(target!!)
            } else {
                hideOverlay()
            }
        }
    }

    // ---- overlay ----

    private fun showOverlay(blockedPackage: String) {
        if (overlayFor == blockedPackage && overlay != null) return
        hideOverlay()
        val view = buildOverlay(blockedPackage)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.OPAQUE
        )
        runCatching { windowManager.addView(view, params) }
            .onSuccess {
                overlay = view
                overlayFor = blockedPackage
                view.requestFocus()
            }
        // addView throws if the overlay grant was revoked since availability() passed;
        // nothing to do but keep polling — the editor will show the grant card again.
    }

    private fun hideOverlay() {
        overlay?.let { runCatching { windowManager.removeView(it) } }
        overlay = null
        overlayFor = null
    }

    private fun goHome() {
        hideOverlay()
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun buildOverlay(blockedPackage: String): View {
        val label = runCatching {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(blockedPackage, 0)
            ).toString()
        }.getOrDefault(blockedPackage)
        val dp = resources.displayMetrics.density

        fun text(value: String, sizeSp: Float, bold: Boolean = false) = TextView(this).apply {
            text = value
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        fun button(labelText: String, onClick: () -> Unit) = Button(this).apply {
            text = labelText
            setOnClickListener { onClick() }
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(SCRIM)
            val pad = (32 * dp).toInt()
            setPadding(pad, pad, pad, pad)

            addView(text("Paused by Portal", 24f, bold = true))
            addView(text("$label is blocked in this profile.", 16f).apply {
                setPadding(0, (8 * dp).toInt(), 0, (24 * dp).toInt())
            })
            addView(button("Go back") { goHome() })
            addView(button("5 more minutes") {
                allowedUntil[blockedPackage] =
                    System.currentTimeMillis() + BlockerPolicy.SNOOZE_MILLIS
                hideOverlay()
            })
            addView(button("Unlock for this session") {
                allowedUntil[blockedPackage] = Long.MAX_VALUE
                hideOverlay()
            })

            // The overlay window is focusable, so it eats the back gesture; back
            // meaning "leave the blocked app" is the only reading that helps.
            isFocusableInTouchMode = true
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    goHome()
                    true
                } else {
                    false
                }
            }
        }
    }

    // ---- foreground plumbing ----

    private fun startInForeground() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "App blocker",
                NotificationManager.IMPORTANCE_LOW
            )
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("App blocker is on")
            .setContentText("Watching for apps this profile blocks.")
            .setOngoing(true)
            .build()
        // specialUse exists from API 34; the typed overload with it on an older
        // platform throws over a type the manifest attribute could not even declare.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private companion object {
        const val CHANNEL_ID = "app_blocker"
        const val NOTIFICATION_ID = 2001
        const val POLL_MILLIS = 1_000L
        const val INITIAL_LOOKBACK_MILLIS = 60_000L
        /** 95% black: clearly a wall, not a tint, without OLED-black harshness. */
        const val SCRIM = 0xF2000000.toInt()

        /** Renamed to ACTIVITY_RESUMED in API 29; same constant value. */
        @Suppress("DEPRECATION")
        const val FOREGROUND_EVENT = UsageEvents.Event.MOVE_TO_FOREGROUND
    }
}
