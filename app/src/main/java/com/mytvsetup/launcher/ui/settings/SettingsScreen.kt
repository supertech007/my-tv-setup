package com.mytvsetup.launcher.ui.settings

import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import android.net.Uri
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.mytvsetup.launcher.BuildConfig
import com.mytvsetup.launcher.apps.AppEntry
import com.mytvsetup.launcher.settings.Prefs
import com.mytvsetup.launcher.ui.*
import com.mytvsetup.launcher.content.ContentRuntime
import com.mytvsetup.launcher.history.KEEP_DAY_CHOICES
import com.mytvsetup.launcher.history.keepDaysLabel

private val sections = listOf("Profile", "Look", "Home cards", "Dock", "Languages", "Music app", "For you", "History", "Accounts", "About")

@Composable
fun SettingsScreen(prefs: Prefs, installed: List<AppEntry>, onUpdate: ((Prefs) -> Prefs) -> Unit,
                   onResetTaste: () -> Unit, onAccounts: () -> Unit, onBack: () -> Unit,
                   citySearch: suspend (String) -> List<City> = ::searchCities) {
    var section by remember { mutableStateOf("Profile") }
    var confirmReset by remember { mutableStateOf(false) }
    var switchBack by remember { mutableStateOf(false) }
    var resetDone by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var cleared by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val first = remember { FocusRequester() }
    BackHandler(switchBack) { switchBack = false }
    if (switchBack) {
        SettingsPage("How to switch back to your old home screen", "Your TV, your choice.", { switchBack = false }) {
            SettingsText("TV Settings → Apps → Default apps → Home app → your old launcher", 32)
            SettingsText("exact menu names may vary slightly", muted = true)
        }
        return
    }
    GlassCard(Modifier.offset(80.dp, 72.dp).size(340.dp, 936.dp), false) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Label("My TV")
            SettingsText("Settings", 44)
            Spacer(Modifier.height(16.dp))
            sections.forEachIndexed { index, label ->
                SettingsAction(label, if (index == 0) Modifier.focusRequester(first) else Modifier,
                    selected = section == label) { section = label }
            }
            Spacer(Modifier.weight(1f))
            SettingsAction("Back home", onClick = onBack)
        }
    }
    GlassCard(Modifier.offset(452.dp, 72.dp).size(1388.dp, 936.dp), false) {
        key(section) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(52.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                when (section) {
                    "Profile" -> ProfileSettings(prefs, onUpdate, citySearch)
                    "Look" -> LookSettings(prefs, onUpdate)
                    "Home cards" -> HomeCardSettings(prefs, onUpdate)
                    "Dock" -> DockSettings(prefs, installed, onUpdate)
                    "Languages" -> LanguageSettings(prefs, onUpdate)
                    "Music app" -> MusicAppSettings(prefs, installed, onUpdate)
                    "For you" -> {
                        SettingsHeader("Find your next favourite.", "Personalise learns from what you accept, reject and play.")
                        PersonaliseSwitch(prefs.personalise) { on -> onUpdate { it.copy(personalise = on) } }
                        SettingsText("Personalise is on by default. Turn it off whenever you like.", muted = true)
                        Spacer(Modifier.height(28.dp))
                        SettingsAction("Reset learned taste", Modifier.width(480.dp)) { confirmReset = true }
                        SettingsText("Start fresh by clearing your learned likes, dislikes and play signals.", muted = true)
                        if (resetDone) SettingsText("Learned taste reset.")
                    }
                    "History" -> HistorySettings(prefs, onUpdate) { confirmClear = true }
                    "Accounts" -> {
                        SettingsHeader("Make it personal.", "Manage the accounts behind your YouTube cards.")
                        SettingsAction("YouTube accounts", Modifier.fillMaxWidth(), onClick = onAccounts)
                    }
                    "About" -> {
                        SettingsHeader("My TV", "A quiet home for everything you love to watch.")
                        SettingsText("Version ${BuildConfig.VERSION_NAME}", 32)
                        SettingsText("Built by Mohan Minda · Shiv Shakti Software Solutions", 26)
                        SettingsText("No ads. No analytics. No trackers.", muted = true)
                        // TMDB's terms ask for this notice wherever its data is used.
                        SettingsText("This product uses the TMDB API but is not endorsed or certified by TMDB.", muted = true)
                        SettingsText("Catalogue from JustWatch. Playlists from JioSaavn.", muted = true)
                        SettingsText("Quick menu: hold Back for a second for Mute or Unmute and Screen off. Move with " +
                            "the arrows, OK to choose, Back to close. To use it inside other apps too, switch on " +
                            "TV Settings → Device Preferences → Accessibility → Services → My TV remote shortcuts.", muted = true)
                        SettingsAction("How to switch back to your old home screen", Modifier.fillMaxWidth()) { switchBack = true }
                    }
                }
            }
        }
    }
    if (confirmReset) SettingsDialog("Reset learned taste?", "Your learned likes, dislikes and play signals will be cleared.",
        "Reset taste", { confirmReset = false }) { onResetTaste(); confirmReset = false; resetDone = true }
    if (confirmClear) SettingsDialog("Clear history?", "Everything in your My TV history will be removed.",
        "Clear history", { confirmClear = false }) {
        Thread { ContentRuntime.history(context).clear() }.start(); confirmClear = false; cleared = true
    }
    if (cleared) LaunchedEffect(Unit) { Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show(); cleared = false }
    LaunchedEffect(Unit) { first.requestFocus() }
}

@Composable
private fun HistorySettings(prefs: Prefs, update: ((Prefs) -> Prefs) -> Unit, onClear: () -> Unit) {
    SettingsHeader("What you watched.", "Everything you open from My TV, kept as long as you choose.")
    Label("Keep history for")
    KEEP_DAY_CHOICES.forEach { days ->
        SettingsAction("${keepDaysLabel(days)}${if (prefs.historyDays == days) " · Selected" else ""}", Modifier.fillMaxWidth(),
            selected = prefs.historyDays == days) { update { it.copy(historyDays = days) } }
    }
    SettingsText("Older entries are removed automatically. Find History on the dock.", muted = true)
    Spacer(Modifier.height(28.dp))
    SettingsAction("Clear history", Modifier.width(480.dp), onClick = onClear)
}

@Composable
private fun HomeCardSettings(prefs: Prefs, update: ((Prefs) -> Prefs) -> Unit) {
    SettingsHeader("Room for what you love.", "Show or hide each card on your Home screen.")
    // Clock and Dock stay: they are the only ways into Settings and All apps.
    listOf("foryou" to "For you", "swipe" to "Swipe", "music" to "Music",
        "jarvis" to "Jarvis", "youtube" to "YouTube").forEach { (id, label) ->
        val hidden = id in prefs.hiddenCards || (id == "foryou" && "forYou" in prefs.hiddenCards)
        SettingsAction("$label · ${if (hidden) "Hidden" else "Shown"}", Modifier.fillMaxWidth()) {
            update { current ->
                val cards = if (id == "foryou") current.hiddenCards - "forYou" else current.hiddenCards
                current.copy(hiddenCards = if (hidden) cards - id else cards + id)
            }
        }
    }
}

@Composable
private fun LanguageSettings(prefs: Prefs, update: ((Prefs) -> Prefs) -> Unit) {
    SettingsHeader("In your languages.", "Choose the audio languages you want to discover.")
    Label("Video audio")
    listOf("hi" to "Hindi", "en" to "English").forEach { (code, label) ->
        SettingsAction("Video: $label · ${if (code in prefs.videoLangs) "On" else "Off"}", Modifier.fillMaxWidth()) {
            update { it.copy(videoLangs = it.videoLangs.toggle(code)) }
        }
    }
    Spacer(Modifier.height(22.dp))
    Label("Music")
    listOf("pa" to "Punjabi", "bgc" to "Haryanvi", "hi" to "Hindi", "en" to "English").forEach { (code, label) ->
        SettingsAction("Music: $label · ${if (code in prefs.musicLangs) "On" else "Off"}", Modifier.fillMaxWidth()) {
            update { it.copy(musicLangs = it.musicLangs.toggle(code)) }
        }
    }
}

private const val APPLE_MUSIC = "com.apple.android.music"
private val MUSIC_APPS = setOf("com.jio.media.jiobeats", "com.amazon.music.tv", "com.spotify.tv.android",
    "com.google.android.youtube.tvmusic", APPLE_MUSIC, "com.apple.android.music.tv")

@Composable
private fun MusicAppSettings(prefs: Prefs, installed: List<AppEntry>, update: ((Prefs) -> Prefs) -> Unit) {
    val context = LocalContext.current
    SettingsHeader("Press play, your way.", "Choose which app opens from your Music card.")
    val apps = installed.filter { it.pkg in MUSIC_APPS }
    apps.forEach { app ->
        SettingsAction("${app.label}${if (prefs.musicApp == app.pkg) " · Selected" else ""}", Modifier.fillMaxWidth(),
            selected = prefs.musicApp == app.pkg) { update { it.copy(musicApp = app.pkg) } }
    }
    SettingsText("Amazon Music can't be sent to a playlist, so playlists picked here open in JioSaavn when it's installed.", muted = true)
    // Apple Music is always offered: when missing, its Play Store page shows whether this TV can install it.
    if (apps.none { it.pkg.startsWith(APPLE_MUSIC) }) {
        SettingsAction("Apple Music · not installed, get it", Modifier.fillMaxWidth()) {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$APPLE_MUSIC"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { Toast.makeText(context, "Play Store isn't available", Toast.LENGTH_LONG).show() }
        }
    }
}

private fun Set<String>.toggle(value: String) = if (value in this) this - value else this + value
