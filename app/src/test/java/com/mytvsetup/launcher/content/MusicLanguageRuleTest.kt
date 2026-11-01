package com.mytvsetup.launcher.content

import org.junit.Assert.*
import org.junit.Test

class MusicLanguageRuleTest {
    private fun music(lang: String) = Title(
        "music:$lang", Kind.MUSIC, lang, null, null, listOf("Pop"), "", emptyList(),
        null, null, emptyList(), null, originalLanguage = lang
    )

    @Test fun punjabiAndHaryanviNeedNoVideoOfferOrVideoLanguage() {
        assertTrue(isAllowed(music("pa"), setOf("en"), emptyList()))
        assertTrue(isAllowed(music("bgc"), setOf("hi"), emptyList()))
    }

    @Test fun tamilMusicCannotEnterRankingEvenWithPersonalisationOff() {
        val items = listOf(music("ta"), music("pa"))
        for (personalise in listOf(true, false)) {
            assertEquals(listOf("music:pa"), rank(items, Filters(kinds = setOf(Kind.MUSIC)),
                personalise, Taste(), emptySet(), GenreCatalog(emptyMap())).map { it.id })
        }
    }

    @Test fun tamilMusicCannotBorrowAnAllowedVideoOffer() {
        val tamil = music("ta").copy(offers = listOf(Offer("prv", listOf("hi"), "prime")))
        assertFalse(isAllowed(tamil, setOf("hi"), listOf("prv")))
        assertFalse(isAllowed(music("pa").copy(originalLanguage = null), setOf("hi"), listOf("prv")))
    }
}
