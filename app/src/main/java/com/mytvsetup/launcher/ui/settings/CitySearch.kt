package com.mytvsetup.launcher.ui.settings

import com.mytvsetup.launcher.ui.home.HomeNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException

data class City(val name: String, val region: String, val country: String, val lat: Double, val lon: Double) {
    val label: String get() = listOf(name, region, country).filter { it.isNotBlank() }.joinToString(", ")
}

fun citySearchUrl(query: String) = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl()
    .newBuilder().addQueryParameter("name", query.trim()).addQueryParameter("count", "5").build()

fun parseCities(body: String): List<City> {
    val results = Json.parseToJsonElement(body).jsonObject["results"] as? JsonArray ?: return emptyList()
    return results.mapNotNull { element ->
        val city = element as? JsonObject ?: return@mapNotNull null
        fun value(key: String) = (city[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val lat = value("latitude").toDoubleOrNull() ?: return@mapNotNull null
        val lon = value("longitude").toDoubleOrNull() ?: return@mapNotNull null
        val name = value("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
        City(name, value("admin1"), value("country"), lat, lon)
    }.take(5)
}

suspend fun searchCities(query: String): List<City> = withContext(Dispatchers.IO) {
    if (query.isBlank()) return@withContext emptyList()
    HomeNetwork.http.newCall(Request.Builder().url(citySearchUrl(query)).build()).execute().use { response ->
        if (!response.isSuccessful) throw IOException("City search HTTP ${response.code}")
        parseCities(response.body?.string() ?: throw IOException("Empty city search response"))
    }
}
