package com.mytvsetup.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import com.mytvsetup.launcher.ui.*
import com.mytvsetup.launcher.youtube.YtVideo

/** Latest video from the visible accounts; OK opens the YouTube screen. */
@Composable
fun YouTubeCard(modifier: Modifier, latest: YtVideo?, imageLoader: ImageLoader, onOpen: () -> Unit) {
    ActionGlass(modifier, onOpen) {
        Column(Modifier.fillMaxSize().padding(22.dp)) {
            Label("YouTube")
            if (latest == null) {
                Text("▶", Modifier.padding(top = 14.dp).background(Color(0xFFDB253C), RoundedCornerShape(12.dp)).padding(12.dp, 2.dp),
                    color = Color.White, fontSize = 26.sp)
                Text("Add a Google account in Settings", Modifier.padding(top = 14.dp), fontFamily = Outfit,
                    fontSize = 23.sp, lineHeight = 27.sp, color = Color.White)
            } else {
                AsyncImage(latest.thumb, latest.title, imageLoader, Modifier.padding(top = 10.dp).fillMaxWidth().height(86.dp)
                    .clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                Text(latest.title, Modifier.padding(top = 8.dp), fontFamily = Outfit, fontSize = 18.sp, lineHeight = 21.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color.White)
                Text(latest.channel, fontFamily = Outfit, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = Color.White.copy(alpha = .7f))
            }
        }
    }
}
