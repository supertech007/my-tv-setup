package com.mytvsetup.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytvsetup.launcher.ui.*

@Composable
internal fun SettingsText(text: String, size: Int = 26, muted: Boolean = false, modifier: Modifier = Modifier) {
    Text(text, modifier, fontFamily = Outfit, fontSize = size.sp,
        color = Color.White.copy(alpha = if (muted) .75f else 1f))
}

@Composable
internal fun SettingsAction(text: String, modifier: Modifier = Modifier, selected: Boolean = false, onClick: () -> Unit) {
    FocusTile(modifier, radius = 20, onClick = onClick) {
        Text(text, Modifier.fillMaxWidth().background(if (selected) Color.White.copy(alpha = .23f) else Color.White.copy(alpha = .09f))
            .padding(horizontal = 24.dp, vertical = 16.dp), fontFamily = Outfit, fontSize = 25.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, color = Color.White)
    }
}

@Composable
internal fun SettingsHeader(title: String, subtitle: String) {
    SettingsText(title, 50)
    SettingsText(subtitle, 25, muted = true)
    Spacer(Modifier.height(18.dp))
}

@Composable
internal fun SettingsPage(title: String, subtitle: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val back = remember { FocusRequester() }
    LaunchedEffect(Unit) { back.requestFocus() }
    GlassCard(Modifier.offset(180.dp, 120.dp).size(1560.dp, 840.dp), false) {
        Column(Modifier.fillMaxSize().padding(56.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Label("My TV")
            SettingsHeader(title, subtitle)
            content()
            Spacer(Modifier.weight(1f))
            SettingsAction("Back to Settings", Modifier.width(330.dp).focusRequester(back), onClick = onBack)
        }
    }
}
