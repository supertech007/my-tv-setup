package com.mytvsetup.launcher

import android.content.Context
import android.content.Intent
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) nav.home()
    }
}
