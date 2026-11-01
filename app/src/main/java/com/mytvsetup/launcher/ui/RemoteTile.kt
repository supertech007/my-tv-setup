package com.mytvsetup.launcher.ui

import android.view.ViewConfiguration
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Consume the entire OK gesture so a hold never also launches an app on release. */
@Composable
fun RemoteTile(modifier: Modifier = Modifier, onClick: () -> Unit, onHold: () -> Unit,
               content: @Composable BoxScope.() -> Unit) {
    val scope = rememberCoroutineScope()
    val click by rememberUpdatedState(onClick)
    val hold by rememberUpdatedState(onHold)
    var timer by remember { mutableStateOf<Job?>(null) }
    var pressed by remember { mutableStateOf(false) }
    var held by remember { mutableStateOf(false) }
    FocusTile(modifier.onFocusChanged {
        if (!it.hasFocus) { timer?.cancel(); pressed = false }
    }.semantics { onLongClick("Hold OK") { hold(); true } }.onPreviewKeyEvent { event ->
        if (event.key !in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)) false
        else when (event.type) {
            KeyEventType.KeyDown -> {
                // A remote auto-repeats a held button; that alone is a hold, whatever the timer says.
                if (pressed && !held && event.nativeKeyEvent.repeatCount > 0) {
                    timer?.cancel(); held = true; hold()
                } else if (!pressed) {
                    pressed = true; held = false
                    timer = scope.launch {
                        delay(ViewConfiguration.getLongPressTimeout().toLong())
                        held = true
                        hold()
                    }
                }
                true
            }
            KeyEventType.KeyUp -> {
                timer?.cancel()
                if (pressed && !held) click()
                pressed = false
                true
            }
            else -> false
        }
    }, radius = 24, onClick = onClick, content = content)
}
