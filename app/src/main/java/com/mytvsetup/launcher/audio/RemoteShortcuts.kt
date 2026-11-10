package com.mytvsetup.launcher.audio

import android.view.KeyEvent

/**
 * One-hand remote shortcuts. The stick sends the volume buttons straight to the TV over HDMI (no app sees them),
 * holding an arrow scrolls lists, and the Mi remote cannot be pressed two-handed comfortably, so everything starts
 * from Back: hold it about a second to open a small quick menu (Mute or Unmute, Screen off), move with the arrows,
 * choose with OK, close with Back. A short Back stays a normal Back: the app gets none of the press until it is
 * released, then a whole Back ([Action.BACK]). Half a Back (press passed, release kept) leaves apps with Back stuck
 * down, and SmartTube then ignored OK. While the screen is off, the next button only
 * brings the picture back. Pure logic, shared by the launcher and the accessibility helper for other apps.
 */
class RemoteShortcuts(val holdMs: Long = 800, private val items: Int = 2) {
    enum class Action { PASS, CONSUME, OPEN_MENU, MOVE, CHOOSE, CLOSE_MENU, SCREEN_ON, BACK }

    var screenIsOff = false
    var menuOpen = false
        private set
    var selected = 0
        private set
    private var swallowUntilUp: Int? = null
    private var backUsed = false
    private var backDown = false

    fun onKey(keyCode: Int, isDown: Boolean, repeat: Int, eventTime: Long, downTime: Long, longPress: Boolean = false): Action {
        swallowUntilUp?.let { held ->
            if (keyCode == held) { if (!isDown) swallowUntilUp = null; return Action.CONSUME }
        }
        if (screenIsOff && isDown && repeat == 0) {
            screenIsOff = false
            swallowUntilUp = keyCode
            return Action.SCREEN_ON
        }
        if (menuOpen) return onMenu(keyCode, isDown)
        if (keyCode != KeyEvent.KEYCODE_BACK) return Action.PASS
        return when {
            isDown && repeat == 0 -> { backUsed = false; backDown = true; Action.CONSUME }
            // Android flags a long press itself on some remotes; otherwise time the hold.
            isDown && !backUsed && (longPress || eventTime - downTime >= holdMs) -> {
                backUsed = true; menuOpen = true; selected = 0; Action.OPEN_MENU
            }
            isDown -> Action.CONSUME
            else -> { backDown = false; if (backUsed) Action.CONSUME else Action.BACK }
        }
    }

    /**
     * Called [holdMs] after Back went down. An accessibility helper sees only press and release, never Android's
     * repeat signals for a held button, so it times the hold itself and asks here whether Back is still down.
     */
    fun onBackHoldTimer(): Action =
        if (backDown && !backUsed && !menuOpen) { backUsed = true; menuOpen = true; selected = 0; Action.OPEN_MENU }
        else Action.PASS

    private fun onMenu(keyCode: Int, isDown: Boolean): Action {
        if (!isDown) return Action.CONSUME
        swallowUntilUp = keyCode
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT -> { selected = (selected + 1) % items; Action.MOVE }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT -> { selected = (selected + items - 1) % items; Action.MOVE }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                menuOpen = false; Action.CHOOSE
            }
            KeyEvent.KEYCODE_BACK -> { menuOpen = false; Action.CLOSE_MENU }
            else -> Action.CONSUME
        }
    }
}
