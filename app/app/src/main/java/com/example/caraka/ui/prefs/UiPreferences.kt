package com.example.caraka.ui.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.uiPrefsDataStore by preferencesDataStore("caraka_ui_prefs")

/**
 * Persistent UI / accessibility preferences:
 *  • language       — "id" (default) or "en"
 *  • bigText        — boolean, force ~1.25× font scale
 *  • highContrast   — boolean, AAA-contrast color tweak
 *  • haptics        — boolean, enable vibration on actions
 *  • onboardingDone — boolean, has first-run coach-mark tour been completed
 */
class UiPreferences(private val context: Context) {

    private object Keys {
        val language       = stringPreferencesKey("language")
        val bigText        = booleanPreferencesKey("big_text")
        val highContrast   = booleanPreferencesKey("high_contrast")
        val haptics        = booleanPreferencesKey("haptics")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
    }

    val language: Flow<String> = context.uiPrefsDataStore.data.map { it[Keys.language] ?: "id" }
    val bigText: Flow<Boolean> = context.uiPrefsDataStore.data.map { it[Keys.bigText] ?: false }
    val highContrast: Flow<Boolean> = context.uiPrefsDataStore.data.map { it[Keys.highContrast] ?: false }
    val haptics: Flow<Boolean> = context.uiPrefsDataStore.data.map { it[Keys.haptics] ?: true }
    val onboardingDone: Flow<Boolean> = context.uiPrefsDataStore.data.map { it[Keys.onboardingDone] ?: false }

    suspend fun setLanguage(value: String)     { context.uiPrefsDataStore.edit { it[Keys.language] = value } }
    suspend fun setBigText(value: Boolean)     { context.uiPrefsDataStore.edit { it[Keys.bigText] = value } }
    suspend fun setHighContrast(value: Boolean){ context.uiPrefsDataStore.edit { it[Keys.highContrast] = value } }
    suspend fun setHaptics(value: Boolean)     { context.uiPrefsDataStore.edit { it[Keys.haptics] = value } }
    suspend fun setOnboardingDone(value: Boolean) { context.uiPrefsDataStore.edit { it[Keys.onboardingDone] = value } }

    // ── Per-peer last-read timestamps (UI-only unread tracking) ──────────────

    private fun lastReadKey(peerId: String) = longPreferencesKey("last_read_$peerId")

    fun getLastRead(peerId: String): Flow<Long> =
        context.uiPrefsDataStore.data.map { it[lastReadKey(peerId)] ?: 0L }

    suspend fun setLastRead(peerId: String, timestamp: Long = System.currentTimeMillis()) {
        context.uiPrefsDataStore.edit { it[lastReadKey(peerId)] = timestamp }
    }

    /** Map of peerId → last-read timestamp for all stored keys. */
    fun observeLastReadMap(): Flow<Map<String, Long>> =
        context.uiPrefsDataStore.data.map { prefs ->
            prefs.asMap().mapNotNull { (key, value) ->
                if (key.name.startsWith("last_read_") && value is Long) {
                    key.name.removePrefix("last_read_") to value
                } else null
            }.toMap()
        }
}
