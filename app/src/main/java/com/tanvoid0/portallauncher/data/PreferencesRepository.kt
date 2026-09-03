package com.tanvoid0.portallauncher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tanvoid0.portallauncher.ui.kit.IconShape
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "portal_launcher_prefs")

// Kept as "default_profile_id" so existing installs keep their selection; the
// Kotlin name says what it actually is — the profile in effect right now.
private val KEY_ACTIVE_PROFILE_ID = stringPreferencesKey("default_profile_id")

// Off until the user turns it on. On-device or not, running a model over the list
// of apps someone has installed is not something to opt anyone into by default.
private val KEY_AI_CATEGORIES_ENABLED = booleanPreferencesKey("ai_categories_enabled")

// Off until the user turns it on, same reasoning as above: a tool-calling assistant
// that can switch profiles and change device settings is opt-in, never a default.
private val KEY_AI_ASSISTANT_ENABLED = booleanPreferencesKey("ai_assistant_enabled")

// Set once the built-in profiles have been written. Superseded by
// KEY_SEEDED_BUILT_IN_PROFILE_IDS below, kept only so PortalLauncherApplication can
// tell an upgrading install (this was true) from a genuinely fresh one (never set).
private val KEY_BUILT_IN_PROFILES_SEEDED = booleanPreferencesKey("built_in_profiles_seeded")

// Which BuiltInProfiles ids have been decided already — seeded, or in the case of a
// pre-existing install, folded in as "already handled" during the one-time migration
// off the flag above. A profile whose id is in this set is never seeded again, whether
// or not it is still in the table, which is what lets BuiltInProfiles.all grow (a new
// template reaching existing installs) without also resurrecting one the user deleted.
private val KEY_SEEDED_BUILT_IN_PROFILE_IDS = stringSetPreferencesKey("seeded_built_in_profile_ids")

// Set when the setup wizard finishes, including when it is skipped. Without it the
// wizard is per-process and reappears on every cold start — which as the home app
// means every time the system reclaims us.
private val KEY_SETUP_COMPLETE = booleanPreferencesKey("setup_complete")

/**
 * Profiles whose home layout the user has taken over.
 *
 * Needed to tell "never customised" from "customised, then emptied". Without it,
 * unpinning the last app makes the layout indistinguishable from a fresh profile, so
 * the category default comes back and every app the user just removed reappears — and
 * an empty home screen is exactly what a minimal-launcher user is after.
 *
 * A preference rather than a column so it needs no migration; it is one flag per
 * profile, not a relation.
 */
private val KEY_CUSTOM_LAYOUT_PROFILE_IDS = stringSetPreferencesKey("custom_layout_profile_ids")

// The profile timetable, one SchedulerConfig as JSON. Global, not per profile: a
// schedule stored on the profile it switches *from* dies the moment it fires — the
// incoming profile's (empty) config would govern the next transition. A timetable is
// a statement about the day, not about any one profile.
private val KEY_SCHEDULE_JSON = stringPreferencesKey("schedule_json")

// The home grid, in cells. Device-wide rather than per profile: it is a statement about
// how big you want icons on this screen, not about what a profile is for — and a grid
// that changed under you when a schedule switched profiles would move every icon.
private val KEY_GRID_COLUMNS = intPreferencesKey("home_grid_columns")
private val KEY_GRID_ROWS = intPreferencesKey("home_grid_rows")

// How the drawer orders apps within a section. A preference rather than per-profile:
// it is a statement about how you like to find things, not about any one profile.
private val KEY_SORT_MODE = stringPreferencesKey("drawer_sort_mode")

// Whether the drawer groups apps under category headers at all. Default true keeps
// today's behaviour for everyone who never opens this toggle.
private val KEY_CATEGORY_BAR_VISIBLE = booleanPreferencesKey("drawer_category_bar_visible")

// Appearance, all default to today's look: nothing changes for an existing install
// until it opts in through the (not yet built) Appearance screen.
private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
private val KEY_COLOR_SOURCE = stringPreferencesKey("color_source")
private val KEY_ICON_SHAPE = stringPreferencesKey("icon_shape")
private val KEY_HOME_LABELS = booleanPreferencesKey("home_labels")

/** How the drawer orders apps within a section (or across all of them, ungrouped). */
enum class DrawerSortMode {
    ALPHABETICAL,
    MOST_USED
}

/** Follows the system setting until the user picks one side explicitly. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

/**
 * Where the theme's accent colour comes from. Portal is the brand palette in
 * [com.tanvoid0.portallauncher.ui.theme.PortalLauncherTheme]; Wallpaper is Android's
 * dynamic colour (API 31+), opt-in rather than default — see §18 of PRODUCTION_PLAN.md.
 */
enum class ColorSource {
    PORTAL,
    WALLPAPER
}

class PreferencesRepository(private val context: Context) {

    val drawerSortMode: Flow<DrawerSortMode> = context.dataStore.data.map { prefs ->
        prefs[KEY_SORT_MODE]?.let { stored ->
            runCatching { DrawerSortMode.valueOf(stored) }.getOrNull()
        } ?: DrawerSortMode.ALPHABETICAL
    }

    suspend fun setDrawerSortMode(mode: DrawerSortMode) {
        context.dataStore.edit { prefs -> prefs[KEY_SORT_MODE] = mode.name }
    }

    val categoryBarVisible: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_CATEGORY_BAR_VISIBLE] ?: true
    }

    suspend fun setCategoryBarVisible(visible: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_CATEGORY_BAR_VISIBLE] = visible }
    }

    /** Dark, light, or follows the system setting (the default). */
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE]?.let { stored ->
            runCatching { ThemeMode.valueOf(stored) }.getOrNull()
        } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs -> prefs[KEY_THEME_MODE] = mode.name }
    }

    /** Where the accent colour comes from. Defaults to the brand palette, not the wallpaper. */
    val colorSource: Flow<ColorSource> = context.dataStore.data.map { prefs ->
        prefs[KEY_COLOR_SOURCE]?.let { stored ->
            runCatching { ColorSource.valueOf(stored) }.getOrNull()
        } ?: ColorSource.PORTAL
    }

    suspend fun setColorSource(source: ColorSource) {
        context.dataStore.edit { prefs -> prefs[KEY_COLOR_SOURCE] = source.name }
    }

    /** The mask app icons render through. Defaults to the OEM's own adaptive-icon mask. */
    val iconShape: Flow<IconShape> = context.dataStore.data.map { prefs ->
        prefs[KEY_ICON_SHAPE]?.let { stored ->
            runCatching { IconShape.valueOf(stored) }.getOrNull()
        } ?: IconShape.System
    }

    suspend fun setIconShape(shape: IconShape) {
        context.dataStore.edit { prefs -> prefs[KEY_ICON_SHAPE] = shape.name }
    }

    /** Whether home-screen cells show a label under the icon. */
    val homeLabels: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_HOME_LABELS] ?: true
    }

    suspend fun setHomeLabels(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_HOME_LABELS] = enabled }
    }

    /** Profiles where the home grid is the user's own list, empty or not. */
    val customLayoutProfileIds: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_CUSTOM_LAYOUT_PROFILE_IDS] ?: emptySet()
    }

    suspend fun setLayoutCustomised(profileId: String, customised: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_CUSTOM_LAYOUT_PROFILE_IDS] ?: emptySet()
            prefs[KEY_CUSTOM_LAYOUT_PROFILE_IDS] =
                if (customised) current + profileId else current - profileId
        }
    }

    /**
     * How many cells the home screen is divided into.
     *
     * Clamped on read, not only on write: these are two loose integers in a preferences
     * file that survives downgrades and restores, and a zero here would divide the
     * screen by zero on the very first frame of the launcher.
     */
    val homeGrid: Flow<GridSize> = context.dataStore.data.map { prefs ->
        GridSize(
            columns = (prefs[KEY_GRID_COLUMNS] ?: GridSize.Default.columns)
                .coerceIn(MIN_COLUMNS, MAX_COLUMNS),
            rows = (prefs[KEY_GRID_ROWS] ?: GridSize.Default.rows)
                .coerceIn(MIN_ROWS, MAX_ROWS)
        )
    }

    suspend fun setHomeGrid(grid: GridSize) {
        context.dataStore.edit { prefs ->
            prefs[KEY_GRID_COLUMNS] = grid.columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
            prefs[KEY_GRID_ROWS] = grid.rows.coerceIn(MIN_ROWS, MAX_ROWS)
        }
    }

    /** The profile currently in effect. Changes when the user switches, or on a schedule. */
    val activeProfileId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_PROFILE_ID]
    }

    suspend fun setActiveProfileId(profileId: String?) {
        context.dataStore.edit { prefs ->
            if (profileId != null) prefs[KEY_ACTIVE_PROFILE_ID] = profileId
            else prefs.remove(KEY_ACTIVE_PROFILE_ID)
        }
    }

    /** The profile timetable as SchedulerConfig JSON, or null when none is set. */
    val scheduleJson: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_SCHEDULE_JSON]
    }

    suspend fun setScheduleJson(json: String?) {
        context.dataStore.edit { prefs ->
            if (json != null) prefs[KEY_SCHEDULE_JSON] = json
            else prefs.remove(KEY_SCHEDULE_JSON)
        }
    }

    /** Whether the user opted in to on-device AI filling gaps in app categories. */
    val aiCategoriesEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AI_CATEGORIES_ENABLED] == true
    }

    suspend fun setAiCategoriesEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_AI_CATEGORIES_ENABLED] = enabled }
    }

    /** Whether the user opted in to the on-device AI assistant. */
    val aiAssistantEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AI_ASSISTANT_ENABLED] == true
    }

    suspend fun setAiAssistantEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_AI_ASSISTANT_ENABLED] = enabled }
    }

    /** Whether the shipped profiles have already been written to the database. */
    val builtInProfilesSeeded: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_BUILT_IN_PROFILES_SEEDED] == true
    }

    suspend fun setBuiltInProfilesSeeded(seeded: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_BUILT_IN_PROFILES_SEEDED] = seeded }
    }

    /** [BuiltInProfiles] ids already decided — see [KEY_SEEDED_BUILT_IN_PROFILE_IDS]. */
    val seededBuiltInProfileIds: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_SEEDED_BUILT_IN_PROFILE_IDS] ?: emptySet()
    }

    /** Adds to the seeded-ids set; never removes, so this can be called incrementally. */
    suspend fun addSeededBuiltInProfileIds(ids: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SEEDED_BUILT_IN_PROFILE_IDS] = (prefs[KEY_SEEDED_BUILT_IN_PROFILE_IDS] ?: emptySet()) + ids
        }
    }

    /** Whether the user has been through first-run setup. False means show it. */
    val setupComplete: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SETUP_COMPLETE] == true
    }

    suspend fun setSetupComplete(complete: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_SETUP_COMPLETE] = complete }
    }

    companion object {
        /**
         * Grid bounds. The low end is where a widget stops having room to say anything;
         * the high end is where a 48dp icon plus its label stops being a comfortable
         * touch target on a phone.
         */
        const val MIN_COLUMNS = 3
        const val MAX_COLUMNS = 6
        const val MIN_ROWS = 3
        const val MAX_ROWS = 7
    }
}
