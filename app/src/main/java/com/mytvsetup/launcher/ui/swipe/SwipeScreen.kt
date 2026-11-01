package com.mytvsetup.launcher.ui.swipe

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import com.mytvsetup.launcher.content.*
import com.mytvsetup.launcher.ui.Outfit
import com.mytvsetup.launcher.ui.Chip as FilterChip
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val red = Color(0xFFFF5A5A)
private val green = Color(0xFF3DDC84)

@Composable
fun SwipeScreen(viewModel: SwipeViewModel, images: ImageLoader, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val input = remember { FocusRequester() }
    val firstChip = remember { FocusRequester() }
    var inputFocused by remember { mutableStateOf(false) }
    var direction by remember { mutableIntStateOf(0) }
    val flight = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    fun rate(sign: Int) {
        if (direction != 0 || state.current == null) return
        direction = sign
        scope.launch {
            flight.animateTo(1f, tween(450, easing = CubicBezierEasing(.3f, .7f, .3f, 1f)))
            if (sign < 0) viewModel.left() else viewModel.right()
            flight.snapTo(0f)
            direction = 0
        }
    }
    LaunchedEffect(Unit) { input.requestFocus() }
    LaunchedEffect(state.toast) {
        if (state.toast != null) { delay(3_000); viewModel.clearToast() }
    }
    Box(Modifier.fillMaxSize().testTag("swipe-input").focusRequester(input)
        .onFocusChanged { inputFocused = it.isFocused }
        .onPreviewKeyEvent { event ->
            if (event.key == Key.Back || event.key == Key.Escape) {
                if (event.type == KeyEventType.KeyUp) onBack()
                true
            } else if (!inputFocused) false
            else when (event.key) {
                Key.DirectionUp -> {
                    if (event.type == KeyEventType.KeyDown && direction == 0) firstChip.requestFocus()
                    true
                }
                Key.DirectionLeft, Key.DirectionRight, Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                    if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0 && direction == 0) {
                        when (event.key) {
                            Key.DirectionLeft -> rate(-1)
                            Key.DirectionRight -> rate(1)
                            else -> viewModel.ok()
                        }
                    }
                    true
                }
                else -> false
            }
        }.focusable()) {
        SwipeBackdrop(state.current?.posterUrl, images)
        Row(Modifier.offset(80.dp, 56.dp).width(1760.dp).height(74.dp)
            .onPreviewKeyEvent { event ->
                if (event.key == Key.DirectionDown) {
                    if (event.type == KeyEventType.KeyDown) input.requestFocus()
                    true
                } else false
            }.horizontalScroll(rememberScrollState()).padding(8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Keep the small catalogue row composed so Up can always reach the first chip,
            // including after the user has scrolled all the way to the last genre.
            state.chips.forEachIndexed { index, chip ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (index == 3 || index == 5) Box(Modifier.padding(horizontal = 8.dp)
                        .size(1.5.dp, 30.dp).background(Color.White.copy(alpha = .3f)))
                    FilterChip(chip.id, chip.selected, if (index == 0) Modifier.focusRequester(firstChip) else Modifier) {
                        viewModel.toggleChip(chip.id)
                    }
                }
            }
        }
        val title = state.current
        if (title == null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Text("All caught up", fontFamily = Outfit, fontSize = 80.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Text("↑ Change your filters · Back to home", fontFamily = Outfit, fontSize = 26.sp, color = Color.White.copy(alpha = .7f))
            }
        } else {
            PosterCard(title, images, direction, flight.value)
            TitleDetails(title)
            Row(Modifier.offset(700.dp, 850.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                KeyHint("◀", "Not for me", red)
                KeyHint("OK", "Play now", Color.White)
                KeyHint("▶", "Add to my list", green, trailing = true)
            }
        }
        val tasteText = state.taste.filter { it.second != 0.0 }.joinToString("  ") { (genre, weight) ->
            "$genre ${if (weight > 0) "▲" else "▼"}"
        }.ifEmpty { "Rate a few titles to shape your picks" }
        Text("Your taste: $tasteText", Modifier.offset(150.dp, 996.dp).width(1450.dp), fontFamily = Outfit,
            fontSize = 21.sp, color = Color.White.copy(alpha = .85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${if (title == null) state.index else state.index + 1} of ${state.total}", Modifier.align(Alignment.BottomEnd)
            .padding(end = 80.dp, bottom = 56.dp), fontFamily = Outfit, fontSize = 21.sp, color = Color.White.copy(alpha = .7f))
        Text("← reject · → accept · OK play · ↑ filters · Back home", Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
            fontFamily = Outfit, fontSize = 16.sp, letterSpacing = 1.sp, color = Color.White.copy(alpha = .5f))
        state.toast?.let {
            Text(it, Modifier.align(Alignment.Center).clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = .94f)).padding(44.dp, 26.dp), fontFamily = Outfit,
                fontSize = 32.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0B1220))
        }
    }
}

@Composable
private fun PosterCard(title: Title, images: ImageLoader, direction: Int, progress: Float) {
    val shape = RoundedCornerShape(32.dp)
    Box(Modifier.offset(150.dp, 200.dp).size(460.dp, 690.dp).graphicsLayer {
        translationX = direction * 700.dp.toPx() * progress
        rotationZ = direction * 18f * progress
        alpha = 1f - progress
        shadowElevation = 40.dp.toPx(); this.shape = shape
    }.clip(shape).background(Color(0xFF253B54))) {
        if (title.posterUrl == null) Text(title.title, Modifier.align(Alignment.Center).padding(30.dp),
            fontFamily = Outfit, fontSize = 48.sp, color = Color.White)
        AsyncImage(title.posterUrl, "${title.title} poster", images, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (direction != 0) {
            val color = if (direction < 0) red else green
            Text(if (direction < 0) "NOPE" else "YES", Modifier.align(if (direction < 0) Alignment.TopStart else Alignment.TopEnd)
                .padding(30.dp, 40.dp).graphicsLayer { rotationZ = direction * 12f }
                .border(4.dp, color, RoundedCornerShape(12.dp)).padding(22.dp, 8.dp),
                color = color, fontFamily = Outfit, fontSize = 36.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TitleDetails(title: Title) {
    val offer = swipeOffer(title)
    val context = LocalContext.current
    val catalogue = remember { GenreCatalog.fromJson(context.assets.open("genre_synonyms.json").bufferedReader().use { it.readText() }) }
    val genres = catalogue.groupsOf(title.labels)
    Column(Modifier.offset(700.dp, 230.dp).size(1060.dp, 550.dp)) {
        Text("${offer?.let { serviceLabel(it.service).uppercase() } ?: "UNAVAILABLE"} · ${if (title.kind == Kind.MOVIE) "MOVIE" else "TV SHOW"}",
            fontFamily = Outfit, fontSize = 22.sp, letterSpacing = 4.sp, fontWeight = FontWeight.SemiBold,
            color = when (offer?.service) { "prv" -> Color(0xFF00A8E1); "snl" -> Color(0xFFF0C35A); else -> Color(0xFFCFD8E3) })
        Text(title.title, Modifier.padding(top = 14.dp, bottom = 22.dp), fontFamily = Outfit, fontSize = 92.sp,
            lineHeight = 96.sp, letterSpacing = (-1.8).sp, fontWeight = FontWeight.SemiBold, color = Color.White,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            title.imdb?.let { Text("IMDb ${String.format(Locale.ROOT, "%.1f", it)}", Modifier.clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFF5C518)).padding(10.dp, 3.dp), color = Color.Black,
                fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 24.sp) }
            Text(listOfNotNull(title.year?.toString(), swipeAudioLabel(title)).joinToString(" · "),
                fontFamily = Outfit, fontSize = 24.sp, color = Color.White.copy(alpha = .9f),
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        FlowRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            genres.forEach { genre -> Text(genre, Modifier.border(1.5.dp, Color.White.copy(alpha = .4f), RoundedCornerShape(16.dp))
                .padding(12.dp, 3.dp), color = Color.White.copy(alpha = .9f), fontFamily = Outfit, fontSize = 20.sp) }
        }
        Text(title.synopsis, Modifier.padding(top = 26.dp).width(900.dp).weight(1f, fill = false), fontFamily = Outfit, fontSize = 26.sp,
            lineHeight = 39.sp, fontWeight = FontWeight.Light, color = Color.White.copy(alpha = .85f), maxLines = 5, overflow = TextOverflow.Ellipsis)
        if (title.cast.isNotEmpty()) Text("Starring ${title.cast.joinToString(", ")}", Modifier.padding(top = 18.dp).width(980.dp),
            fontFamily = Outfit, fontSize = 21.sp, color = Color.White.copy(alpha = .65f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun KeyHint(key: String, text: String, color: Color, trailing: Boolean = false) {
    Row(Modifier.border(1.5.dp, Color.White.copy(alpha = .3f), RoundedCornerShape(22.dp))
        .clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = .12f)).padding(28.dp, 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        @Composable fun symbol() { Box(Modifier.size(44.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
            Text(key, fontFamily = Outfit, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                color = if (color == red) Color.White else Color(0xFF0B1220))
        } }
        if (!trailing) symbol()
        Text(text, fontFamily = Outfit, fontSize = 24.sp, color = Color.White)
        if (trailing) symbol()
    }
}
