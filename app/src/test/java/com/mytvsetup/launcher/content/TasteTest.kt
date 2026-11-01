package com.mytvsetup.launcher.content

import org.junit.Assert.*
import org.junit.Test

class TasteTest {
    private val genres = GenreCatalog(mapOf("Horror" to listOf("paranormal")))

    @Test fun acceptThenPlayAccumulateThreeAndRejectSubtractsOne() {
        val taste = Taste()
        taste.record(setOf("g:Horror"), Signal.ACCEPT)
        taste.record(setOf("g:Horror"), Signal.PLAY)
        taste.record(setOf("g:Comedy"), Signal.REJECT)
        assertEquals(mapOf("g:Horror" to 3.0, "g:Comedy" to -1.0), taste.snapshot())
    }

    @Test fun scoreClampsEachFeatureButKeepsRawWeights() {
        val taste = Taste(mapOf("g:Horror" to 12.0, "g:Comedy" to -20.0, "svc:prv" to 2.0))
        assertEquals(5.0, taste.score(setOf("g:Horror")), 0.0)
        assertEquals(-5.0, taste.score(setOf("g:Comedy")), 0.0)
        assertEquals(2.0, taste.score(setOf("g:Horror", "g:Comedy", "svc:prv", "unknown")), 0.0)
        assertEquals(12.0, taste.snapshot().getValue("g:Horror"), 0.0)
    }

    @Test fun snapshotIsDetachedAndTopReturnsLargestWeights() {
        val initial = mutableMapOf("a" to 1.0, "b" to 3.0, "c" to -1.0)
        val taste = Taste(initial)
        initial["a"] = 99.0
        val snapshot = taste.snapshot()
        taste.record(setOf("a"), Signal.PLAY)
        assertEquals(1.0, snapshot.getValue("a"), 0.0)
        assertEquals(listOf("a" to 3.0, "b" to 3.0), taste.top(2))
        assertEquals(emptyList<Pair<String, Double>>(), taste.top(0))
    }

    @Test fun featuresUseGroupsSelectedOfferAllowedAudioAndFirstThreeCast() {
        val title = testTitle("horror").copy(
            labels = listOf("paranormal", "hrr"), cast = listOf("A", "B", "C", "D"),
            offers = listOf(Offer("snl", listOf("en"), "sony"), Offer("prv", listOf("hi", "te"), "prime"))
        )
        assertEquals(setOf("g:Horror", "svc:prv", "lang:hi", "cast:A", "cast:B", "cast:C"),
            features(title, genres, title.offers[1]))
    }

    @Test fun emptyAudioUsesConfirmedOriginalLanguageOnlyWithSelectedOffer() {
        val title = testTitle("original").copy(originalLanguage = "en", offers = listOf(Offer("prv", emptyList(), "prime")))
        assertTrue("lang:en" in features(title, genres, title.offers[0]))
        assertFalse(features(title.copy(originalLanguage = "te"), genres, title.offers[0]).any { it.startsWith("lang:") })
        assertFalse(features(title, genres, null).any { it.startsWith("lang:") || it.startsWith("svc:") })
    }
}

internal fun testTitle(id: String, labels: List<String> = emptyList(), imdb: Double? = 7.0,
                       year: Int? = 2024, kind: Kind = Kind.MOVIE) = Title(
    id, kind, id, year, imdb, labels, "synopsis", emptyList(), null, null,
    listOf(Offer("prv", listOf("en"), "https://example.com/$id")), null
)
