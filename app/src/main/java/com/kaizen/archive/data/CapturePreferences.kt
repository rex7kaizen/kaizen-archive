package com.kaizen.archive.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.captureStore by preferencesDataStore(name = "capture_settings")

class CapturePreferences(private val context: Context) {
    private val captureEnabledKey = booleanPreferencesKey("capture_enabled")

    val captureEnabled: Flow<Boolean> = context.captureStore.data.map { preferences ->
        preferences[captureEnabledKey] ?: false
    }

    suspend fun setCaptureEnabled(enabled: Boolean) {
        context.captureStore.edit { preferences ->
            preferences[captureEnabledKey] = enabled
        }
    }
}
