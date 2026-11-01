package com.mytvsetup.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import com.mytvsetup.launcher.ui.*

@Composable
fun SwipeCard(source: HomeContentSource, imageLoader: ImageLoader, modifier: Modifier, onOpen: () -> Unit) {
    val queue by source.swipeQueue.collectAsState(emptyList())
    ActionGlass(modifier, onOpen) {
        Column(Modifier.fillMaxSize().padding(24.dp, 28.dp)) {
            Label("Swipe")
            Box(Modifier.fillMaxWidth().height(195.dp).padding(top = 22.dp), contentAlignment = Alignment.Center) {
                queue.take(3).forEachIndexed { index, title ->
                    val angle = listOf(-12f, 10f, 0f)[index]
                    AsyncImage(title.posterUrl, title.title, imageLoader,
                        Modifier.offset(x = listOf(-25, 25, 0)[index].dp).size(110.dp, 165.dp)
                            .graphicsLayer { rotationZ = angle; shadowElevation = 12.dp.toPx() }
                            .clip(RoundedCornerShape(14.dp)).background(Color(0xFF253B54)), contentScale = ContentScale.Crop)
                }
            }
            Text("Rate &\ndiscover", fontFamily = Outfit, fontSize = 32.sp, lineHeight = 34.sp, color = Color.White)
            Text("${queue.size} to rate", Modifier.padding(top = 10.dp), fontFamily = Outfit, fontSize = 18.sp, maxLines = 1, color = Color.White.copy(alpha = .85f))
        }
    }
}
