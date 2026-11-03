package com.mytvsetup.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytvsetup.launcher.tv.LgTv

/**
 * Music without a picture: the LG TV switches its screen off while sound keeps playing. Any button on the stick's
 * remote (or leaving this page any other way, Home included) switches the screen back on.
 */
@Composable
fun ScreenOffScreen(onClose: () -> Unit) {
    val tv = LgTv.get(LocalContext.current)
    var status by remember { mutableStateOf("Switching the screen off…") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        status = if (tv.screenOff { status = "Your TV is asking to allow My TV.\nAccept it with the LG remote." }) ""
        else "Couldn't reach the LG TV.\nCheck it is on the same Wi-Fi, then try again."
    }
    DisposableEffect(Unit) { onDispose { tv.screenOnLater() } }
    BackHandler { onClose() }
    Box(Modifier.fillMaxSize().background(Color.Black).focusRequester(focus).focusable()
        .onKeyEvent { if (it.type == KeyEventType.KeyUp) onClose(); true }, contentAlignment = Alignment.Center) {
        if (status.isNotEmpty()) Text(status + "\n\nPress any button to go back.", Modifier.padding(48.dp),
            fontFamily = Outfit, fontSize = 30.sp, color = Color.White, textAlign = TextAlign.Center, lineHeight = 40.sp)
    }
}
