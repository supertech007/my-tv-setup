package com.mytvsetup.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytvsetup.launcher.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.ImageLoader
import coil.compose.AsyncImage

@Composable
fun MusicCard(modifier: Modifier, featured: com.mytvsetup.launcher.music.Playlist?, imageLoader: ImageLoader? = null, onOpen: () -> Unit) {
    ActionGlass(modifier, onOpen) {
        Row(Modifier.fillMaxSize().padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.size(96.dp, 132.dp).background(Brush.linearGradient(listOf(Color(0xFFFF9A3C), Color(0xFFC4161C))), RoundedCornerShape(20.dp))) {
                Text(featured?.name?.uppercase()?.replace(" ", "\n") ?: "PLAY\nYOUR\nMUSIC", Modifier.align(Alignment.BottomStart).padding(12.dp),
                    fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 21.sp, color = Color.White)
                // JioSaavn cover art, when the playlist came from a refresh, sits over the text fallback.
                if (featured?.image != null && imageLoader != null) AsyncImage(featured.image, featured.name, imageLoader,
                    Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop)
            }
            Column {
                Label("Music")
                Text(featured?.name ?: "Your rhythm.", Modifier.padding(top = 10.dp), fontFamily = Outfit, fontSize = 24.sp, lineHeight = 30.sp, maxLines = 1, color = Color.White)
                Text(featured?.let { languageName(it.lang) + " · " + (it.tags.firstOrNull() ?: "Playlist") } ?: "Choose languages\nin Settings", Modifier.padding(top = 8.dp), fontFamily = Outfit, fontSize = 18.sp, lineHeight = 22.sp, color = Color.White.copy(alpha = .8f))
                Text("Open music  ↗", Modifier.padding(top = 12.dp), fontFamily = Outfit, fontSize = 17.sp, lineHeight = 21.sp, color = Color(0xFFCFEDFF))
            }
        }
    }
}

internal fun languageName(code: String) = when (code) {
    "pa" -> "Punjabi"; "bgc" -> "Haryanvi"; "hi" -> "Hindi"; "en" -> "English"; else -> code
}
