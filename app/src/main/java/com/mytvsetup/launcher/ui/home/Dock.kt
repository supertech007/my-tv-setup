package com.mytvsetup.launcher.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytvsetup.launcher.apps.*
import com.mytvsetup.launcher.settings.Prefs
import com.mytvsetup.launcher.ui.*
import androidx.activity.compose.BackHandler
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.mytvsetup.launcher.settings.moveInDock
import com.mytvsetup.launcher.audio.Mute
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalContext

@Composable
fun Dock(installed: List<AppEntry>, prefs: Prefs, modifier: Modifier, onApp: (AppEntry) -> Unit, onAllApps: () -> Unit,
         onSettings: () -> Unit, update: ((Prefs) -> Prefs) -> Unit = {}, onScreenOff: () -> Unit = {},
         onHistory: () -> Unit = {}) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    // The Mute tile names what pressing it will do; mute can also change from the remote, so keep checking.
    var muted by remember { mutableStateOf(Mute.isMuted(context)) }
    LaunchedEffect(Unit) { while (true) { muted = Mute.isMuted(context); delay(1_000) } }
    // Hold OK on any item to edit it in place: ◀ / ▶ move it; apps also get ✕ to leave the dock (Settings brings
    // them back). All apps, History, Screen off and Settings move but never leave: they are the ways into those screens.
    var editing by remember { mutableStateOf<String?>(null) }
    val apps = remember(installed, prefs.dockOrder, prefs.hiddenApps) { dockApps(installed, prefs.dockOrder, prefs.hiddenApps) }
    val byId = remember(apps) { apps.associateBy { it.pkg } }
    val ids = remember(apps, prefs.dockOrder) { dockIds(apps.map { it.pkg }, prefs.dockOrder) }
    BackHandler(editing != null) { editing = null }
    fun move(id: String, delta: Int) = update { current ->
        current.copy(dockOrder = moveInDock(
            dockIds(dockApps(installed, current.dockOrder, current.hiddenApps).map { it.pkg }, current.dockOrder), id, delta))
    }
    // The dock goes round: Left on the first tile lands on the last, Right on the last back on the first.
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val tiles = remember { mutableMapOf<String, FocusRequester>() }
    fun jump(index: Int) = scope.launch {
        list.scrollToItem(index)
        repeat(3) { withFrameNanos { } }
        runCatching { tiles[ids[index]]?.requestFocus() }
    }
    GlassCard(modifier.onFocusChanged { focused = it.hasFocus }, focused) {
        LazyRow(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(horizontal = 44.dp, vertical = 53.dp),
            horizontalArrangement = Arrangement.spacedBy(30.dp), verticalAlignment = Alignment.CenterVertically) {
            itemsIndexed(ids, key = { _, it -> it }) { index, id ->
                val app = byId[id]
                val wrap = Modifier.focusRequester(tiles.getOrPut(id) { FocusRequester() }).onPreviewKeyEvent { event ->
                    if (editing != null || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        event.key == Key.DirectionLeft && index == 0 && ids.size > 1 -> { jump(ids.lastIndex); true }
                        event.key == Key.DirectionRight && index == ids.lastIndex && ids.size > 1 -> { jump(0); true }
                        else -> false
                    }
                }
                val (label, open) = when (id) {
                    ALL_APPS -> "All apps" to onAllApps
                    HISTORY -> "History" to onHistory
                    SCREEN_OFF -> "Screen off" to onScreenOff
                    MUTE -> (if (muted) "Unmute" else "Mute") to { Mute.toggle(context); muted = !muted }
                    SETTINGS -> "Settings" to onSettings
                    else -> app!!.label to { onApp(app) }
                }
                DockItem(id, label, wrap, width = if (app != null) 250 else 170, editing = editing == id,
                    removable = app != null, focused = focused, onOpen = open, onHold = { editing = id },
                    onMove = { move(id, it) },
                    onRemove = { editing = null; update { it.copy(hiddenApps = it.hiddenApps + id) } }) {
                    if (app != null) DockArt(app) else ShortcutArt(id, label, muted)
                }
            }
        }
    }
}

@Composable
private fun DockItem(id: String, label: String, tileModifier: Modifier, width: Int, editing: Boolean, removable: Boolean, focused: Boolean,
                     onOpen: () -> Unit, onHold: () -> Unit, onMove: (Int) -> Unit, onRemove: () -> Unit,
                     art: @Composable BoxScope.() -> Unit) {
    val tile = remember { FocusRequester() }
    val first = remember { FocusRequester() }
    var wasEditing by remember { mutableStateOf(false) }
    // Finishing an edit returns focus to the tile instead of letting it fall elsewhere.
    LaunchedEffect(editing) {
        runCatching { if (editing) first.requestFocus() else if (wasEditing) tile.requestFocus() }
        wasEditing = editing
    }
    Box(Modifier.size(width.dp, 150.dp)) {
        RemoteTile(Modifier.fillMaxSize().then(tileModifier).focusRequester(tile).focusProperties { canFocus = !editing },
            onClick = onOpen, onHold = onHold) {
            art()
            if (editing) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)))
        }
        if (editing) {
            EditButton("◀", "Move $label left", Modifier.align(Alignment.CenterStart).padding(start = 10.dp)
                .then(if (removable) Modifier else Modifier.focusRequester(first))) { onMove(-1) }
            if (removable) EditButton("✕", "Remove $label from the dock", Modifier.align(Alignment.TopCenter)
                .padding(top = 10.dp).focusRequester(first)) { onRemove() }
            EditButton("▶", "Move $label right", Modifier.align(Alignment.CenterEnd).padding(end = 10.dp)) { onMove(1) }
            Text("Back = done", Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), fontFamily = Outfit,
                fontSize = 17.sp, color = Color.White)
        }
    }
}

@Composable
private fun BoxScope.ShortcutArt(id: String, label: String, muted: Boolean) {
    Column(Modifier.fillMaxSize().background(Color.White.copy(alpha = .12f)),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(when (id) { ALL_APPS -> "▦"; HISTORY -> "↺"; SCREEN_OFF -> "☾"; MUTE -> if (muted) "🔊" else "🔇"; else -> "⚙" }, fontSize = 50.sp, color = Color.White)
        Text(label, fontFamily = Outfit, fontSize = 22.sp, color = Color.White)
    }
}

@Composable
private fun BoxScope.DockArt(app: AppEntry) {
    val colors = when (app.pkg) {
        "in.startv.hotstar" -> listOf(Color(0xFF1B46E0), Color(0xFFC0189B))
        "com.graymatrix.did" -> listOf(Color(0xFF8230C6), Color(0xFF3A0D6B))
        else -> listOf(Color(0xFF1C334C), Color(0xFF0F171E))
    }
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(colors)))
    if (app.banner != null) Image(app.banner, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    else Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
        app.icon?.let { Image(it, null, Modifier.size(64.dp)) }
        Text(app.label, Modifier.padding(horizontal = 12.dp), textAlign = TextAlign.Center, fontFamily = Outfit,
            fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 2)
    }
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color.White.copy(alpha = .16f), Color.Transparent))))
}

@Composable
private fun EditButton(symbol: String, description: String, modifier: Modifier, onClick: () -> Unit) {
    // The OK press that opened editing is still held when these buttons appear: act only on a fresh press.
    var pressed by remember { mutableStateOf(false) }
    FocusTile(modifier.size(56.dp).semantics { contentDescription = description }.onPreviewKeyEvent { event ->
        if (event.key !in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)) return@onPreviewKeyEvent false
        when (event.type) {
            KeyEventType.KeyDown -> if (event.nativeKeyEvent.repeatCount == 0) pressed = true
            KeyEventType.KeyUp -> if (pressed) { pressed = false; onClick() }
        }
        true
    }, radius = 28, onClick = onClick) {
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .92f)), contentAlignment = Alignment.Center) {
            Text(symbol, fontSize = 26.sp, color = Color(0xFF0D1522), fontWeight = FontWeight.Bold)
        }
    }
}
