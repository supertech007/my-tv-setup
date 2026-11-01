package com.mytvsetup.launcher.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PrefsRepository(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.settingsStore)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val key = stringPreferencesKey("prefs_json")

    val prefs: Flow<Prefs> = store.data.map { decode(it) }

    suspend fun update(f: (Prefs) -> Prefs) {
        store.edit { values ->
            values[key] = json.encodeToString(f(decode(values)))
        }
    }

    private fun decode(values: Preferences): Prefs = try {
        values[key]?.let { json.decodeFromString<Prefs>(it) } ?: Prefs()
    } catch (_: SerializationException) {
        Prefs()
    } catch (_: IllegalArgumentException) {
        Prefs()
    }
}
