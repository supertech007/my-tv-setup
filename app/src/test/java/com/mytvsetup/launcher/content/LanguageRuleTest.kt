package com.mytvsetup.launcher.content

import org.junit.Assert.*
import org.junit.Test

class LanguageRuleTest {
    private val langs = setOf("hi", "en")
    private val services = listOf("jhs", "prv", "snl", "zee", "atp")
    private fun title(audio: List<String>, original: String? = null) = Title(
        "test", Kind.MOVIE, "Test", 2021, null, emptyList(), "", emptyList(),
        null, null, listOf(Offer("prv", audio, "https://example.com/play")), "690957", original
    )

    @Test fun teluguOriginalWithConfirmedHindiDubIsAllowed() {
        assertTrue(isAllowed(title(listOf("hi"), "te"), langs, services))
    }

    @Test fun kannadaOnlyAudioIsRejectedEvenWhenOriginalIsEnglish() {
        assertFalse(isAllowed(title(listOf("kn"), "en"), langs, services))
    }

    @Test fun emptyAudioWithHindiOriginalIsAllowed() {
        assertTrue(isAllowed(title(emptyList(), "hi"), langs, services))
    }

    @Test fun emptyAudioWithUnknownOrTeluguOriginalIsRejected() {
        assertFalse(isAllowed(title(emptyList()), langs, services))
        assertFalse(isAllowed(title(emptyList(), "te"), langs, services))
    }

    @Test fun oppenheimerSelectsFirstMatchingServiceInPreferenceOrder() {
        val prime = Offer("prv", listOf("en"), "prime")
        val hotstar = Offer("jhs", listOf("en", "hi", "ta"), "hotstar")
        val t = title(listOf("en")).copy(offers = listOf(prime, hotstar))
        assertEquals(hotstar, bestOffer(t, langs, listOf("jhs", "prv")))
        assertEquals(prime, bestOffer(t, langs, listOf("prv", "jhs")))
    }

    @Test fun skipsPreferredServiceWithNoMatchingAudioAndExcludesUnselectedServices() {
        val t = title(listOf("hi")).copy(offers = listOf(
            Offer("jhs", listOf("kn"), "hotstar"), Offer("prv", listOf("hi"), "prime")
        ))
        assertEquals("prv", bestOffer(t, langs, services)?.service)
        assertNull(bestOffer(t, langs, listOf("atp")))
        assertNull(bestOffer(t, emptySet(), services))
        assertNull(bestOffer(t.copy(offers = emptyList()), langs, services))
    }
}
