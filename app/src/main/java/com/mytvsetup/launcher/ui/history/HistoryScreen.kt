package com.mytvsetup.launcher.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import com.mytvsetup.launcher.history.*
import com.mytvsetup.launcher.ui.*
import com.mytvsetup.launcher.ui.settings.SettingsDialog
import java.time.LocalDate

/** Everything opened from My TV, newest first and grouped by day. OK opens it again; Clear empties the list. */
@Composable
fun HistoryScreen(history: WatchHistory, keepDays: Int, imageLoader: ImageLoader, onOpen: (HistoryEntry) -> Unit,
                  onBack: () -> Unit) {
    val entries by history.entries.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val today = remember { LocalDate.now() }
    val days = remember(entries) { entries.groupBy { historyDay(it.at, today) }.toList() }
    BackHandler { onBack() }
    LaunchedEffect(entries.isEmpty()) { runCatching { first.requestFocus() } }
    GlassCard(Modifier.offset(40.dp, 36.dp).size(1840.dp, 1008.dp), false) {
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 30.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label("History")
                    Text("${entries.size} opened · kept for ${keepDaysLabel(keepDays).lowercase()} · Back to close",
                        fontFamily = Outfit, fontSize = 20.sp, color = Color.White.copy(alpha = .7f))
                }
                if (entries.isNotEmpty()) Chip("Clear history") { confirmClear = true }
                Spacer(Modifier.width(12.dp))
                Chip("Back home", modifier = if (entries.isEmpty()) Modifier.focusRequester(first) else Modifier) { onBack() }
            }
            if (entries.isEmpty()) Text("Nothing yet. Movies, shows, music, YouTube videos and apps you open from " +
                "My TV will show up here.", Modifier.padding(top = 60.dp), fontFamily = Outfit, fontSize = 28.sp,
                color = Color.White.copy(alpha = .85f))
            else LazyVerticalGrid(GridCells.Adaptive(300.dp), Modifier.fillMaxSize().padding(top = 16.dp),
                contentPadding = PaddingValues(12.dp, 12.dp), horizontalArrangement = Arrangement.spacedBy(26.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)) {
                days.forEachIndexed { dayIndex, (day, items) ->
                    item(key = "day:$day", span = { GridItemSpan(maxLineSpan) }) {
                        Text(day, Modifier.padding(top = if (dayIndex == 0) 0.dp else 14.dp), fontFamily = Outfit,
                            fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                    items(items, key = { "${it.kind}:${it.ref}" }) { entry ->
                        val isFirst = dayIndex == 0 && entry == items.first()
                        HistoryTile(entry, imageLoader, if (isFirst) Modifier.focusRequester(first) else Modifier) { onOpen(entry) }
                    }
                }
            }
        }
    }
    if (confirmClear) SettingsDialog("Clear history?", "Everything in your My TV history will be removed.",
        "Clear history", { confirmClear = false }) { history.clear(); confirmClear = false }
}

@Composable
private fun HistoryTile(entry: HistoryEntry, imageLoader: ImageLoader, modifier: Modifier, onClick: () -> Unit) {
    FocusTile(modifier.size(300.dp, 170.dp).semantics { contentDescription = "${entry.name}, ${entry.detail}" }, onClick = onClick) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF2A4260), Color(0xFF141E2C)))))
        if (entry.poster != null) AsyncImage(entry.poster, entry.name, imageLoader, Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .88f)), startY = 40f)))
        Text(kindLabel(entry.kind), Modifier.align(Alignment.TopStart).padding(8.dp)
            .background(Color.White.copy(alpha = .9f), RoundedCornerShape(5.dp)).padding(5.dp, 2.dp),
            fontFamily = Outfit, fontSize = 11.sp, color = Color(0xFF0D1522), fontWeight = FontWeight.Bold)
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(entry.name, fontFamily = Outfit, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                overflow = TextOverflow.Ellipsis, color = Color.White, lineHeight = 22.sp)
            Text("${entry.detail} · ${historyTime(entry.at)}", Modifier.padding(top = 4.dp), fontFamily = Outfit,
                fontSize = 13.sp, color = Color(0xFFBFE3FF), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun kindLabel(kind: HistoryKind) = when (kind) {
    HistoryKind.MOVIE -> "MOVIE"; HistoryKind.SHOW -> "SERIES"; HistoryKind.MUSIC -> "MUSIC"
    HistoryKind.YOUTUBE -> "YOUTUBE"; HistoryKind.APP -> "APP"
}
