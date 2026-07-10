package com.example.caraka.ui.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
        val manualContacts = stringSetPreferencesKey("manual_contacts") // entri "peerIdnama"
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

    // ── Kontak Caraka yang ditambah manual (peerId belum tentu punya kunci) ───────────────────
    // Disimpan sebagai set string "peerIdnama". Persisten lintas restart tanpa migrasi DB.

    private val manualSep = ""

    /** Daftar kontak manual: pasangan (peerId, nama). */
    fun observeManualContacts(): Flow<List<Pair<String, String>>> =
        context.uiPrefsDataStore.data.map { prefs ->
            (prefs[Keys.manualContacts] ?: emptySet()).mapNotNull { entry ->
                val parts = entry.split(manualSep, limit = 2)
                val id = parts.getOrNull(0)?.trim().orEmpty()
                if (id.isBlank()) null else id to (parts.getOrNull(1)?.trim().orEmpty())
            }
        }

    /** Tambah/timpa kontak manual by peerId. */
    suspend fun addManualContact(peerId: String, name: String) {
        val id = peerId.trim()
        if (id.isBlank()) return
        context.uiPrefsDataStore.edit { prefs ->
            val current = prefs[Keys.manualContacts] ?: emptySet()
            val kept = current.filterNot { it.substringBefore(manualSep) == id }.toSet()
            prefs[Keys.manualContacts] = kept + "$id$manualSep${name.trim()}"
        }
    }

    /** Hapus kontak manual (mis. saat sudah jadi peer ber-kunci). */
    suspend fun removeManualContact(peerId: String) {
        val id = peerId.trim()
        context.uiPrefsDataStore.edit { prefs ->
            val current = prefs[Keys.manualContacts] ?: return@edit
            prefs[Keys.manualContacts] = current.filterNot { it.substringBefore(manualSep) == id }.toSet()
        }
    }
}
