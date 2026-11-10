package com.mytvsetup.launcher

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import com.mytvsetup.launcher.audio.MuteKeyService
import com.mytvsetup.launcher.audio.QuickMenuChoice
import com.mytvsetup.launcher.audio.QuickMenuView
import com.mytvsetup.launcher.audio.RemoteShortcuts
import com.mytvsetup.launcher.ui.Screen
import com.mytvsetup.launcher.audio.Mute
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.mytvsetup.launcher.settings.PrefsRepository
import com.mytvsetup.launcher.ui.Nav
import com.mytvsetup.launcher.ui.home.TvApp
import com.mytvsetup.launcher.content.ContentRuntime

class MainActivity : ComponentActivity() {
    private val nav = Nav()
    private var catalogRevision by mutableIntStateOf(0)

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        val prefs = PrefsRepository(applicationContext)
        val content = ContentRuntime.repository(applicationContext)
        registerReceiver(packageReceiver, android.content.IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package")
        })
        setContent {
            BackHandler { nav.back() }
            TvApp(nav, prefs, content, catalogRevision)
        }
    }

    // Rebuild the app list only when packages change, not on every return to Home.
    private val packageReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { catalogRevision++ }
    }

    override fun onDestroy() { unregisterReceiver(packageReceiver); super.onDestroy() }

    private val shortcuts = RemoteShortcuts()
    private var menu: QuickMenuView? = null

    // The hold-Back quick menu works inside the launcher even when the accessibility helper is off.
    // Screen off opens the black page, which itself brings the picture back on the next button.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (MuteKeyService.running) return super.dispatchKeyEvent(event)
        val action = shortcuts.onKey(event.keyCode, event.action == KeyEvent.ACTION_DOWN, event.repeatCount,
            event.eventTime, event.downTime, event.isLongPress)
        when (action) {
            RemoteShortcuts.Action.OPEN_MENU -> {
                closeMenu()
                menu = QuickMenuView(this).also {
                    it.show(Mute.isMuted(this), 0)
                    (window.decorView as android.view.ViewGroup).addView(it)
                }
            }
            RemoteShortcuts.Action.MOVE -> menu?.show(Mute.isMuted(this), shortcuts.selected)
            RemoteShortcuts.Action.CLOSE_MENU -> closeMenu()
            RemoteShortcuts.Action.BACK -> {
                // The held-back press and this release, delivered together as one ordinary Back.
                super.dispatchKeyEvent(KeyEvent.changeAction(event, KeyEvent.ACTION_DOWN))
                super.dispatchKeyEvent(event)
            }
            RemoteShortcuts.Action.CHOOSE -> {
                closeMenu()
                QuickMenuChoice.choose(this, shortcuts.selected) { nav.push(Screen.ScreenOff) }
            }
            else -> Unit
        }
        return action != RemoteShortcuts.Action.PASS || super.dispatchKeyEvent(event)
    }

    private fun closeMenu() {
        menu?.let { (window.decorView as android.view.ViewGroup).removeView(it) }
        menu = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) nav.home()
    }
}
