package com.tanvoid0.portallauncher.ai.agent

import com.tanvoid0.portallauncher.PortalLauncherApplication
import com.tanvoid0.portallauncher.automation.Automation
import com.tanvoid0.portallauncher.automation.AutomationAvailability
import com.tanvoid0.portallauncher.automation.DisplayComfortAutomation
import com.tanvoid0.portallauncher.automation.DndAutomation
import com.tanvoid0.portallauncher.automation.ProfileScheduler
import com.tanvoid0.portallauncher.data.AutomationConfigEntity
import com.tanvoid0.portallauncher.data.AutomationIds
import com.tanvoid0.portallauncher.data.BrightnessMode
import com.tanvoid0.portallauncher.data.ConfigCodec
import com.tanvoid0.portallauncher.data.DisplayComfortConfig
import com.tanvoid0.portallauncher.data.DndConfig
import com.tanvoid0.portallauncher.data.DndFilterLevel
import com.tanvoid0.portallauncher.data.ProfileEntity
import com.tanvoid0.portallauncher.data.RefreshRateMode
import com.tanvoid0.portallauncher.data.ScheduleSlot
import com.tanvoid0.portallauncher.data.SchedulerConfig
import java.util.Locale
import kotlinx.coroutines.flow.first

/**
 * The assistant's tool set: every one of these calls an existing repository or
 * automation method (see the file each one names), never DataStore or Room directly.
 *
 * Kotlin port of the shape `portal_ai`'s per-app tool lists take (`AiTool` lists built
 * from a repository), scoped to what a launcher profile actually has: switching
 * profiles, display comfort, Do Not Disturb, and the schedule.
 */
fun launcherTools(app: PortalLauncherApplication): List<LauncherTool> = listOf(
    LauncherTool(
        name = "list_profiles",
        description = "Lists every profile by name."
    ) { _ ->
        val profiles = app.profileRepository.getAllProfiles().first()
        if (profiles.isEmpty()) "no profiles yet" else profiles.joinToString(", ") { it.name }
    },
    LauncherTool(
        name = "get_active_profile",
        description = "Returns the name of the profile currently in effect."
    ) { _ ->
        app.activeProfileSource.activeProfile.first()?.name ?: "no active profile"
    },
    LauncherTool(
        name = "switch_profile",
        description = "Switches the active profile.",
        parameters = mapOf("profile_id_or_name" to "a profile's id or name"),
        mutates = true
    ) { call ->
        val query = call.argString("profile_id_or_name")
            ?: return@LauncherTool "profile_id_or_name is required"
        val profiles = app.profileRepository.getAllProfiles().first()
        val target = resolveProfile(query, profiles)
            ?: return@LauncherTool "no profile matches \"$query\""
        app.preferencesRepository.setActiveProfileId(target.id)
        "Switched to ${target.name}."
    },
    LauncherTool(
        name = "set_display",
        description = "Changes screen brightness and/or refresh rate for the active " +
            "profile, and applies it immediately.",
        parameters = mapOf(
            "refresh_rate_mode" to "auto, min, max, or custom",
            "brightness_mode" to "auto or manual",
            "brightness_percent" to "0-100, used when brightness_mode is manual"
        ),
        mutates = true
    ) { call ->
        val profile = app.activeProfileSource.activeProfile.first()
            ?: return@LauncherTool "no active profile"
        val current = ConfigCodec.decodeOr(
            app.profileRepository.getAutomationConfig(profile.id, AutomationIds.DISPLAY_COMFORT)
                .first()?.configJson,
            DisplayComfortConfig()
        )
        val updated = current.copy(
            refreshRateMode = call.argEnum<RefreshRateMode>("refresh_rate_mode")
                ?: current.refreshRateMode,
            brightnessMode = call.argEnum<BrightnessMode>("brightness_mode")
                ?: current.brightnessMode,
            brightnessPercent = call.argInt("brightness_percent") ?: current.brightnessPercent
        )
        applyAndSave(
            app, profile, DisplayComfortAutomation(), AutomationIds.DISPLAY_COMFORT, ConfigCodec.encode(updated)
        )
    },
    LauncherTool(
        name = "set_dnd",
        description = "Sets Do Not Disturb for the active profile.",
        parameters = mapOf("level" to "priority_only, alarms_only, or total_silence"),
        mutates = true
    ) { call ->
        val level = call.argEnum<DndFilterLevel>("level")
            ?: return@LauncherTool "level must be priority_only, alarms_only, or total_silence"
        val profile = app.activeProfileSource.activeProfile.first()
            ?: return@LauncherTool "no active profile"
        applyAndSave(app, profile, DndAutomation(), AutomationIds.DND, ConfigCodec.encode(DndConfig(level)))
    },
    LauncherTool(
        name = "list_schedule_slots",
        description = "Lists every scheduled profile switch."
    ) { _ ->
        val slots = ConfigCodec.decodeOr(app.preferencesRepository.scheduleJson.first(), SchedulerConfig()).slots
        if (slots.isEmpty()) return@LauncherTool "no schedule slots"
        val profiles = app.profileRepository.getAllProfiles().first()
        slots.joinToString("\n") { slot ->
            val name = profiles.find { it.id == slot.profileId }?.name ?: slot.profileId
            "${formatMinutes(slot.startTimeMinutes)}-${formatMinutes(slot.endTimeMinutes)}: $name"
        }
    },
    LauncherTool(
        name = "add_schedule_slot",
        description = "Adds a scheduled profile switch.",
        parameters = mapOf(
            "start" to "start time, 24h HH:MM",
            "end" to "end time, 24h HH:MM",
            "profile_id_or_name" to "the profile to switch to"
        ),
        mutates = true
    ) { call ->
        val start = parseTimeMinutes(call.argString("start"))
            ?: return@LauncherTool "start must be a 24h time like 09:00"
        val end = parseTimeMinutes(call.argString("end"))
            ?: return@LauncherTool "end must be a 24h time like 17:00"
        if (start == end) return@LauncherTool "start and end must differ"
        val query = call.argString("profile_id_or_name")
            ?: return@LauncherTool "profile_id_or_name is required"
        val profiles = app.profileRepository.getAllProfiles().first()
        val profile = resolveProfile(query, profiles)
            ?: return@LauncherTool "no profile matches \"$query\""
        val slots = ConfigCodec.decodeOr(app.preferencesRepository.scheduleJson.first(), SchedulerConfig()).slots
        app.preferencesRepository.setScheduleJson(
            ConfigCodec.encode(SchedulerConfig(slots = slots + ScheduleSlot(start, end, profile.id)))
        )
        ProfileScheduler(app).sync()
        "Added ${formatMinutes(start)}-${formatMinutes(end)} -> ${profile.name}."
    },
    LauncherTool(
        name = "remove_schedule_slot",
        description = "Removes a scheduled profile switch by its start and end time.",
        parameters = mapOf("start" to "start time, 24h HH:MM", "end" to "end time, 24h HH:MM"),
        mutates = true
    ) { call ->
        val start = parseTimeMinutes(call.argString("start"))
            ?: return@LauncherTool "start must be a 24h time like 09:00"
        val end = parseTimeMinutes(call.argString("end"))
            ?: return@LauncherTool "end must be a 24h time like 17:00"
        val slots = ConfigCodec.decodeOr(app.preferencesRepository.scheduleJson.first(), SchedulerConfig()).slots
        val remaining = slots.filterNot { it.startTimeMinutes == start && it.endTimeMinutes == end }
        if (remaining.size == slots.size) {
            return@LauncherTool "no slot at ${formatMinutes(start)}-${formatMinutes(end)}"
        }
        app.preferencesRepository.setScheduleJson(
            if (remaining.isEmpty()) null else ConfigCodec.encode(SchedulerConfig(slots = remaining))
        )
        ProfileScheduler(app).sync()
        "Removed ${formatMinutes(start)}-${formatMinutes(end)}."
    }
)

/** Matches a profile by id first, falling back to a case-insensitive name match. */
private fun resolveProfile(idOrName: String, profiles: List<ProfileEntity>): ProfileEntity? =
    profiles.find { it.id == idOrName }
        ?: profiles.find { it.name.equals(idOrName, ignoreCase = true) }

/**
 * Writes [json] as [profile]'s config for [automationId] -- the same
 * `AutomationConfigEntity`/`ConfigCodec` path [com.tanvoid0.portallauncher.ui.profiles.ProfileEditViewModel]
 * writes on autosave -- turns the automation on for this profile if it was not
 * already, then applies it directly rather than waiting for [ProfileEngine] to notice
 * the change on its next transition, so the effect is immediate.
 */
private suspend fun applyAndSave(
    app: PortalLauncherApplication,
    profile: ProfileEntity,
    automation: Automation,
    automationId: String,
    json: String
): String {
    app.profileRepository.saveAutomationConfig(AutomationConfigEntity(profile.id, automationId, json))
    if (automationId !in profile.enabledAutomationIds) {
        app.profileRepository.insertProfile(
            profile.copy(enabledAutomationIds = profile.enabledAutomationIds + automationId)
        )
    }
    return when (val availability = automation.availability(app)) {
        AutomationAvailability.Ready -> {
            automation.apply(app, json)
            "Updated ${profile.name}'s settings and applied them now."
        }
        is AutomationAvailability.NeedsPermission ->
            "Saved for ${profile.name}, but couldn't apply it now: ${availability.explanation}"
        is AutomationAvailability.Unsupported ->
            "Saved for ${profile.name}, but couldn't apply it now: ${availability.reason}"
    }
}

private val TIME_PATTERN = Regex("""^(\d{1,2}):(\d{2})$""")

/** "HH:MM" (24h) to minutes since midnight, or null when it doesn't parse. */
private fun parseTimeMinutes(raw: String?): Int? {
    val match = TIME_PATTERN.find(raw?.trim().orEmpty()) ?: return null
    val hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()
    return (hour * 60 + minute).takeIf { hour in 0..23 && minute in 0..59 }
}

private fun formatMinutes(minutes: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", minutes / 60, minutes % 60)
