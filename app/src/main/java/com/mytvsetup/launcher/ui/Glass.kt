package com.mytvsetup.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

const val TILE_LIFT = 1.08f

val LocalGlassWallpaper = staticCompositionLocalOf<ImageBitmap?> { null }
val LocalStageSize = staticCompositionLocalOf { IntSize(1920, 1080) }

@Composable
fun GlassCard(modifier: Modifier, focused: Boolean, content: @Composable BoxScope.() -> Unit) {
    val wallpaper = LocalGlassWallpaper.current
    val stage = LocalStageSize.current
    var origin by remember { mutableStateOf(IntOffset.Zero) }
    val scale by animateFloatAsState(if (focused) 1.025f else 1f, label = "glass lift")
    val shape = RoundedCornerShape(44.dp)
    Box(modifier
        .zIndex(if (focused) 2f else 0f)
        .onGloballyPositioned { p -> p.positionInRoot().let { origin = IntOffset(it.x.roundToInt(), it.y.roundToInt()) } }
        .graphicsLayer { scaleX = scale; scaleY = scale; shadowElevation = 0f; this.shape = shape }
        .border(if (focused) 3.dp else 1.5.dp, if (focused) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = .38f), shape)
        .clip(shape)
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        // The stick's GPU cannot repaint six glass panels every frame: the panel is cached as a texture,
        // so focus moving inside a card only repaints its posters and text.
        Box(Modifier.matchParentSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawBehind {
            wallpaper?.let { drawImage(it, dstOffset = -origin, dstSize = stage, filterQuality = FilterQuality.Medium) }
            drawRect(Color(0xFF081324).copy(alpha = .22f))
            drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = .30f), Color.White.copy(alpha = .08f), Color.White.copy(alpha = .14f))))
            drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = .15f), Color.Transparent), radius = size.width * .8f))
        })
        content()
    }
}

@Composable
fun ActionGlass(modifier: Modifier, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    var focused by remember { mutableStateOf(false) }
    GlassCard(modifier.onFocusChanged { focused = it.isFocused }.clickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick
    ), focused, content)
}

@Composable
fun FocusTile(modifier: Modifier = Modifier, radius: Int = 18, role: Role = Role.Button, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) TILE_LIFT else 1f, label = "tile lift")
    val shape = RoundedCornerShape(radius.dp)
    Box(modifier.zIndex(if (focused) 3f else 0f).onFocusChanged { focused = it.isFocused }
        .graphicsLayer { scaleX = scale; scaleY = scale; shadowElevation = 0f; this.shape = shape }
        .border(if (focused) 4.dp else 0.dp, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
        .clip(shape).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
            role = role, onClick = onClick), content = content)
}

@Composable
fun Chip(text: String, selected: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusTile(modifier, radius = 22, onClick = onClick) {
        Text(text, Modifier.background(if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = .12f))
            .padding(horizontal = 16.dp, vertical = 8.dp), color = if (selected) Color(0xFF0D1522) else Color.White,
            fontFamily = Outfit, fontSize = 19.sp, maxLines = 1, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, fontSize = 20.sp, letterSpacing = 3.sp, color = Color.White.copy(alpha = .8f),
        fontFamily = Outfit, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun PersonaliseSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    FocusTile(Modifier.semantics {
        toggleableState = if (on) ToggleableState.On else ToggleableState.Off
        stateDescription = if (on) "On" else "Off"
    }, radius = 22, role = Role.Switch, onClick = { onChange(!on) }) {
        Row(Modifier.background(Color.White.copy(alpha = .12f)).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Personalise", fontFamily = Outfit, fontSize = 19.sp, color = Color.White)
            Box(Modifier.size(36.dp, 20.dp).background(if (on) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = .3f), CircleShape).padding(2.dp)) {
                Box(Modifier.align(if (on) Alignment.CenterEnd else Alignment.CenterStart).size(16.dp)
                    .background(if (on) Color(0xFF172B43) else Color.White, CircleShape))
            }
        }
    }
}
