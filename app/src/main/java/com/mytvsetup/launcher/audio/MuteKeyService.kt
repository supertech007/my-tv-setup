package com.mytvsetup.launcher.audio

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.mytvsetup.launcher.audio.RemoteShortcuts.Action
import com.mytvsetup.launcher.tv.LgTv

/**
 * Lets the hold-Back quick menu (Mute or Unmute, Screen off) work in every app, not only in the launcher.
 * Android shows remote buttons to an accessibility service only after the user switches it on in TV Settings
 * (Device Preferences → Accessibility → Services); it reads nothing on screen.
 */
class MuteKeyService : AccessibilityService() {
    private val shortcuts = RemoteShortcuts()
    private var menu: QuickMenuView? = null

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val action = shortcuts.onKey(event.keyCode, event.action == KeyEvent.ACTION_DOWN, event.repeatCount,
            event.eventTime, event.downTime, event.isLongPress)
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            handler.removeCallbacks(holdTimer)
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) handler.postDelayed(holdTimer, shortcuts.holdMs)
        }
        act(action)
        return action != Action.PASS
    }

    private val handler = Handler(Looper.getMainLooper())
    private val holdTimer = Runnable { act(shortcuts.onBackHoldTimer()) }

    private fun act(action: Action) {
        when (action) {
            Action.OPEN_MENU -> showMenu()
            Action.MOVE -> menu?.show(Mute.isMuted(this), shortcuts.selected)
            Action.CLOSE_MENU -> hideMenu()
            Action.CHOOSE -> {
                hideMenu()
                QuickMenuChoice.choose(this, shortcuts.selected) {
                    shortcuts.screenIsOff = true
                    QuickMenuChoice.screenOffBehindApps(this) { shortcuts.screenIsOff = false }
                }
            }
            Action.SCREEN_ON -> LgTv.get(this).screenOnLater()
            else -> Unit
        }
    }

    private fun showMenu() {
        hideMenu()
        val view = QuickMenuView(this).apply { show(Mute.isMuted(this@MuteKeyService), 0) }
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT)
        runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).addView(view, params); menu = view }
    }

    private fun hideMenu() {
        menu?.let { runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } }
        menu = null
    }

    override fun onServiceConnected() { running = true }
    override fun onDestroy() { hideMenu(); running = false; super.onDestroy() }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        /** While on, the helper handles the shortcuts everywhere, so the launcher must not handle them twice. */
        @Volatile var running = false
    }
}
