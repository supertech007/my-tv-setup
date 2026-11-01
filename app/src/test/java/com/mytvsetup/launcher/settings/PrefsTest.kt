package com.mytvsetup.launcher.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PrefsTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test fun movesOnePlaceEarlier() {
        assertEquals(listOf("a", "c", "b"), moveInDock(listOf("a", "b", "c"), "c", -1))
    }

    @Test fun firstItemCannotMoveEarlier() {
        assertEquals(listOf("a", "b", "c"), moveInDock(listOf("a", "b", "c"), "a", -1))
    }

    @Test fun unknownPackageLeavesOrderUnchanged() {
        assertEquals(listOf("a", "b", "c"), moveInDock(listOf("a", "b", "c"), "unknown", 1))
    }

    @Test fun lastItemCannotMoveLater() {
        assertEquals(listOf("a", "b", "c"), moveInDock(listOf("a", "b", "c"), "c", 1))
    }

    @Test fun defaultsRoundTripAsJson() {
        assertEquals(Prefs(), json.decodeFromString<Prefs>(json.encodeToString(Prefs())))
    }

    @Test fun olderJsonPreservesValuesAndDefaultsNewFields() {
        val decoded = json.decodeFromString<Prefs>("""{"name":"Test","city":"Mumbai","futureField":true}""")
        assertEquals(Prefs(name = "Test", city = "Mumbai"), decoded)
    }

    @Test fun customValuesRoundTripAsJson() {
        val prefs = Prefs(
            name = "Test", city = "Mumbai", lat = 19.0, lon = 72.8,
            wallpaper = "forest", accent = "gold", dockOrder = listOf("b", "a"),
            hiddenApps = setOf("c"), hiddenCards = setOf("music"), personalise = false,
            videoLangs = setOf("en"), musicLangs = setOf("pa"), musicApp = "music.pkg",
            hiddenYtAccounts = setOf("account")
        )
        assertEquals(prefs, json.decodeFromString<Prefs>(json.encodeToString(prefs)))
    }

    @Test fun repositoryDefaultsAndUpdatesPersistAsOneJsonValue() = runBlocking {
        val file = temporaryFolder.newFolder().resolve("settings.preferences_pb")
        suspend fun withRepository(check: suspend (PrefsRepository) -> Unit) {
            val job = SupervisorJob()
            val scope = CoroutineScope(Dispatchers.IO + job)
            try {
                val store = PreferenceDataStoreFactory.create(scope = scope) { file }
                check(PrefsRepository(store))
                assertEquals(1, store.data.first().asMap().size)
            } finally {
                scope.cancel()
                job.join()
            }
        }
        withRepository { repository ->
            assertEquals(Prefs(), repository.prefs.first())
            repository.update { it.copy(city = "Mumbai", hiddenApps = setOf("hidden")) }
        }
        withRepository { repository ->
            assertEquals(Prefs(city = "Mumbai", hiddenApps = setOf("hidden")), repository.prefs.first())
        }
    }

    @Test fun malformedStoredJsonFallsBackToDefaultsAndCanBeUpdated() = runBlocking {
        val job = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.IO + job)
        try {
            val file = temporaryFolder.newFolder().resolve("settings.preferences_pb")
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            store.edit { it[stringPreferencesKey("prefs_json")] = "garbage JSON {" }
            val repository = PrefsRepository(store)
            assertEquals(Prefs(), repository.prefs.first())
            repository.update { it.copy(city = "Mumbai") }
            assertEquals(Prefs(city = "Mumbai"), repository.prefs.first())
        } finally {
            scope.cancel()
            job.join()
        }
    }

    @Test fun concurrentUpdatesDoNotLoseChanges() = runBlocking {
        val job = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.IO + job)
        try {
            val file = temporaryFolder.newFolder().resolve("settings.preferences_pb")
            val repository = PrefsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            (1..20).map { index ->
                async { repository.update { it.copy(hiddenApps = it.hiddenApps + "pkg$index") } }
            }.awaitAll()
            assertEquals((1..20).map { "pkg$it" }.toSet(), repository.prefs.first().hiddenApps)
        } finally {
            scope.cancel()
            job.join()
        }
    }
}
