package com.mytvsetup.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytvsetup.launcher.settings.Prefs
import com.mytvsetup.launcher.ui.*
import com.mytvsetup.launcher.weather.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ClockCard(prefs: Prefs, modifier: Modifier, onSettings: () -> Unit) {
    var time by remember { mutableStateOf(LocalDateTime.now()) }
    var weather by remember(prefs.lat, prefs.lon) { mutableStateOf<Weather?>(null) }
    var unavailable by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { time = LocalDateTime.now(); delay(10_000) } }
    LaunchedEffect(prefs.lat, prefs.lon) {
        val lat = prefs.lat; val lon = prefs.lon
        if (lat != null && lon != null) while (true) {
            try { weather = WeatherClient(HomeNetwork.http).now(lat, lon); unavailable = false }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { unavailable = true }
            delay(30 * 60_000L)
        }
    }
    ActionGlass(modifier, onSettings) {
        Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 52.dp)) {
            Text(greeting(time.hour, prefs.name), color = Color.White.copy(alpha = .92f), fontFamily = Outfit, fontSize = 28.sp, lineHeight = 34.sp)
            Spacer(Modifier.height(26.dp))
            Row(Modifier.height(200.dp), verticalAlignment = Alignment.Bottom) {
                Text(time.format(DateTimeFormatter.ofPattern("h:mm")), fontFamily = Outfit, fontSize = 190.sp,
                    fontWeight = FontWeight.Light, letterSpacing = (-8).sp, lineHeight = 200.sp, color = Color.White)
                Text(if (time.hour < 12) "AM" else "PM", Modifier.padding(start = 14.dp, bottom = 28.dp),
                    fontFamily = Outfit, fontSize = 44.sp, color = Color.White)
            }
            Spacer(Modifier.height(18.dp))
            Text(time.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)), fontFamily = Outfit, fontSize = 36.sp, lineHeight = 44.sp, color = Color.White)
            Spacer(Modifier.height(34.dp))
            Box(Modifier.fillMaxWidth().height(1.5.dp).background(Color.White.copy(alpha = .35f)))
            Spacer(Modifier.height(32.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    if (prefs.lat != null && prefs.lon != null) Text(weather?.let { "${it.tempC}°" } ?: "—°", Modifier.height(110.dp), fontFamily = Outfit, fontSize = 100.sp,
                        fontWeight = FontWeight.Light, lineHeight = 110.sp, color = Color.White)
                    Text(when {
                        prefs.lat == null || prefs.lon == null -> "Set your city in Settings"
                        unavailable -> if (weather == null) "Weather unavailable · Offline" else "${weather?.label} · Saved"
                        weather == null -> "Loading weather…"
                        else -> "${weather?.label} · ${prefs.city.ifBlank { "Home" }}"
                    }, fontFamily = Outfit, fontSize = 25.sp, lineHeight = 30.sp, color = Color.White.copy(alpha = .9f))
                }
                if (weather?.code == 0) Box(Modifier.size(100.dp).background(Brush.radialGradient(
                    listOf(Color(0xFFFFF6C9), Color(0xFFFFD36B), Color(0xFFFFAD3B))), CircleShape))
            }
        }
    }
}
