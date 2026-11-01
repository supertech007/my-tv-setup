package com.mytvsetup.launcher.settings

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/** The only delegate for settings.preferences_pb; share it across all settings consumers. */
val Context.settingsStore by preferencesDataStore(name = "settings")
