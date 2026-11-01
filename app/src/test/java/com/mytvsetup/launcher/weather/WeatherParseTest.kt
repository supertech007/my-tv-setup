package com.mytvsetup.launcher.weather
import org.junit.Assert.*
import org.junit.Test
class WeatherParseTest {
 @Test fun clearCurrentWeather() {
  val result = parseWeather("""{"current":{"temperature_2m":24.0,"weather_code":0}}""")
  assertEquals(Weather(24, 0, "Clear"), result)
 }
 @Test fun roundsTemperatureAndLabelsRain() {
  assertEquals(Weather(25, 61, "Rain"), parseWeather("""{"current":{"temperature_2m":24.6,"weather_code":61}}"""))
 }
 @Test(expected = IllegalArgumentException::class) fun missingCurrentIsNotZeroDegrees() { parseWeather("{}") }
}
