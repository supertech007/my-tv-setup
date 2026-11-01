package com.mytvsetup.launcher.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mytvsetup.launcher.ui.GlassCard

@Composable
internal fun SettingsDialog(title: String, detail: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val cancel = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GlassCard(Modifier.width(850.dp), false) {
                Column(Modifier.padding(50.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    SettingsText(title, 38)
                    SettingsText(detail, 26, muted = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        SettingsAction("Cancel", Modifier.weight(1f).focusRequester(cancel), onClick = onDismiss)
                        SettingsAction(action, Modifier.weight(1f), onClick = onConfirm)
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { cancel.requestFocus() }
}
