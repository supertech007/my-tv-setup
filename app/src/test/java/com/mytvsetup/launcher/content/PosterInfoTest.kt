package com.mytvsetup.launcher.content

import org.junit.Assert.assertEquals
import org.junit.Test

class PosterInfoTest {
    @Test fun longGenreNamesAreShortened() {
        assertEquals(listOf("Action", "Family", "Musical", "War", "Reality", "Docu", "Drama", "Sci-Fi"),
            listOf("Action & Adventure", "Kids & Family", "Music & Musical", "War & Military", "Reality TV",
                "Documentary", "Drama", "Sci-Fi").map(::shortGenre))
        assertEquals("Animated", shortGenre("Animation"))
    }

    @Test fun genreAndYearShowWhenNoGenreIsFiltered() {
        assertEquals("Drama · 2011", posterInfo("Drama", 2011, genreFiltered = false))
    }

    @Test fun onlyTheYearShowsWhenAGenreIsFiltered() {
        // The filter already says the genre.
        assertEquals("2011", posterInfo("Drama", 2011, genreFiltered = true))
    }

    @Test fun missingPartsAreLeftOut() {
        assertEquals("Romance", posterInfo("Romance", null, genreFiltered = false))
        assertEquals("2020", posterInfo(null, 2020, genreFiltered = false))
        assertEquals(null, posterInfo(null, null, genreFiltered = false))
        assertEquals(null, posterInfo("Pop", null, genreFiltered = true))
    }

    @Test fun firstGenreOfTheTitleIsUsed() {
        val genres = GenreCatalog(mapOf("Drama" to listOf("drama"), "Crime" to listOf("crime")))
        assertEquals("Crime", genres.primaryGenre(testTitle("x", listOf("crm", "drm"))))
        assertEquals(null, genres.primaryGenre(testTitle("y")))
    }
}
