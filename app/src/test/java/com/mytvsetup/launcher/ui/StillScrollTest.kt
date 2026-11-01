package com.mytvsetup.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StillScrollTest {
    @Test fun aFullyVisibleItemNeverScrollsTheList() {
        // The TV default re-centred every focus change, nudging the grid ~4 px on each press.
        assertEquals(0f, stillScrollDistance(offset = 400f, size = 274f, containerSize = 900f))
        assertEquals(0f, stillScrollDistance(offset = 20f, size = 274f, containerSize = 900f))
    }

    @Test fun anItemBelowScrollsJustEnoughPlusTheLift() {
        // 274 px item at 800 in a 900 px list: 174 px missing, plus room for the 4% focus lift.
        assertEquals(174f + 274f * (TILE_LIFT - 1f), stillScrollDistance(800f, 274f, 900f), 0.01f)
    }

    @Test fun anItemAboveScrollsBackJustEnough() {
        assertEquals(-50f - 274f * (TILE_LIFT - 1f), stillScrollDistance(-50f, 274f, 900f), 0.01f)
    }

    @Test fun anItemTallerThanTheListAlignsItsStart() {
        assertEquals(30f, stillScrollDistance(30f, 1200f, 900f))
    }
}
