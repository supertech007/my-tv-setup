package com.mytvsetup.launcher.audio

import android.view.KeyEvent

/**
 * Remote shortcuts built on Back, because the stick sends the volume buttons straight to the TV over HDMI and
 * no app can see them, and holding an arrow is how lists scroll fast:
 * hold Back about a second to switch the TV screen off (the next button brings it back), and while holding Back
 * press Down to mute or Up to unmute. A short Back stays a normal Back. Pure logic, shared by the launcher and
 * the accessibility helper that sees buttons in other apps.
 */
class RemoteShortcuts(private val holdMs: Long = 800) {
    enum class Action { PASS, CONSUME, MUTE, UNMUTE, SCREEN_OFF, SCREEN_ON }

    var screenIsOff = false
    private var swallowUntilUp: Int? = null
    private var backHeld = false
    private var backUsed = false

    fun onKey(keyCode: Int, isDown: Boolean, repeat: Int, eventTime: Long, downTime: Long): Action {
        swallowUntilUp?.let { held ->
            if (keyCode == held) { if (!isDown) swallowUntilUp = null; return Action.CONSUME }
        }
        if (screenIsOff && isDown && repeat == 0) {
            screenIsOff = false
            swallowUntilUp = keyCode
            return Action.SCREEN_ON
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) return onBack(isDown, repeat, eventTime - downTime)
        if (backHeld && isDown && repeat == 0 &&
            (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_UP)) {
            backUsed = true
            swallowUntilUp = keyCode
            return if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) Action.MUTE else Action.UNMUTE
        }
        return Action.PASS
    }

    // Apps act on Back when it is released, so the press goes through and only the release is held back once
    // Back has been used for a shortcut.
    private fun onBack(isDown: Boolean, repeat: Int, heldMs: Long): Action = when {
        isDown && repeat == 0 -> { backHeld = true; backUsed = false; Action.PASS }
        isDown && !backUsed && heldMs >= holdMs -> { backUsed = true; screenIsOff = true; Action.SCREEN_OFF }
        isDown -> Action.CONSUME
        else -> { backHeld = false; if (backUsed) Action.CONSUME else Action.PASS }
    }
}
