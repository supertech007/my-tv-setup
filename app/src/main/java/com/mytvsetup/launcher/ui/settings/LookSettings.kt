package com.mytvsetup.launcher.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytvsetup.launcher.settings.Prefs
import com.mytvsetup.launcher.ui.*

@Composable
internal fun LookSettings(prefs: Prefs, update: ((Prefs) -> Prefs) -> Unit) {
    SettingsHeader("A view that's yours.", "Choose a wallpaper and a little colour.")
    Label("Wallpaper")
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        WALLPAPERS.forEach { wallpaper ->
            FocusTile(Modifier.weight(1f).height(195.dp), radius = 22,
                onClick = { update { it.copy(wallpaper = wallpaper.id) } }) {
                Image(painterResource(wallpaper.image), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Text(wallpaper.id.replaceFirstChar { it.uppercase() }, Modifier.align(Alignment.BottomStart)
                    .fillMaxWidth().background(Color.Black.copy(alpha = .48f)).padding(18.dp),
                    color = Color.White, fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 25.sp)
                if (prefs.wallpaper == wallpaper.id) Text("✓", Modifier.align(Alignment.TopEnd).padding(18.dp),
                    color = Color.White, fontSize = 28.sp)
            }
        }
    }
    Spacer(Modifier.height(32.dp))
    Label("Accent")
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        listOf("ice", "rose", "gold", "mint").forEach { accent ->
            FocusTile(Modifier.weight(1f), radius = 22, onClick = { update { it.copy(accent = accent) } }) {
                Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .1f)).padding(22.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(26.dp).background(ACCENTS.getValue(accent), CircleShape))
                    SettingsText(accent.replaceFirstChar { it.uppercase() })
                    if (prefs.accent == accent) SettingsText("✓")
                }
            }
        }
    }
}
