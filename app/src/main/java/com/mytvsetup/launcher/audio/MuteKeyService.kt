package com.mytvsetup.launcher.audio

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.mytvsetup.launcher.audio.RemoteShortcuts.Action
import com.mytvsetup.launcher.tv.LgTv
import kotlinx.coroutines.launch

/**
 * Lets the remote shortcuts work in every app, not only in the launcher: hold Back to switch the LG screen off
 * (any button brings it back), hold Back and press Down to mute or Up to unmute. Android shows remote buttons to an accessibility
 * service only after the user switches it on in TV Settings; it reads nothing on screen.
 */
class MuteKeyService : AccessibilityService() {
    private val shortcuts = RemoteShortcuts()

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val action = shortcuts.onKey(event.keyCode, event.action == KeyEvent.ACTION_DOWN, event.repeatCount,
            event.eventTime, event.downTime)
        when (action) {
            Action.MUTE -> Mute.mute(this)
            Action.UNMUTE -> Mute.unmute(this)
            Action.SCREEN_OFF -> LgTv.get(this).let { tv -> tv.scope.launch { if (!tv.screenOff {}) shortcuts.screenIsOff = false } }
            Action.SCREEN_ON -> LgTv.get(this).screenOnLater()
            else -> Unit
        }
        return action != Action.PASS
    }

    override fun onServiceConnected() { running = true }
    override fun onDestroy() { running = false; super.onDestroy() }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    companion object {
        /** While on, the helper handles the shortcuts everywhere, so the launcher must not handle them twice. */
        @Volatile var running = false
    }
}
