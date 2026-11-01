package com.mytvsetup.launcher.ui.home

import com.mytvsetup.launcher.BuildConfig
import com.mytvsetup.launcher.weather.WeatherClient
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.IOException

/** Exercises the application interceptor, before DNS or any network connection. */
class HomeNetworkTest {
    @Test fun refusesUnapprovedHosts() {
        try {
            HomeNetwork.http.newCall(Request.Builder().url("https://example.invalid/").build()).execute().close()
            fail("An unapproved host must be refused")
        } catch (e: IOException) {
            assertEquals(if (BuildConfig.OFFLINE) "Debug offline mode" else "Host is not allowed", e.message)
        }
    }

    @Test fun debugOfflineModeFailsWeatherAndArtworkWithoutNetwork() {
        assumeTrue("Run with -Ptv.offline=true", BuildConfig.OFFLINE)
        val request = Request.Builder().url("https://images.justwatch.com/poster/example/s332/example.webp").build()
        try {
            HomeNetwork.http.newCall(request).execute().close()
            fail("Artwork HTTP must fail in debug offline mode")
        } catch (e: IOException) { assertEquals("Debug offline mode", e.message) }
        try {
            runBlocking { WeatherClient(HomeNetwork.http).now(28.6, 77.2) }
            fail("Weather HTTP must fail in debug offline mode")
        } catch (e: IOException) { assertEquals("Debug offline mode", e.message) }
    }
}
