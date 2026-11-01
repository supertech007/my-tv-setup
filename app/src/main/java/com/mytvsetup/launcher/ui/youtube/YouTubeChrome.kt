package com.mytvsetup.launcher.ui.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Standalone glass styling; does not depend on the parallel Nav/Glass implementation. */
@Composable
internal fun YouTubePage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val firstFocus = remember { FocusRequester() }
    MaterialTheme(colorScheme = darkColorScheme()) {
        Column(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xff1c2c44), Color(0xff0d1522))))
            .padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, color = Color.White, fontSize = 30.sp)
                YouTubeButton("Back", onBack, Modifier.focusRequester(firstFocus))
            }
            content()
        }
    }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }
}

@Composable
internal fun YouTubeButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var focused by remember { mutableStateOf(false) }
    Button(onClick, modifier.onFocusChanged { focused = it.isFocused }
        .border(if (focused) 3.dp else 1.dp, Color.White.copy(alpha = if (focused) 1f else .3f), RoundedCornerShape(18.dp)),
        enabled = enabled, shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = if (focused) .28f else .12f),
            contentColor = Color.White)) {
        Text(label, fontSize = 18.sp, modifier = Modifier.padding(vertical = 4.dp))
    }
}
