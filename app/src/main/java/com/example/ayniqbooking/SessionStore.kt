package com.example.ayniqbooking

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ayniq_state")

class SessionStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private val appStateKey = stringPreferencesKey("app_state")

    val stateFlow: Flow<AppState> = context.dataStore.data.map { prefs ->
        val raw = prefs[appStateKey]
        if (raw.isNullOrBlank()) {
            AppState()
        } else {
            runCatching { json.decodeFromString<AppState>(raw) }.getOrElse { AppState() }
        }
    }

    suspend fun upsertAccount(account: AccountSession) {
        context.dataStore.edit { prefs ->
            val current = decode(prefs[appStateKey])
            val updated = current.copy(sessions = current.sessions + (account.phone to account))
            prefs[appStateKey] = json.encodeToString(updated)
        }
    }

    suspend fun deleteAccount(phone: String) {
        context.dataStore.edit { prefs ->
            val current = decode(prefs[appStateKey])
            val updated = current.copy(sessions = current.sessions - phone)
            prefs[appStateKey] = json.encodeToString(updated)
        }
    }

    suspend fun updateSessions(transform: (Map<String, AccountSession>) -> Map<String, AccountSession>) {
        context.dataStore.edit { prefs ->
            val current = decode(prefs[appStateKey])
            val updated = current.copy(sessions = transform(current.sessions))
            prefs[appStateKey] = json.encodeToString(updated)
        }
    }

    suspend fun updateSettings(settings: AppSettings) {
        context.dataStore.edit { prefs ->
            val current = decode(prefs[appStateKey])
            prefs[appStateKey] = json.encodeToString(current.copy(settings = settings))
        }
    }

    suspend fun updateAccess(access: AccessControl) {
        context.dataStore.edit { prefs ->
            val current = decode(prefs[appStateKey])
            prefs[appStateKey] = json.encodeToString(current.copy(access = access))
        }
    }

    suspend fun clearAccess() {
        context.dataStore.edit { prefs ->
            val current = decode(prefs[appStateKey])
            prefs[appStateKey] = json.encodeToString(
                current.copy(access = current.access.copy(token = "", approved = false))
            )
        }
    }

    private fun decode(raw: String?): AppState {
        if (raw.isNullOrBlank()) return AppState()
        return runCatching { json.decodeFromString<AppState>(raw) }.getOrElse { AppState() }
    }
}
