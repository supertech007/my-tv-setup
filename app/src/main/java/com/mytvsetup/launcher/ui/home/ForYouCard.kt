package com.mytvsetup.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.ImageLoader
import coil.compose.AsyncImage
import com.mytvsetup.launcher.content.*
import com.mytvsetup.launcher.ui.*
import java.time.Year

@Composable
fun ForYouCard(source: HomeContentSource, imageLoader: ImageLoader, modifier: Modifier, onPlay: (Title) -> Unit,
               onPersonalise: (Boolean) -> Unit = source::setPersonalise, onOpen: () -> Unit = {}) {
    val titles by source.forYou.collectAsState(emptyList())
    val filters by source.filters.collectAsState()
    var focused by remember { mutableStateOf(false) }
    val open = remember { FocusRequester() }
    // Entering the panel always lands on Open, so OK goes straight to the full-screen view.
    GlassCard(modifier.onFocusChanged { focused = it.hasFocus }
        .focusProperties { onEnter = { open.requestFocus() } }.focusGroup(), focused) {
        Column(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 22.dp)) {
            ForYouFilters(source, onPersonalise, chipLimit = 10) {
                Chip("Open ⤢", modifier = Modifier.focusRequester(open)) { onOpen() }
            }
            if (titles.isEmpty()) EmptyForYou(source)
            else LazyRow(Modifier.fillMaxWidth().height(253.dp), horizontalArrangement = Arrangement.spacedBy(22.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp)) {
                items(titles, key = { it.id }) { title ->
                    Poster(title, imageLoader, Modifier.size(150.dp, 225.dp), info = posterInfo(source, title, filters)) { onPlay(title) }
                }
            }
        }
    }
}

/** The For you header: type tabs, then genre, year, sort, Personalise and the top genres. */
@Composable
fun ForYouFilters(source: HomeContentSource, onPersonalise: (Boolean) -> Unit, chipLimit: Int, lead: @Composable () -> Unit = {}) {
    val groups by source.genreGroups.collectAsState(emptyList())
    val filters by source.filters.collectAsState()
    val personal by source.personalise.collectAsState()
    var genresOpen by remember { mutableStateOf(false) }
    var exploreGroup by remember { mutableStateOf<String?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Label("For you", Modifier.padding(end = 8.dp))
        lead()
        listOf("All" to null, "Movies" to Kind.MOVIE, "TV shows" to Kind.SHOW, "Music" to Kind.MUSIC).forEach { (name, kind) ->
            Chip(name, if (kind == null) filters.kinds.isEmpty() else filters.kinds == setOf(kind)) {
                source.setFilters(filters.copy(kinds = kind?.let { setOf(it) } ?: emptySet()))
            }
        }
    }
    Spacer(Modifier.height(5.dp))
    // Room inside the scroll area so a focused chip, drawn 8% larger, is not clipped.
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Chip("${filters.tag ?: filters.group ?: "Genre"} ▾", filters.group != null) { exploreGroup = null; genresOpen = true }
        Chip(if (filters.yearMin == null) "Any year" else "This year", filters.yearMin != null) {
            source.setFilters(filters.copy(yearMin = if (filters.yearMin == null) Year.now().value else null))
        }
        Chip(if (filters.sort == Sort.IMDB) "IMDb ↓" else "Newest ↓") {
            source.setFilters(filters.copy(sort = if (filters.sort == Sort.IMDB) Sort.NEWEST else Sort.IMDB))
        }
        PersonaliseSwitch(personal, onPersonalise)
        // The row is not lazy: show the top genres here; the picker lists the rest.
        groups.take(chipLimit).forEach { group ->
            Chip(group.name, filters.group == group.name) {
                // A genre without finer tags needs no second step: apply it (or clear it) at once.
                if (group.finerTags().isEmpty()) source.setFilters(filters.copy(
                    group = if (filters.group == group.name) null else group.name, tag = null))
                else { exploreGroup = group.name; genresOpen = true }
            }
        }
    }
    Spacer(Modifier.height(5.dp))
    if (genresOpen) GenrePicker(groups, filters, exploreGroup, { source.setFilters(it) }) { genresOpen = false }
}

@Composable
fun EmptyForYou(source: HomeContentSource) {
    Column(Modifier.padding(top = 35.dp)) {
        Text("Nothing here yet", fontFamily = Outfit, fontSize = 32.sp, color = Color.White)
        Text("Try another genre, year or content type.", fontFamily = Outfit, fontSize = 22.sp, color = Color.White.copy(alpha = .75f))
        Chip("Reset filters", modifier = Modifier.padding(top = 18.dp)) { source.setFilters(Filters()) }
    }
}

@Composable
fun Poster(title: Title, imageLoader: ImageLoader, modifier: Modifier, info: String? = null, onClick: () -> Unit) {
    FocusTile(modifier.semantics { contentDescription = "${title.title}, ${title.year ?: ""}, IMDb ${title.imdb ?: "unrated"}" }, onClick = onClick) {
        Box(Modifier.fillMaxSize().background(Color(0xFF23364A)))
        AsyncImage(title.posterUrl, title.title, imageLoader, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .86f)), startY = 80f)))
        Text(if (title.kind == Kind.SHOW) "SERIES" else title.kind.name,
            Modifier.align(Alignment.TopStart).padding(8.dp).background(Color.White.copy(alpha = .9f), RoundedCornerShape(5.dp)).padding(5.dp, 2.dp),
            fontFamily = Outfit, fontSize = 11.sp, color = Color(0xFF0D1522), fontWeight = FontWeight.Bold)
        info?.let {
            Text(it, Modifier.align(Alignment.TopEnd).padding(top = 9.dp, end = 8.dp)
                .background(Color.Black.copy(alpha = .62f), RoundedCornerShape(5.dp)).padding(5.dp, 1.dp),
                fontFamily = Outfit, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        title.imdb?.let {
            Text("IMDb $it", Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 8.dp)
                .background(Color(0xFFF5C518), RoundedCornerShape(5.dp)).padding(5.dp, 2.dp),
                fontFamily = Outfit, fontSize = 14.sp, color = Color.Black, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(title.title, fontFamily = Outfit, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 2,
                overflow = TextOverflow.Ellipsis, color = Color.White, lineHeight = 20.sp)
            Text(if (title.kind == Kind.MUSIC) "LISTEN" else serviceName((title.offers.firstOrNull { o -> o.audio.any { it == "hi" || it == "en" } } ?: title.offers.firstOrNull())?.service), Modifier.padding(top = 5.dp),
                fontFamily = Outfit, fontSize = 11.sp, letterSpacing = 1.sp, color = Color(0xFFBFE3FF))
        }
    }
}

fun posterInfo(source: HomeContentSource, title: Title, filters: Filters): String? =
    posterInfo(source.genreOf(title), title.year, genreFiltered = filters.group != null || filters.tag != null)

fun serviceName(service: String?): String = when (service) {
    "prv" -> "PRIME VIDEO"; "jhs" -> "JIOHOTSTAR"; "snl" -> "SONYLIV"; "atp" -> "APPLE TV"; "zee" -> "ZEE5"; else -> "WATCH"
}

@Composable
private fun GenrePicker(groups: List<GenreGroup>, filters: Filters, exploreGroup: String?, onFilters: (Filters) -> Unit, close: () -> Unit) {
    var selected by remember { mutableStateOf(groups.firstOrNull { it.name == (exploreGroup ?: filters.group) }) }
    Dialog(onDismissRequest = close) {
        Column(Modifier.width(700.dp).background(Color(0xF2203045), RoundedCornerShape(32.dp)).padding(32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Label("Explore genres")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(8.dp)) {
                item { Chip("All genres", filters.group == null) { onFilters(filters.copy(group = null, tag = null)); close() } }
                itemsIndexed(groups) { _, group ->
                    Chip(group.name, selected == group) {
                        if (group.finerTags().isEmpty()) { onFilters(filters.copy(group = group.name, tag = null)); close() }
                        else selected = group
                    }
                }
            }
            selected?.let { group ->
                Text("${group.name} · finer tags", fontFamily = Outfit, fontSize = 26.sp, color = Color.White)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(8.dp)) {
                    item { Chip("All ${group.name}") { onFilters(filters.copy(group = group.name, tag = null)); close() } }
                    itemsIndexed(group.finerTags()) { _, tag -> Chip(tag, filters.tag == tag) { onFilters(filters.copy(group = group.name, tag = tag)); close() } }
                }
            }
            Chip("Done") { close() }
        }
    }
}
