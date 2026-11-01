package com.mytvsetup.launcher.ui.youtube

import androidx.compose.foundation.layout.PaddingValues
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytvsetup.launcher.apps.LaunchResult
import com.mytvsetup.launcher.apps.Launcher
import com.mytvsetup.launcher.youtube.AccountsState
import com.mytvsetup.launcher.youtube.YtPlaylist
import com.mytvsetup.launcher.youtube.YouTubeRuntime

@Composable
fun YouTubeScreen(onBack: () -> Unit, onAccounts: () -> Unit = {}, runtime: YouTubeRuntime? = null) {
    val context = LocalContext.current
    val youtube = runtime ?: remember(context) { YouTubeRuntime.get(context) }
    val accounts by youtube.store.state.collectAsState(AccountsState())
    val subscriptions by youtube.feed.videos.collectAsState(emptyList())
    val playlists by youtube.feed.playlists.collectAsState(emptyList())
    val liked by youtube.feed.liked.collectAsState(emptyList())
    val message by youtube.feed.message.collectAsState()
    var tab by remember { mutableStateOf("Subscriptions") }
    var selected by remember { mutableStateOf<YtPlaylist?>(null) }
    val current = selected?.takeIf { playlist -> accounts.visibleAccounts.any { it.id == playlist.accountId } }
    val selectedFlow = remember(current) { current?.let(youtube.feed::playlistVideos) ?: kotlinx.coroutines.flow.flowOf(emptyList()) }
    val playlistVideos by selectedFlow.collectAsState(emptyList())
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(accounts, tab, current) {
        loading = true
        try {
            if (youtube.auth.configured) {
                if (current != null) youtube.feed.openPlaylist(current)
                else if (tab == "Subscriptions") youtube.feed.onHomeShown()
                else youtube.feed.refreshLibrary()
            }
        } finally { loading = false }
    }

    YouTubePage("YouTube", onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (name in listOf("Subscriptions", "Playlists", "Liked")) {
                YouTubeButton(if (tab == name) "• $name" else name, { selected = null; tab = name })
            }
            YouTubeButton("Accounts", onAccounts)
        }
        if (!youtube.auth.configured) {
            Text("Add the YouTube key in secrets.properties", color = Color.White, fontSize = 22.sp)
            Text("Add key in Settings → Accounts", color = Color.White, fontSize = 18.sp)
        } else if (accounts.visibleAccounts.isEmpty()) {
            Text("Add or show a YouTube account in Settings → Accounts.", color = Color.White, fontSize = 22.sp)
        }
        message?.let { Text(it, color = Color.White, fontSize = 16.sp) }
        if (loading) Text("Refreshing…", color = Color.White)
        val videos = when {
            current != null -> playlistVideos
            tab == "Liked" -> liked
            else -> subscriptions
        }
        if (tab == "Playlists" && current == null) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(12.dp)) {
                if (playlists.isEmpty() && !loading) item { Text("No playlists to show.", color = Color.White) }
                items(playlists, key = { "${it.accountId}:${it.id}" }) { playlist ->
                    val account = accounts.visibleAccounts.firstOrNull { it.id == playlist.accountId }
                    if (account != null) YouTubeButton("${playlist.title} · ${account.channelTitle}",
                        { selected = playlist }, Modifier.fillMaxWidth())
                }
            }
        } else {
            current?.let { Text(it.title, color = Color.White, fontSize = 22.sp) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(12.dp)) {
                if (videos.isEmpty() && !loading) item { Text("No videos to show yet.", color = Color.White) }
                items(videos, key = { "${it.accountId}:${it.id}" }) { video ->
                    YouTubeButton("▶ ${video.title} · ${video.channel}", {
                        val result = Launcher.openUrlIn(context, "org.smarttube.stable",
                            "https://www.youtube.com/watch?v=${video.id}", "SmartTube")
                        if (result is LaunchResult.NotInstalled) Toast.makeText(context, "SmartTube is not installed", Toast.LENGTH_SHORT).show()
                    }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}
