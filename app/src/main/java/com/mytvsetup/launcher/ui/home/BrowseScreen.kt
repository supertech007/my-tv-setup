package com.mytvsetup.launcher.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import com.mytvsetup.launcher.content.Title
import com.mytvsetup.launcher.content.Filters
import com.mytvsetup.launcher.ui.*

/** Full-screen For you: the same filters as the Home panel over a grid of every matching title. */
@Composable
fun BrowseScreen(source: HomeContentSource, imageLoader: ImageLoader, onPlay: (Title) -> Unit, onPersonalise: (Boolean) -> Unit) {
    val titles by source.forYou.collectAsState(emptyList())
    val filters by source.filters.collectAsState()
    val first = remember { FocusRequester() }
    var focusedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(titles.isNotEmpty()) {
        if (titles.isNotEmpty() && !focusedOnce) { runCatching { first.requestFocus() }; focusedOnce = true }
    }
    GlassCard(Modifier.offset(40.dp, 36.dp).size(1840.dp, 1008.dp), false) {
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 30.dp)) {
            ForYouFilters(source, onPersonalise, chipLimit = 14) {
                Text("${titles.size} titles · Back to close", Modifier.padding(end = 12.dp), fontFamily = Outfit,
                    fontSize = 20.sp, color = Color.White.copy(alpha = .7f))
            }
            if (titles.isEmpty()) EmptyForYou(source)
            else LazyVerticalGrid(GridCells.Adaptive(176.dp), Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp, 16.dp), horizontalArrangement = Arrangement.spacedBy(26.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp)) {
                itemsIndexed(titles, key = { _, t -> t.id }) { index, title ->
                    Poster(title, imageLoader, Modifier.size(176.dp, 264.dp)
                        .then(if (index == 0) Modifier.focusRequester(first) else Modifier), info = posterInfo(source, title, filters)) { onPlay(title) }
                }
            }
        }
    }
}
