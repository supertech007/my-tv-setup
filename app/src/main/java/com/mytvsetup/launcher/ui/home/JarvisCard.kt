package com.mytvsetup.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytvsetup.launcher.ai.*
import com.mytvsetup.launcher.ui.*

@Composable
fun JarvisCard(installed: Set<String>, modifier: Modifier, onOpen: (Assistant) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val status = assistantStatus(installed)
    GlassCard(modifier.onFocusChanged { focused = it.hasFocus }, focused) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Label("Jarvis")
            Text("Just ask.", Modifier.padding(top = 8.dp), fontFamily = Outfit, fontSize = 40.sp, color = Color.White)
            Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                status.entries.forEachIndexed { index, (assistant, present) ->
                    FocusTile(radius = 18, onClick = { onOpen(assistant) }) {
                        Row(Modifier.background(Color.White.copy(alpha = .14f)).padding(horizontal = 9.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(8.dp).background(listOf(Color(0xFFD97757), Color(0xFF10A37F), Color(0xFF8A9AFF))[index]
                                .copy(alpha = if (present) 1f else .55f), CircleShape))
                            Text(assistant.name, fontFamily = Outfit, fontSize = 16.sp, color = Color.White)
                        }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(top = 26.dp, end = 24.dp).size(70.dp)
            .background(Brush.radialGradient(listOf(Color.White, Color(0xFFE9EEF8), Color(0xFFA9B6CC), Color(0xFF4B5670))), CircleShape))
    }
}
