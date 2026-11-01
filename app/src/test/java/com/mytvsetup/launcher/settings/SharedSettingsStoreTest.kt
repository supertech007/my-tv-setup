package com.mytvsetup.launcher.settings

import android.app.Application
import android.content.ContextWrapper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class SharedSettingsStoreTest {
    @Test fun repositoriesFromDifferentContextsShareSettingsAndObserveEachOthersUpdates() = runBlocking {
        withTimeout(5_000) {
            val context = RuntimeEnvironment.getApplication()
            val settings = PrefsRepository(context)
            val content = PrefsRepository(ContextWrapper(context))
            settings.update { it.copy(videoLangs = setOf("en"), personalise = false) }
            assertEquals(setOf("en"), content.prefs.first().videoLangs)
            assertEquals(false, content.prefs.first().personalise)
            content.update { it.copy(name = "Shared") }
            assertEquals("Shared", settings.prefs.first().name)
            assertEquals(setOf("en"), settings.prefs.first().videoLangs)
        }
    }
}
