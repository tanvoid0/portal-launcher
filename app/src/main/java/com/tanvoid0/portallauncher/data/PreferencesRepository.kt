package com.tanvoid0.portallauncher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "portal_launcher_prefs")

// Kept as "default_profile_id" so existing installs keep their selection; the
// Kotlin name says what it actually is — the profile in effect right now.
private val KEY_ACTIVE_PROFILE_ID = stringPreferencesKey("default_profile_id")

// Off until the user turns it on. On-device or not, running a model over the list
// of apps someone has installed is not something to opt anyone into by default.
private val KEY_AI_CATEGORIES_ENABLED = booleanPreferencesKey("ai_categories_enabled")

class PreferencesRepository(private val context: Context) {

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

    /** Whether the user opted in to on-device AI filling gaps in app categories. */
    val aiCategoriesEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AI_CATEGORIES_ENABLED] == true
    }

    suspend fun setAiCategoriesEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[KEY_AI_CATEGORIES_ENABLED] = enabled }
    }
}
