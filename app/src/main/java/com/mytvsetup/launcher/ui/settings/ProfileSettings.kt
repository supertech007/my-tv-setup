package com.mytvsetup.launcher.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.mytvsetup.launcher.ui.Outfit
import androidx.compose.ui.unit.dp
import com.mytvsetup.launcher.settings.Prefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun ProfileSettings(prefs: Prefs, update: ((Prefs) -> Prefs) -> Unit,
                             citySearch: suspend (String) -> List<City>) {
    var name by remember(prefs.name) { mutableStateOf(prefs.name) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<City>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    SettingsHeader("Make yourself at home.", "Your name in every greeting. Your city in the forecast.")
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        OutlinedTextField(name, { name = it }, Modifier.weight(1f).heightIn(min = 86.dp).testTag("profile-name"),
            label = { Text("Your name", fontFamily = Outfit, fontSize = 22.sp) }, singleLine = true,
            textStyle = TextStyle(fontFamily = Outfit, fontSize = 26.sp, color = Color.White))
        SettingsAction("Save name", Modifier.width(220.dp)) {
            if (name.isNotBlank()) { update { it.copy(name = name.trim()) }; message = "Name saved" }
            else message = "Enter a name first"
        }
    }
    Spacer(Modifier.height(26.dp))
    SettingsText("Weather city", 30)
    SettingsText(if (prefs.city.isBlank()) "Choose a city to see your weather." else "Current city: ${prefs.city}", muted = true)
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        OutlinedTextField(query, { query = it }, Modifier.weight(1f).heightIn(min = 86.dp).testTag("city-query"),
            label = { Text("Search by city name", fontFamily = Outfit, fontSize = 22.sp) }, singleLine = true,
            textStyle = TextStyle(fontFamily = Outfit, fontSize = 26.sp, color = Color.White))
        SettingsAction("Search cities", Modifier.width(250.dp)) {
            search?.cancel()
            results = emptyList()
            if (query.isBlank()) { message = "Enter a city name"; loading = false }
            else {
                val requested = query.trim()
                search = scope.launch {
                    loading = true; message = null
                    try {
                        results = citySearch(requested)
                        message = if (results.isEmpty()) "No cities found. Try another name." else "Choose your city below."
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { message = "City search unavailable. Check your connection and try again." }
                    finally { loading = false }
                }
            }
        }
    }
    if (loading) SettingsText("Searching cities…", muted = true)
    message?.let { SettingsText(it, muted = true) }
    results.forEach { city ->
        SettingsAction(city.label, Modifier.fillMaxWidth()) {
            update { it.copy(city = city.name, lat = city.lat, lon = city.lon) }
            results = emptyList()
            message = "Weather city saved: ${city.name}"
        }
    }
    SettingsText("City search by Open-Meteo", 20, muted = true)
}
