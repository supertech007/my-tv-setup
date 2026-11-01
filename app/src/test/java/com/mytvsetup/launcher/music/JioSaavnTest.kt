package com.mytvsetup.launcher.music

import com.mytvsetup.launcher.content.ContentTestServer
import com.mytvsetup.launcher.content.fixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JioSaavnTest {
    @get:Rule val temporary = TemporaryFolder()
    private val saavn = "com.jio.media.jiobeats"

    @Test fun featuredPlaylistsBecomePlaylistsWithArtAndLinks() = runBlocking {
        ContentTestServer().use { api ->
            api.enqueue(fixture("saavn_featured.json"))
            val lists = JioSaavnClient(api.http).featured("pa")
            assertEquals("punjabi", api.urls.single().queryParameter("languages"))
            assertEquals("www.jiosaavn.com", api.urls.single().host)
            // An entry without a link cannot be opened, so it is dropped.
            assertEquals(listOf("Best of Romance - Punjabi", "Let's Play - Sidhu Moose Wala - Punjabi", "Viral Nation"),
                lists.map { it.name })
            val romance = lists.first()
            assertEquals("js-158225486", romance.id)
            assertEquals("pa", romance.lang)
            assertTrue(romance.image!!.startsWith("https://c.saavncdn.com/"))
            assertEquals("https://www.jiosaavn.com/featured/best-of-romance-punjabi/s3X0s4hSgi-femJ68FuXsA__", romance.links[saavn])
            assertEquals(listOf("Romance"), romance.tags)
            assertEquals(listOf("Artist"), lists[1].tags)
            assertEquals(listOf("Featured"), lists[2].tags)
        }
    }

    @Test fun haryanviUsesItsJioSaavnLanguageName() = runBlocking {
        ContentTestServer().use { api ->
            api.enqueue(fixture("saavn_featured.json"))
            JioSaavnClient(api.http).featured("bgc")
            assertEquals("haryanvi", api.urls.single().queryParameter("languages"))
        }
    }

    @Test fun fetchedPlaylistsReplaceBundledOnesWithTheSameLinkAndSurviveARestart() {
        val file = temporary.newFile("music.json")
        val bundled = Playlist("romance", "Punjabi Romance", "pa", listOf("Romance"),
            mapOf(saavn to "https://www.jiosaavn.com/featured/best-of-romance-punjabi/s3X0s4hSgi-femJ68FuXsA__"), "Punjabi Romance")
        val fetched = listOf(
            bundled.copy(id = "js-1", name = "Best of Romance - Punjabi", image = "https://c.saavncdn.com/a.jpg"),
            Playlist("js-2", "Viral Nation", "pa", listOf("Featured"), mapOf(saavn to "https://www.jiosaavn.com/featured/v/x"),
                "Viral Nation", "https://c.saavncdn.com/b.jpg")
        )
        val catalog = MusicCatalog(listOf(bundled), store = file)
        catalog.replaceFetched(fetched)
        assertEquals(listOf("js-1", "js-2"), catalog.playlists.value.map { it.id })
        // A new process reads the saved list back.
        assertEquals(listOf("js-1", "js-2"), MusicCatalog(listOf(bundled), store = file).playlists.value.map { it.id })
    }

    @Test fun anEmptyFetchKeepsWhatWeHave() {
        val file = temporary.newFile("music.json")
        val bundled = Playlist("a", "A", "pa", listOf("Pop"), emptyMap(), "A")
        val catalog = MusicCatalog(listOf(bundled), store = file)
        catalog.replaceFetched(emptyList())
        assertEquals(listOf("a"), catalog.playlists.value.map { it.id })
    }

    @Test fun playlistArtBecomesThePoster() {
        val p = Playlist("js-1", "X", "pa", listOf("Pop"), emptyMap(), "X", "https://c.saavncdn.com/a.jpg")
        assertEquals("https://c.saavncdn.com/a.jpg", p.toTitle().posterUrl)
    }
}
