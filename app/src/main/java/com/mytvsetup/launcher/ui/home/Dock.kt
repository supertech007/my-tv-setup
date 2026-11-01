package com.mytvsetup.launcher.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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

@Composable
fun Dock(installed: List<AppEntry>, prefs: Prefs, modifier: Modifier, onApp: (AppEntry) -> Unit, onAllApps: () -> Unit,
         onSettings: () -> Unit, update: ((Prefs) -> Prefs) -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    // Hold OK on an app to edit it in place: ◀ / ▶ move it, ✕ takes it off the dock (Settings brings it back).
    var editing by remember { mutableStateOf<String?>(null) }
    val apps = remember(installed, prefs.dockOrder, prefs.hiddenApps) { dockApps(installed, prefs.dockOrder, prefs.hiddenApps) }
    BackHandler(editing != null) { editing = null }
    fun move(pkg: String, delta: Int) = update { current ->
        current.copy(dockOrder = moveInDock(dockApps(installed, current.dockOrder, current.hiddenApps).map { it.pkg }, pkg, delta))
    }
    GlassCard(modifier.onFocusChanged { focused = it.hasFocus }, focused) {
        LazyRow(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 44.dp, vertical = 53.dp),
            horizontalArrangement = Arrangement.spacedBy(30.dp), verticalAlignment = Alignment.CenterVertically) {
            items(apps, key = { it.pkg }) { app ->
                val isEditing = editing == app.pkg
                val tile = remember { FocusRequester() }
                val remove = remember { FocusRequester() }
                var wasEditing by remember { mutableStateOf(false) }
                // Finishing an edit returns focus to the app's tile instead of letting it fall elsewhere.
                LaunchedEffect(isEditing) {
                    runCatching { if (isEditing) remove.requestFocus() else if (wasEditing) tile.requestFocus() }
                    wasEditing = isEditing
                }
                Box(Modifier.size(250.dp, 150.dp)) {
                    RemoteTile(Modifier.fillMaxSize().focusRequester(tile).focusProperties { canFocus = !isEditing },
                        onClick = { onApp(app) }, onHold = { editing = app.pkg }) {
                        DockArt(app)
                        if (isEditing) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)))
                    }
                    if (isEditing) {
                        EditButton("◀", "Move ${app.label} left", Modifier.align(Alignment.CenterStart).padding(start = 12.dp)) { move(app.pkg, -1) }
                        EditButton("✕", "Remove ${app.label} from the dock", Modifier.align(Alignment.TopCenter).padding(top = 10.dp)
                            .focusRequester(remove)) { editing = null; update { it.copy(hiddenApps = it.hiddenApps + app.pkg) } }
                        EditButton("▶", "Move ${app.label} right", Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)) { move(app.pkg, 1) }
                        Text("Back = done", Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), fontFamily = Outfit,
                            fontSize = 17.sp, color = Color.White)
                    }
                }
            }
            item { DockShortcut("▦", "All apps", onAllApps) }
            item { DockShortcut("⚙", "Settings", onSettings) }
        }
    }
}

@Composable
private fun DockShortcut(icon: String, label: String, action: () -> Unit) {
    FocusTile(Modifier.size(170.dp, 150.dp), radius = 30, onClick = action) {
        Column(Modifier.fillMaxSize().background(Color.White.copy(alpha = .12f)),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(icon, fontSize = 50.sp, color = Color.White)
            Text(label, fontFamily = Outfit, fontSize = 22.sp, color = Color.White)
        }
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
