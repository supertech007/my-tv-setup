package com.mytvsetup.launcher.content

import org.junit.Assert.*
import org.junit.Test

class RankingTest {
    private val genres = GenreCatalog(mapOf("Horror" to listOf("paranormal"), "Comedy" to listOf("comedy")))
    private val horror = testTitle("horror", listOf("paranormal"), 7.5, 2020)
    private val comedy = testTitle("comedy", listOf("comedy"), 8.0, 2024, Kind.SHOW)
    private val taste = Taste(mapOf("g:Horror" to 3.0, "g:Comedy" to -1.0))
    private fun ranked(items: List<Title> = listOf(comedy, horror), f: Filters = Filters(),
                       personalise: Boolean = true, rejected: Set<String> = emptySet()) =
        rank(items, f, personalise, taste, rejected, genres)

    @Test fun personalisedScorePromotesHorror() {
        assertEquals(listOf(horror, comedy), ranked())
    }

    @Test fun personaliseOffUsesImdbAndShowsRejectedTitles() {
        assertEquals(listOf(comedy, horror), ranked(personalise = false, rejected = setOf("comedy")))
    }

    @Test fun rejectedTitlesHiddenOnlyWhenPersonaliseOn() {
        assertEquals(listOf(horror), ranked(rejected = setOf("comedy")))
    }

    @Test fun groupIncludesSynonymOnlyTitle() {
        assertEquals(listOf(horror), ranked(f = Filters(group = "Horror")))
    }

    @Test fun movieFilterDropsShows() {
        assertEquals(listOf(horror), ranked(f = Filters(kinds = setOf(Kind.MOVIE))))
    }

    @Test fun newestOrdersByYearWithNullsLast() {
        val unknown = testTitle("unknown", year = null)
        assertEquals(listOf(comedy, horror, unknown), ranked(listOf(unknown, horror, comedy),
            Filters(sort = Sort.NEWEST), personalise = false))
    }

    @Test fun newestSortStillAppliesWhenPersonalised() {
        // Personalise reorders by taste; it must not swallow the Newest choice.
        assertEquals(listOf(comedy, horror), ranked(f = Filters(sort = Sort.NEWEST)))
    }

    @Test fun personalisedNewestBreaksSameYearTiesByTaste() {
        val horror2024 = testTitle("horror2024", listOf("paranormal"), 6.0, 2024)
        assertEquals(listOf(horror2024, comedy, horror), ranked(listOf(comedy, horror, horror2024), Filters(sort = Sort.NEWEST)))
    }

    @Test fun imdbNullsLastWithoutTaste() {
        val unknown = testTitle("unknown", imdb = null)
        assertEquals(listOf(comedy, horror, unknown), ranked(listOf(unknown, horror, comedy), personalise = false))
    }

    @Test fun unknownImdbUsesSixAsPersonalisedBaseScore() {
        val unknown = horror.copy(id = "unknown", imdb = null)
        assertEquals(listOf(unknown, comedy), ranked(listOf(comedy, unknown)))
    }

    @Test fun yearAndNormalisedTagFiltersCompose() {
        val old = horror.copy(id = "old", year = 1990)
        val unknown = horror.copy(id = "unknown", year = null)
        assertEquals(listOf(horror), ranked(listOf(comedy, old, unknown, horror),
            Filters(yearMin = 2000, group = "Horror", tag = " PARANORMAL ")))
    }

    @Test fun titlesWithoutAnyGenreGroupStillAppearWithoutGroupFilter() {
        val ungrouped = testTitle("european", listOf("eur"))
        assertTrue(genres.groupsOf(ungrouped.labels).isEmpty())
        assertEquals(listOf(ungrouped), ranked(listOf(ungrouped)))
        assertEquals(emptyList<Title>(), ranked(listOf(ungrouped), Filters(group = "Horror")))
    }
}
