package com.mytvsetup.launcher.ui.home

import android.content.Intent
import android.widget.Toast
import com.mytvsetup.launcher.ai.*
import com.mytvsetup.launcher.ui.ai.AssistantScreen
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import com.mytvsetup.launcher.apps.*
import com.mytvsetup.launcher.content.*
import com.mytvsetup.launcher.settings.*
import com.mytvsetup.launcher.ui.*
import com.mytvsetup.launcher.ui.swipe.*
import com.mytvsetup.launcher.ui.settings.*
import com.mytvsetup.launcher.ui.allapps.AllAppsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import com.mytvsetup.launcher.music.MusicCatalog
import com.mytvsetup.launcher.music.Playlist
import com.mytvsetup.launcher.ui.youtube.YouTubeScreen
import com.mytvsetup.launcher.youtube.YouTubeRuntime
import com.mytvsetup.launcher.youtube.YtVideo
import java.time.LocalDate

@Composable
fun HomeScreen(prefs: Prefs, source: HomeContentSource, installed: List<AppEntry>, imageLoader: ImageLoader,
               nav: Nav, onPlay: (Title) -> Unit, onApp: (String, String) -> Unit,
               featured: Playlist? = null, latestVideo: YtVideo? = null, onMusic: () -> Unit = {},
               onPersonalise: (Boolean) -> Unit = source::setPersonalise, onPrefs: ((Prefs) -> Prefs) -> Unit = {}) {
    val hidden = prefs.hiddenCards
    // Clock and Dock are never hidden: they lead to Settings and All apps.
    fun card(id: String) = id == "clock" || id == "dock" || id !in hidden
    // Coordinates are the approved 1920×1080 design, uniformly scaled by the shell.
    fun at(x: Int, y: Int, w: Int, h: Int) = Modifier.offset(x.dp, y.dp).size(w.dp, h.dp)
    if (card("clock")) ClockCard(prefs, at(80, 72, 700, 640)) { nav.push(Screen.Settings) }
    if (card("foryou") && card("forYou")) ForYouCard(source, imageLoader, at(830, 72, 760, 400), onPlay, onPersonalise) { nav.push(Screen.Browse) }
    if (card("swipe")) SwipeCard(source, imageLoader, at(1620, 72, 220, 400)) { nav.push(Screen.Swipe) }
    if (card("music")) MusicCard(at(830, 502, 320, 210), featured, imageLoader) { onMusic() }
    val context = LocalContext.current
    if (card("jarvis")) JarvisCard(installed.mapTo(mutableSetOf()) { it.pkg }, at(1170, 502, 360, 210)) { assistant ->
        when (val route = jarvisRoute(assistant, installed.mapTo(mutableSetOf()) { it.pkg })) {
            is JarvisRoute.App -> onApp(route.pkg, assistant.name)
            is JarvisRoute.Web -> nav.push(Screen.Assistant(assistant.name))
            JarvisRoute.GoogleAssistant -> runCatching {
                context.startActivity(Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { Toast.makeText(context, "Google Assistant isn't available on this TV", Toast.LENGTH_LONG).show() }
        }
    }
    if (card("youtube")) YouTubeCard(at(1550, 502, 290, 210), latestVideo, imageLoader) { nav.push(Screen.YouTube(null)) }
    if (card("dock")) Dock(installed, prefs, at(80, 752, 1760, 256), { onApp(it.pkg, it.label) },
        { nav.push(Screen.AllApps) }, { nav.push(Screen.Settings) }, onPrefs)
}

@Composable
fun TvApp(nav: Nav, prefsRepository: PrefsRepository, source: HomeContentSource, catalogRevision: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Draw black until stored prefs load, so the wrong wallpaper or cards never flash on cold start.
    val loadedPrefs by prefsRepository.prefs.collectAsState<Prefs, Prefs?>(null)
    val prefs = loadedPrefs ?: run { Box(Modifier.fillMaxSize().background(Color.Black)); return }
    val stack by nav.stack.collectAsState()
    var installed by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    val images = remember { HomeNetwork.imageLoader(context.applicationContext) }
    val youTube = remember { YouTubeRuntime.get(context) }
    val videos by youTube.feed.videos.collectAsState(emptyList())
    val music = remember { ContentRuntime.music(context) }
    val playlists by music.playlists.collectAsState()
    val featured = remember(prefs.musicLangs, playlists) { runCatching { music.featuredPlaylist(LocalDate.now(), prefs.musicLangs) }.getOrNull() }
    LaunchedEffect(stack.last()) { if (stack.last() == Screen.Home) runCatching { youTube.feed.onHomeShown() } }
    DisposableEffect(images) { onDispose { images.shutdown() } }
    LaunchedEffect(catalogRevision) { installed = withContext(Dispatchers.IO) { AppCatalog(context).installed() } }
    fun feedback(result: LaunchResult) {
        if (result is LaunchResult.NotInstalled) Toast.makeText(context,
            "${result.label} isn't installed on this TV yet", Toast.LENGTH_LONG).show()
    }
    fun openApp(pkg: String, label: String) { feedback(Launcher.openApp(context, pkg, label)) }
    val play: (Title) -> Unit = play@{ title ->
        if (title.kind == Kind.MUSIC) {
            val playlist = music.forLangs(prefs.musicLangs).firstOrNull { it.id == title.id.removePrefix("music:") }
            if (playlist != null) feedback(music.open(context, playlist, prefs.musicApp))
            return@play
        }
        val servicePackages = mapOf("prv" to "com.amazon.amazonvideo.livingroom", "jhs" to "in.startv.hotstar",
            "snl" to "com.sonyliv", "zee" to "com.graymatrix.did", "atp" to "com.apple.atve.androidtv.appletv")
        val offer = title.offers.firstOrNull { o -> o.service in servicePackages && (o.audio.any { it in prefs.videoLangs } || (o.audio.isEmpty() && title.originalLanguage in prefs.videoLangs)) }
        if (offer != null) {
            val result = Launcher.openUrlIn(context, servicePackages.getValue(offer.service),
                playUrl(offer.service, offer.url), serviceName(offer.service), fresh = needsFreshStart(offer.service))
            feedback(result)
            if (result == LaunchResult.Ok) scope.launch { source.decide(title, Signal.PLAY) }
        } else Toast.makeText(context, "No confirmed Hindi or English offer available", Toast.LENGTH_LONG).show()
    }
    TvTheme(prefs.accent) {
        WallpaperStage(prefs.wallpaper) {
            when (val screen = stack.last()) {
                Screen.Home -> HomeScreen(prefs, source, installed, images, nav, play, ::openApp, featured,
                    videos.firstOrNull(), { featured?.let { feedback(music.open(context, it, prefs.musicApp)) } },
                    { on -> source.setPersonalise(on) }, // the repository persists it
                    { update -> scope.launch { prefsRepository.update(update) } })
                Screen.Settings -> SettingsScreen(prefs, installed,
                    { update -> scope.launch { prefsRepository.update(update) } }, source::resetTaste,
                    { nav.push(Screen.Accounts) }, { nav.back() })
                Screen.AllApps -> AllAppsScreen(installed, prefs,
                    { update -> scope.launch { prefsRepository.update(update) } }, { openApp(it.pkg, it.label) }, { nav.back() })
                Screen.Accounts -> AccountsScreen({ nav.back() }, youTube)
                Screen.Swipe -> SwipeRoute(source, images, nav)
                is Screen.Assistant -> ASSISTANTS.firstOrNull { it.name == screen.name }?.webUrl
                    ?.let { AssistantScreen(screen.name, it) { nav.back() } } ?: nav.back()
                Screen.Browse -> BrowseScreen(source, images, play) { on -> source.setPersonalise(on) }
                is Screen.YouTube -> YouTubeScreen({ nav.back() }, { nav.push(Screen.Accounts) }, youTube)
            }
        }
    }
}

@Composable
internal fun WallpaperStage(wallpaperId: String, content: @Composable BoxScope.() -> Unit) {
    val wallpaper = WALLPAPERS.firstOrNull { it.id == wallpaperId } ?: WALLPAPERS.first()
    val background = ImageBitmap.imageResource(wallpaper.image)
    val blur = ImageBitmap.imageResource(wallpaper.blurred)
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val scale = minOf(constraints.maxWidth / 1920f, constraints.maxHeight / 1080f)
        val stage = IntSize((1920 * scale).roundToInt(), (1080 * scale).roundToInt())
        val density = LocalDensity.current
        Box(Modifier.size(with(density) { stage.width.toDp() }, with(density) { stage.height.toDp() })) {
            CompositionLocalProvider(LocalDensity provides Density(scale, 1f), LocalGlassWallpaper provides blur, LocalStageSize provides stage) {
                Box(Modifier.requiredSize(1920.dp, 1080.dp)) {
                    Image(background, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF080E18).copy(alpha = .15f), Color(0xFF080E18).copy(alpha = .45f)))))
                    content()
                }
            }
        }
    }
}

@Composable
private fun ShellPlaceholder(label: String, title: String, detail: String, nav: Nav, settings: (() -> Unit)? = null) {
    GlassCard(Modifier.offset(320.dp, 230.dp).size(1280.dp, 620.dp), false) {
        Column(Modifier.padding(64.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Label(label)
            Text(title, fontFamily = Outfit, fontSize = 70.sp, color = Color.White)
            Text(detail, fontFamily = Outfit, fontSize = 28.sp, color = Color.White.copy(alpha = .85f))
            if (settings != null) Chip("Open Settings") { settings() }
            Chip("Back home") { nav.home() }
        }
    }
}

@Composable
private fun SwipeRoute(source: HomeContentSource, images: ImageLoader, nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Swipe sets its own filters; give Home back the ones that were set.
    val homeFilters = remember(source) { source.filters.value }
    DisposableEffect(source) { onDispose { source.setFilters(homeFilters) } }
    val viewModel = remember(source) { SwipeViewModel(source,
        SwipeLauncher { pkg, url, label ->
        Launcher.openUrlIn(context, pkg, url, label, fresh = pkg == "com.amazon.amazonvideo.livingroom") },
        (source as? ContentRepository)?.tasteSummary ?: defaultTasteSummary(source), scope) }
    SwipeScreen(viewModel, images) { nav.back() }
}
