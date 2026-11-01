package com.mytvsetup.launcher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import com.mytvsetup.launcher.apps.*
import com.mytvsetup.launcher.settings.*
import com.mytvsetup.launcher.ui.RemoteTile

@Composable
internal fun DockSettings(prefs: Prefs, installed: List<AppEntry>, update: ((Prefs) -> Prefs) -> Unit) {
    var held by remember { mutableStateOf<String?>(null) }
    val apps = remember(installed, prefs.dockOrder) { dockApps(installed, prefs.dockOrder, emptySet()) }
    BackHandler(held != null) { held = null }
    SettingsHeader("Your favourites, in order.", "Hold OK to pick up an app. Move with Left / Right, then OK to drop.")
    if (apps.isEmpty()) SettingsText("No apps available yet.", muted = true)
    apps.forEachIndexed { index, app ->
        key(app.pkg) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                RemoteTile(Modifier.weight(1f).onPreviewKeyEvent { event ->
                    if (held != app.pkg || event.key !in listOf(Key.DirectionLeft, Key.DirectionRight)) false
                    else {
                        if (event.type == KeyEventType.KeyDown) update { current ->
                            val order = dockApps(installed, current.dockOrder, emptySet()).map { it.pkg }
                            current.copy(dockOrder = moveInDock(order, app.pkg, if (event.key == Key.DirectionLeft) -1 else 1))
                        }
                        true
                    }
                }, onClick = { held = if (held == app.pkg) null else app.pkg }, onHold = { held = app.pkg }) {
                    Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha = if (held == app.pkg) .28f else .09f))
                        .padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        SettingsText("${index + 1}", muted = true)
                        SettingsText(app.label, modifier = Modifier.weight(1f))
                        if (held == app.pkg) SettingsText("← Move →  ·  OK to drop", 22)
                    }
                }
                val hidden = app.pkg in prefs.hiddenApps
                SettingsAction("${if (hidden) "Show" else "Hide"} ${app.label}", Modifier.width(320.dp)) {
                    update { it.copy(hiddenApps = if (hidden) it.hiddenApps - app.pkg else it.hiddenApps + app.pkg) }
                }
            }
        }
    }
}
