package com.mytvsetup.launcher.content

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayLinksTest {
    @Test fun hotstarTitleLinkBecomesItsWatchLink() {
        // Verified on the TV 1 Nov: /watch starts playback with the remembered profile.
        assertEquals("https://www.hotstar.com/in/movies/good-will-hunting/1271672402/watch",
            playUrl("jhs", "https://www.hotstar.com/in/movies/good-will-hunting/1271672402?search_query=Good+Will+Hunting"))
        assertEquals("https://www.hotstar.com/in/movies/x/1/watch", playUrl("jhs", "https://www.hotstar.com/in/movies/x/1/watch"))
    }

    @Test fun primeDetailLinkBecomesItsWatchLink() {
        assertEquals("https://app.primevideo.com/watch?gti=amzn1.dv.gti.ceb8",
            playUrl("prv", "https://app.primevideo.com/detail?gti=amzn1.dv.gti.ceb8"))
    }

    @Test fun otherServicesKeepTheirLink() {
        assertEquals("https://www.sonyliv.com/movies/dc-1090548404", playUrl("snl", "https://www.sonyliv.com/movies/dc-1090548404"))
        assertEquals("https://tv.apple.com/in/episode/pilot/x", playUrl("atp", "https://tv.apple.com/in/episode/pilot/x"))
    }

    @Test fun onlyPrimeIsRestartedForALink() {
        assertEquals(listOf("prv"), listOf("prv", "jhs", "snl", "atp", "zee").filter(::needsFreshStart))
    }
}
