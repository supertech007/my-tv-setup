package com.mytvsetup.launcher.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.math.roundToInt

data class Weather(val tempC: Int, val code: Int, val label: String)

fun parseWeather(body: String): Weather {
    val current = Json.parseToJsonElement(body).jsonObject["current"]?.jsonObject
        ?: throw IllegalArgumentException("Missing current weather")
    val temp = current["temperature_2m"]?.jsonPrimitive?.doubleOrNull
        ?: throw IllegalArgumentException("Missing temperature")
    require(temp.isFinite()) { "Invalid temperature" }
    val code = current["weather_code"]?.jsonPrimitive?.intOrNull
        ?: throw IllegalArgumentException("Missing weather code")
    val label = when (code) {
        0 -> "Clear"
        1, 2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67, in 80..82 -> "Rain"
        in 71..77, 85, 86 -> "Snow"
        95, 96, 99 -> "Thunderstorm"
        else -> "Weather unavailable"
    }
    return Weather(temp.roundToInt(), code, label)
}

class WeatherClient(private val http: OkHttpClient) {
    suspend fun now(lat: Double, lon: Double): Weather = withContext(Dispatchers.IO) {
        require(lat in -90.0..90.0 && lon in -180.0..180.0)
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code"
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Weather HTTP ${response.code}")
            parseWeather(response.body?.string() ?: throw IOException("Empty weather response"))
        }
    }
}
