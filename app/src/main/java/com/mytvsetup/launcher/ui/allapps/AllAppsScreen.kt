package com.mytvsetup.launcher.ui.allapps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytvsetup.launcher.apps.AppEntry
import com.mytvsetup.launcher.settings.Prefs
import com.mytvsetup.launcher.ui.*
import com.mytvsetup.launcher.ui.settings.SettingsDialog

@Composable
fun AllAppsScreen(installed: List<AppEntry>, prefs: Prefs, onUpdate: ((Prefs) -> Prefs) -> Unit,
                  onOpen: (AppEntry) -> Unit, onBack: () -> Unit) {
    var addApp by remember { mutableStateOf<AppEntry?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    val apps = remember(installed) { installed.distinctBy { it.pkg }.sortedBy { it.label.lowercase() } }
    GlassCard(Modifier.offset(80.dp, 72.dp).size(1760.dp, 936.dp), false) {
        Column(Modifier.fillMaxSize().padding(52.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Label("My TV")
                    Text("All apps", color = Color.White, fontFamily = Outfit, fontSize = 60.sp)
                }
                Chip("Back home", onClick = onBack)
            }
            Text(message ?: "Everything on your TV. OK to open · Hold OK to add to dock", color = Color.White.copy(alpha = .8f),
                fontFamily = Outfit, fontSize = 26.sp)
            if (apps.isEmpty()) Text("No apps found yet.", color = Color.White, fontFamily = Outfit, fontSize = 32.sp)
            LazyVerticalGrid(GridCells.Fixed(5), Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp), verticalArrangement = Arrangement.spacedBy(36.dp)) {
                // Deliberately do not filter hiddenApps: hidden dock entries are still available here.
                items(apps, key = { it.pkg }) { app ->
                    RemoteTile(Modifier.height(210.dp).then(if (app == apps.first()) Modifier.focusRequester(first) else Modifier),
                        onClick = { onOpen(app) }, onHold = { addApp = app }) {
                        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF223D58), Color(0xFF101C30)))))
                        if (app.banner != null) Image(app.banner, null, Modifier.fillMaxWidth().height(158.dp), contentScale = ContentScale.Crop)
                        else app.icon?.let { Image(it, null, Modifier.align(Alignment.TopCenter).padding(top = 28.dp).size(96.dp)) }
                        Text(app.label, Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .background(Color.Black.copy(alpha = .25f)).padding(14.dp), color = Color.White, fontFamily = Outfit,
                            fontSize = 24.sp, textAlign = TextAlign.Center, maxLines = 2)
                    }
                }
            }
        }
    }
    addApp?.let { app ->
        val present = app.pkg in prefs.dockOrder && app.pkg !in prefs.hiddenApps
        SettingsDialog(app.label, if (present) "Keep this app in your favourites." else "Show this app in your Home dock.",
            "Add to dock", { addApp = null }) {
            onUpdate { it.copy(dockOrder = (it.dockOrder + app.pkg).distinct(), hiddenApps = it.hiddenApps - app.pkg) }
            message = "${app.label} added to dock"
            addApp = null
        }
    }
    LaunchedEffect(apps.isNotEmpty()) { if (apps.isNotEmpty()) first.requestFocus() }
}
