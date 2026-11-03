package com.mytvsetup.launcher.audio

import com.mytvsetup.launcher.audio.RemoteShortcuts.Action.*
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteShortcutsTest {
    private val back = 4    // KeyEvent.KEYCODE_BACK
    private val up = 19     // KeyEvent.KEYCODE_DPAD_UP
    private val down = 20   // KeyEvent.KEYCODE_DPAD_DOWN
    private val ok = 23

    private fun holdBack(r: RemoteShortcuts, at: Long = 1_000) {
        assertEquals(PASS, r.onKey(back, true, 0, at, at))
        assertEquals(CONSUME, r.onKey(back, true, 1, at + 400, at))
        assertEquals(OPEN_MENU, r.onKey(back, true, 5, at + 900, at))
        assertEquals(CONSUME, r.onKey(back, false, 0, at + 1_000, at)) // no Back in the app behind
    }

    @Test fun aShortBackIsStillBack() {
        val r = RemoteShortcuts()
        assertEquals(PASS, r.onKey(back, true, 0, 1_000, 1_000))
        assertEquals(PASS, r.onKey(back, false, 0, 1_150, 1_000))
    }

    @Test fun holdingBackOpensTheQuickMenuOnTheFirstItem() {
        val r = RemoteShortcuts()
        holdBack(r)
        assertEquals(0, r.selected)
    }

    @Test fun arrowsMoveTheSelectionAndOkChoosesIt() {
        val r = RemoteShortcuts(items = 2)
        holdBack(r)
        assertEquals(MOVE, r.onKey(down, true, 0, 3_000, 3_000))
        assertEquals(1, r.selected)
        assertEquals(CONSUME, r.onKey(down, false, 0, 3_100, 3_000))
        assertEquals(MOVE, r.onKey(down, true, 0, 3_200, 3_200))
        assertEquals(0, r.selected) // wraps round
        assertEquals(MOVE, r.onKey(up, true, 0, 3_300, 3_300))
        assertEquals(1, r.selected)
        assertEquals(CHOOSE, r.onKey(ok, true, 0, 4_000, 4_000))
        assertEquals(CONSUME, r.onKey(ok, false, 0, 4_100, 4_000))
        // Menu closed: the next OK reaches the app again.
        assertEquals(PASS, r.onKey(ok, true, 0, 5_000, 5_000))
    }

    @Test fun backClosesTheMenuWithoutGoingBack() {
        val r = RemoteShortcuts()
        holdBack(r)
        assertEquals(CLOSE_MENU, r.onKey(back, true, 0, 3_000, 3_000))
        assertEquals(CONSUME, r.onKey(back, false, 0, 3_100, 3_000))
        assertEquals(PASS, r.onKey(down, true, 0, 4_000, 4_000))
    }

    @Test fun afterScreenOffAnyButtonBringsThePictureBackAndDoesNothingElse() {
        val r = RemoteShortcuts()
        holdBack(r)
        r.onKey(ok, true, 0, 3_000, 3_000); r.onKey(ok, false, 0, 3_100, 3_000)
        r.screenIsOff = true
        assertEquals(SCREEN_ON, r.onKey(down, true, 0, 9_000, 9_000))
        assertEquals(CONSUME, r.onKey(down, false, 0, 9_100, 9_000))
        assertEquals(PASS, r.onKey(down, true, 0, 9_500, 9_500))
    }

    @Test fun arrowsAloneStillScroll() {
        val r = RemoteShortcuts()
        assertEquals(PASS, r.onKey(down, true, 0, 1_000, 1_000))
        assertEquals(PASS, r.onKey(down, true, 8, 3_000, 1_000))
        assertEquals(PASS, r.onKey(up, true, 0, 4_000, 4_000))
    }

    @Test fun androidsLongPressFlagAlsoOpensTheMenu() {
        val r = RemoteShortcuts()
        assertEquals(PASS, r.onKey(back, true, 0, 1_000, 1_000))
        assertEquals(OPEN_MENU, r.onKey(back, true, 1, 1_000, 1_000, longPress = true))
    }

    @Test fun aTimedHoldOpensTheMenuWhenNoRepeatsArrive() {
        // The accessibility helper only sees press and release (checked on the TV).
        val r = RemoteShortcuts()
        assertEquals(PASS, r.onKey(back, true, 0, 1_000, 1_000))
        assertEquals(OPEN_MENU, r.onBackHoldTimer())
        assertEquals(CONSUME, r.onKey(back, false, 0, 1_900, 1_000))
        assertEquals(MOVE, r.onKey(down, true, 0, 3_000, 3_000))
    }

    @Test fun aReleasedBackDoesNotOpenTheMenuWhenTheTimerFires() {
        val r = RemoteShortcuts()
        r.onKey(back, true, 0, 1_000, 1_000)
        assertEquals(PASS, r.onKey(back, false, 0, 1_200, 1_000))
        assertEquals(PASS, r.onBackHoldTimer())
    }
}
