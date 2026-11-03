package com.mytvsetup.launcher.audio

import com.mytvsetup.launcher.audio.RemoteShortcuts.Action.*
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteShortcutsTest {
    private val back = 4    // KeyEvent.KEYCODE_BACK
    private val up = 19     // KeyEvent.KEYCODE_DPAD_UP
    private val down = 20   // KeyEvent.KEYCODE_DPAD_DOWN
    private val ok = 23

    @Test fun aShortBackIsStillBack() {
        val r = RemoteShortcuts()
        assertEquals(PASS, r.onKey(back, true, 0, 1_000, 1_000))
        assertEquals(PASS, r.onKey(back, false, 0, 1_150, 1_000))
    }

    @Test fun holdingBackSwitchesTheScreenOffWithoutGoingBack() {
        val r = RemoteShortcuts()
        assertEquals(PASS, r.onKey(back, true, 0, 1_000, 1_000))
        assertEquals(CONSUME, r.onKey(back, true, 1, 1_400, 1_000))
        assertEquals(SCREEN_OFF, r.onKey(back, true, 5, 1_900, 1_000))
        assertEquals(CONSUME, r.onKey(back, true, 6, 1_950, 1_000))
        assertEquals(CONSUME, r.onKey(back, false, 0, 2_100, 1_000)) // no Back in the app behind
    }

    @Test fun anyButtonBringsThePictureBackAndDoesNothingElse() {
        val r = RemoteShortcuts()
        r.onKey(back, true, 0, 1_000, 1_000); r.onKey(back, true, 5, 1_900, 1_000); r.onKey(back, false, 0, 2_000, 1_000)
        assertEquals(SCREEN_ON, r.onKey(ok, true, 0, 9_000, 9_000))
        assertEquals(CONSUME, r.onKey(ok, false, 0, 9_100, 9_000))
        assertEquals(PASS, r.onKey(ok, true, 0, 9_500, 9_500))
    }

    @Test fun backPlusDownMutesAndBackPlusUpUnmutes() {
        val r = RemoteShortcuts()
        r.onKey(back, true, 0, 1_000, 1_000)
        assertEquals(MUTE, r.onKey(down, true, 0, 1_200, 1_200))
        assertEquals(CONSUME, r.onKey(down, false, 0, 1_300, 1_200))
        // The chord used Back, so letting go is not a Back press and the hold does not switch the screen off.
        assertEquals(CONSUME, r.onKey(back, true, 9, 2_500, 1_000))
        assertEquals(CONSUME, r.onKey(back, false, 0, 2_600, 1_000))
        r.onKey(back, true, 0, 5_000, 5_000)
        assertEquals(UNMUTE, r.onKey(up, true, 0, 5_200, 5_200))
    }

    @Test fun arrowsAloneStillScroll() {
        // Holding Down to scroll a long list must never mute.
        val r = RemoteShortcuts()
        assertEquals(PASS, r.onKey(down, true, 0, 1_000, 1_000))
        assertEquals(PASS, r.onKey(down, true, 8, 3_000, 1_000))
        assertEquals(PASS, r.onKey(up, true, 0, 4_000, 4_000))
    }
}
