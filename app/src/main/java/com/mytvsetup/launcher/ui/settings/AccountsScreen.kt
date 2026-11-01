package com.mytvsetup.launcher.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mytvsetup.launcher.ui.youtube.YouTubeButton
import com.mytvsetup.launcher.ui.youtube.YouTubePage
import com.mytvsetup.launcher.youtube.AccountsState
import com.mytvsetup.launcher.youtube.DeviceAuthException
import com.mytvsetup.launcher.youtube.DeviceCode
import com.mytvsetup.launcher.youtube.YouTubeRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch

@Composable
fun AccountsScreen(onBack: () -> Unit, runtime: YouTubeRuntime? = null) {
    val context = LocalContext.current
    val youtube = runtime ?: remember(context) { YouTubeRuntime.get(context) }
    val state by youtube.store.state.collectAsState(AccountsState())
    val scope = rememberCoroutineScope()
    var signingIn by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf<DeviceCode?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(signingIn) {
        if (!signingIn) return@LaunchedEffect
        try {
            val started = youtube.auth.start()
            code = started
            val token = youtube.auth.awaitToken(started)
            val account = youtube.client.account(token)
            youtube.store.add(account, token)
            message = "Account added."
        } catch (_: TimeoutCancellationException) {
            message = "Code expired. Choose Add account to try again."
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: DeviceAuthException) { message = failure.message }
        catch (_: Exception) { message = "Could not add the account. Check your connection and YouTube client, then try again." }
        finally { code = null; signingIn = false }
    }

    YouTubePage("YouTube accounts", onBack) {
        if (!youtube.auth.configured) {
            Text("YouTube needs a one-time Google setup first", color = Color.White, fontSize = 24.sp)
            Text("Adding accounts unlocks once the free Google sign-in key is created and My TV is updated.",
                color = Color.White.copy(alpha = .8f), fontSize = 18.sp)
        }
        if (signingIn) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("On your phone or computer, visit", fontSize = 20.sp, color = Color.White)
                Text(code?.verificationUrl ?: "Requesting sign-in code…", fontSize = 28.sp, color = Color.White)
                code?.let { Text(it.userCode, fontSize = 40.sp, color = Color.White) }
                Text("Enter the code and approve YouTube access. Waiting for approval…", color = Color.White)
                YouTubeButton("Cancel sign-in", { signingIn = false })
            }
        } else {
            YouTubeButton("Add account", { message = null; signingIn = true }, enabled = youtube.auth.configured)
        }
        message?.let { Text(it, color = Color.White, fontSize = 18.sp) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(12.dp)) {
            if (state.accounts.isEmpty()) item { Text("No YouTube accounts added yet.", color = Color.White) }
            items(state.accounts, key = { it.id }) { account ->
                val hidden = account.id in state.hidden
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        // Keep a placeholder so hidden accounts can be restored without revealing their names.
                        Text(if (hidden) "Hidden account ${state.accounts.indexOf(account) + 1}" else account.channelTitle,
                            color = Color.White, fontSize = 22.sp)
                        if (!hidden && account.email.isNotBlank()) Text(account.email, color = Color.White)
                    }
                    YouTubeButton(if (hidden) "Show" else "Hide", {
                        scope.launch {
                            try { youtube.store.setHidden(account.id, !hidden) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { message = "Could not save visibility. Try again." }
                        }
                    })
                }
            }
        }
    }
}
