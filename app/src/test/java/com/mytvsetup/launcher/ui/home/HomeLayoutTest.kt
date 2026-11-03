package com.mytvsetup.launcher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeLayoutTest {
    private val all = setOf("foryou", "swipe", "music", "jarvis", "youtube")

    @Test fun allCardsKeepTheApprovedDesign() {
        val l = homeLayout(all)
        assertEquals(CardBox(830, 72, 760, 400), l["foryou"])
        assertEquals(CardBox(1620, 72, 220, 400), l["swipe"])
        assertEquals(CardBox(830, 502, 320, 210), l["music"])
        assertEquals(CardBox(1170, 502, 360, 210), l["jarvis"])
        assertEquals(CardBox(1550, 502, 290, 210), l["youtube"])
    }

    @Test fun hidingSwipeLetsForYouTakeTheWholeTopRow() {
        assertEquals(CardBox(830, 72, 1010, 400), homeLayout(all - "swipe")["foryou"])
    }

    @Test fun theMiddleRowSharesItsWidthWhenACardIsHidden() {
        val l = homeLayout(all - "jarvis")
        assertFalse("jarvis" in l)
        val music = l.getValue("music"); val youtube = l.getValue("youtube")
        assertEquals(830, music.x)
        assertEquals(music.x + music.w + 20, youtube.x)
        assertEquals(1840, youtube.x + youtube.w) // still reaches the right edge
    }

    @Test fun anEmptyRowGivesItsHeightToTheOther() {
        val topOnly = homeLayout(setOf("foryou", "swipe"))
        assertEquals(640, topOnly.getValue("foryou").h)
        val middleOnly = homeLayout(setOf("music"))
        assertEquals(CardBox(830, 72, 1010, 640), middleOnly["music"])
    }

    @Test fun nothingVisibleMeansNoBoxes() {
        assertEquals(emptyMap<String, CardBox>(), homeLayout(emptySet()))
    }
}
