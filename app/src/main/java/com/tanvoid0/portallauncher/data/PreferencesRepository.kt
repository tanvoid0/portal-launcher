package com.tanvoid0.portallauncher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "portal_launcher_prefs")

private val KEY_DEFAULT_PROFILE_ID = stringPreferencesKey("default_profile_id")

class PreferencesRepository(private val context: Context) {

    val defaultProfileId: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_PROFILE_ID]
    }

    suspend fun setDefaultProfileId(profileId: String?) {
        context.dataStore.edit { prefs ->
            if (profileId != null) prefs[KEY_DEFAULT_PROFILE_ID] = profileId
            else prefs.remove(KEY_DEFAULT_PROFILE_ID)
        }
    }
}
