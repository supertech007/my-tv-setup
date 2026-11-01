package com.mytvsetup.launcher.ui.ai

import android.annotation.SuppressLint
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.mytvsetup.launcher.ui.Outfit

// Without the WebView marker sites treat the page like Chrome on a tablet.
private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 11; Tablet) AppleWebKit/537.36 (KHTML, like Gecko) " +
    "Chrome/124.0.0.0 Safari/537.36"

/**
 * Claude and ChatGPT have no Android TV apps, so Jarvis opens their websites with a remote-driven pointer:
 * arrows move it, OK clicks, holding it at the top or bottom edge scrolls, Back closes. Sign-ins persist.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AssistantScreen(name: String, url: String, onClose: () -> Unit) {
    var pointer by remember { mutableStateOf(Pair(480f, 300f)) }
    var loading by remember { mutableStateOf(true) }
    var web by remember { mutableStateOf<WebView?>(null) }
    BackHandler { onClose() }
    DisposableEffect(Unit) { onDispose { web?.destroy() } }
    LaunchedEffect(web) { web?.requestFocus() }
    Box(Modifier.fillMaxSize().background(Color(0xFF0D1522))) {
        AndroidView(factory = { context ->
            // WebView hands keys to the page before any OnKeyListener, so the pointer intercepts them here.
            object : WebView(context) {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean =
                    handleRemoteKey(this, event.keyCode, event, pointer) { pointer = it } || super.dispatchKeyEvent(event)
            }.apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.userAgentString = USER_AGENT
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) { loading = false }
                }
                loadUrl(url)
                requestFocus()
                web = this
            }
        }, modifier = Modifier.fillMaxSize())
        val density = LocalDensity.current
        Box(Modifier.offset(with(density) { (pointer.first - 14f).toDp() }, with(density) { (pointer.second - 14f).toDp() })
            .size(28.dp).background(Color.White.copy(alpha = .85f), CircleShape).border(3.dp, Color(0xFF2B6CB0), CircleShape))
        if (loading) Text("Opening $name…", Modifier.align(Alignment.Center), fontFamily = Outfit, fontSize = 34.sp, color = Color.White)
        Text("$name · arrows move · OK clicks · Back closes", Modifier.align(Alignment.BottomEnd).padding(18.dp)
            .background(Color.Black.copy(alpha = .55f)).padding(horizontal = 14.dp, vertical = 6.dp),
            fontFamily = Outfit, fontSize = 18.sp, color = Color.White)
    }
}

/** Returns true when the remote key was used for the pointer; typing keys (Enter included) reach the page. */
private fun handleRemoteKey(view: WebView, keyCode: Int, event: KeyEvent, pointer: Pair<Float, Float>,
                            move: (Pair<Float, Float>) -> Unit): Boolean {
    val (dx, dy) = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> -1 to 0
        KeyEvent.KEYCODE_DPAD_RIGHT -> 1 to 0
        KeyEvent.KEYCODE_DPAD_UP -> 0 to -1
        KeyEvent.KEYCODE_DPAD_DOWN -> 0 to 1
        KeyEvent.KEYCODE_DPAD_CENTER -> {
            if (event.action == KeyEvent.ACTION_UP) tap(view, pointer.first, pointer.second)
            return true
        }
        else -> return false
    }
    if (event.action != KeyEvent.ACTION_DOWN) return true
    // Accelerate while the button is held.
    val step = 14f + minOf(event.repeatCount * 8f, 70f)
    val x = (pointer.first + dx * step).coerceIn(0f, view.width - 1f)
    val y = (pointer.second + dy * step).coerceIn(0f, view.height - 1f)
    val edge = 24f
    if (dy != 0 && (y <= edge || y >= view.height - edge)) scroll(view, x, y, -dy.toFloat())
    move(x to y)
    return true
}

private fun tap(view: WebView, x: Float, y: Float) {
    val down = SystemClock.uptimeMillis()
    listOf(MotionEvent.ACTION_DOWN to down, MotionEvent.ACTION_UP to down + 40).forEach { (action, time) ->
        val event = MotionEvent.obtain(down, time, action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        view.dispatchTouchEvent(event)
        event.recycle()
    }
}

/** A mouse-wheel event scrolls whichever panel is under the pointer, unlike WebView.scrollBy. */
private fun scroll(view: WebView, x: Float, y: Float, amount: Float) {
    val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE })
    val coords = arrayOf(MotionEvent.PointerCoords().apply {
        this.x = x; this.y = y; setAxisValue(MotionEvent.AXIS_VSCROLL, amount * 3)
    })
    val now = SystemClock.uptimeMillis()
    val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, props, coords, 0, 0, 1f, 1f, 0, 0,
        InputDevice.SOURCE_MOUSE, 0)
    view.dispatchGenericMotionEvent(event)
    event.recycle()
}
