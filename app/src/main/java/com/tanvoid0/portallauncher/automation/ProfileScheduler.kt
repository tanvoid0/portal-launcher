package com.tanvoid0.portallauncher.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tanvoid0.portallauncher.PortalLauncherApplication
import com.tanvoid0.portallauncher.data.ConfigCodec
import com.tanvoid0.portallauncher.data.PreferencesRepository
import com.tanvoid0.portallauncher.data.ScheduleSlot
import com.tanvoid0.portallauncher.data.SchedulerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Switches profiles on a timetable.
 *
 * **One alarm at a time, re-armed on each fire.** Not `WorkManager`: its minimum
 * periodic interval is 15 minutes and it explicitly does not promise punctuality, which
 * is useless for "Productivity at 09:00". Not N alarms for N slots either — that
 * multiplies the ways the schedule can drift out of sync with the config.
 *
 * Re-armed on `BOOT_COMPLETED` and `TIME_SET` because alarms do not survive a reboot and
 * a manual clock change silently invalidates the one that is pending.
 */
class ProfileScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /**
     * Resolves what should be active now, applies it, and arms the next transition.
     * Safe to call repeatedly — that is how boot, a clock change and each fire all
     * converge on the same state.
     *
     * The timetable is **global** ([PreferencesRepository.scheduleJson]), not read from
     * the active profile's config: a schedule stored on the profile it switches *from*
     * dies the moment it fires, because the incoming profile's config governs the next
     * transition and it has none.
     */
    suspend fun sync() {
        val app = context.applicationContext as? PortalLauncherApplication ?: return
        val config = ConfigCodec.decodeOr(
            app.preferencesRepository.scheduleJson.first(),
            SchedulerConfig()
        )
        if (config.slots.isEmpty()) {
            cancel()
            return
        }

        val active = app.activeProfileSource.activeProfile.first()
        val nowMinutes = currentMinuteOfDay()
        slotFor(nowMinutes, config.slots)?.let { slot ->
            if (slot.profileId != active?.id) {
                app.preferencesRepository.setActiveProfileId(slot.profileId)
            }
        }
        armNext(nowMinutes, config.slots)
    }

    private fun armNext(nowMinutes: Int, slots: List<ScheduleSlot>) {
        val nextBoundary = nextBoundaryMinutes(nowMinutes, slots) ?: return
        val triggerAt = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // A boundary at or before "now" belongs to tomorrow.
            val minutesAhead = (nextBoundary - nowMinutes + MINUTES_PER_DAY) % MINUTES_PER_DAY
            add(Calendar.MINUTE, if (minutesAhead == 0) MINUTES_PER_DAY else minutesAhead)
        }.timeInMillis

        val pending = pendingIntent(context)
        // A profile switch the user scheduled is a promise, and inexact alarms in Doze
        // can be hours late — so ask for exact. From API 31 the permission can be
        // revoked at any time, so check rather than assume, and degrade to an inexact
        // alarm instead of losing the schedule. SecurityException is still caught: the
        // check and the call are not atomic.
        val canBeExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        try {
            if (canBeExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    private fun cancel() = alarmManager.cancel(pendingIntent(context))

    companion object {
        private const val MINUTES_PER_DAY = 24 * 60
        private const val REQUEST_CODE = 1001

        /** Our own alarm's action, so the receiver can tell it from a spoofed intent. */
        const val ACTION_SCHEDULE_TICK = "com.tanvoid0.portallauncher.action.SCHEDULE_TICK"

        private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ProfileScheduleReceiver::class.java).setAction(ACTION_SCHEDULE_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        private fun currentMinuteOfDay(): Int = Calendar.getInstance().let {
            it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
        }
    }
}

/**
 * The slot covering [minuteOfDay], or null when none does.
 *
 * Handles slots that wrap midnight (22:00–07:00), which is the shape every "wind down"
 * schedule takes and the one an interval comparison gets wrong. Later slots win, so a
 * specific slot layered over a broad one behaves the way the list reads.
 */
fun slotFor(minuteOfDay: Int, slots: List<ScheduleSlot>): ScheduleSlot? =
    slots.lastOrNull { it.covers(minuteOfDay) }

/** True when this slot is in effect at [minuteOfDay], midnight wrap included. */
fun ScheduleSlot.covers(minuteOfDay: Int): Boolean =
    if (startTimeMinutes <= endTimeMinutes) {
        minuteOfDay >= startTimeMinutes && minuteOfDay < endTimeMinutes
    } else {
        minuteOfDay >= startTimeMinutes || minuteOfDay < endTimeMinutes
    }

/**
 * The next minute-of-day at which anything changes, or null when nothing ever does.
 * Both ends of every slot count: leaving a slot is as much a transition as entering one.
 *
 * A boundary at exactly [nowMinutes] is treated as a full day away, not as zero. It has
 * just fired — `sync()` applies it before arming — so counting it as "next" armed the
 * alarm for the same time tomorrow and **skipped every remaining boundary today**: a
 * 09:00–17:00 slot would switch on at nine and then never switch off.
 */
fun nextBoundaryMinutes(nowMinutes: Int, slots: List<ScheduleSlot>): Int? {
    val day = 24 * 60
    val boundaries = slots.flatMap { listOf(it.startTimeMinutes, it.endTimeMinutes) }
        .map { it.mod(day) }
        .distinct()
    if (boundaries.isEmpty()) return null
    return boundaries.minByOrNull { boundary ->
        val ahead = (boundary - nowMinutes).mod(day)
        if (ahead == 0) day else ahead
    }
}

/**
 * Wakes the scheduler on a fire, on boot, and when the clock is changed.
 *
 * The action is checked against an allow-list rather than trusted. The receiver is not
 * exported, but a receiver with an intent-filter for protected broadcasts can still be
 * reached with a missing or unexpected action, and acting on any intent that arrives is
 * how a component becomes a lever for something else.
 */
class ProfileScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in HANDLED_ACTIONS) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                ProfileScheduler(context.applicationContext).sync()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            ProfileScheduler.ACTION_SCHEDULE_TICK,
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
    }
}
