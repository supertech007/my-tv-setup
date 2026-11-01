package com.mytvsetup.launcher.content

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mytvsetup.launcher.music.MusicCatalog
import com.mytvsetup.launcher.music.Playlist
import com.mytvsetup.launcher.music.toTitle
import com.mytvsetup.launcher.settings.PrefsRepository
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MusicRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val playlists = Json.decodeFromString<List<Playlist>>(
        javaClass.getResource("/music_playlists.json")!!.readText()
    )

    private suspend fun withRepository(
        check: suspend (ContentRepository, PrefsRepository, MemoryContentDao) -> Unit
    ) {
        val job = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.Default + job)
        val prefs = PrefsRepository(PreferenceDataStoreFactory.create(scope = scope) {
            temporary.newFolder().resolve("prefs.preferences_pb")
        })
        val dao = MemoryContentDao()
        val repo = ContentRepository(dao, GenreCatalog(emptyMap()), prefs, scope,
            { _, _, _ -> throw IOException("offline") }, { it }, music = MusicCatalog(playlists))
        try {
            withTimeout(5_000) { check(repo, prefs, dao) }
        } finally {
            scope.cancel()
            job.join()
        }
    }

    @Test fun playlistsJoinForYouMusicTabAndGenreCatalogueWithoutNetworkOrOffers() = runBlocking {
        withRepository { repo, _, _ ->
            assertTrue(repo.refresh().isFailure)
            repo.setFilters(Filters(kinds = setOf(Kind.MUSIC)))
            val titles = repo.forYou.first()
            assertEquals(setOf("music:pa", "music:bgc", "music:hi", "music:en"), titles.map { it.id }.toSet())
            assertTrue(titles.all { it.kind == Kind.MUSIC && it.offers.isEmpty() })
            assertEquals(listOf("Pop", "Dance"), titles.first { it.id == "music:pa" }.labels)
            assertEquals(setOf("Pop", "Dance", "Folk", "Romance", "Chill"),
                repo.genreGroups.first().map { it.name }.toSet())
            assertFalse(titles.any { it.originalLanguage == "ta" })
        }
    }

    @Test fun genreChipsFollowTheSelectedTab() = runBlocking {
        withRepository { repo, _, dao ->
            // This catalogue knows no genres, so Drama needs enough titles to pass the keyword threshold.
            dao.upsertTitles(List(MIN_KEYWORD_TITLES) { com.mytvsetup.launcher.data.TitleEntity.from(testTitle("drama$it", listOf("drm"))) })
            assertTrue(repo.genreGroups.first { g -> g.any { it.name == "Drama" } }.any { it.name == "Pop" })
            // On the Music tab, movie genres would only lead to empty results.
            repo.setFilters(Filters(kinds = setOf(Kind.MUSIC)))
            val music = repo.genreGroups.first { g -> g.none { it.name == "Drama" } }.map { it.name }
            assertTrue("Pop" in music)
            repo.setFilters(Filters(kinds = setOf(Kind.MOVIE)))
            assertEquals(listOf("Drama"), repo.genreGroups.first { g -> g.none { it.name == "Pop" } }.map { it.name })
        }
    }

    @Test fun preferencesGateMusicIndependentlyOfVideoAndUpdateGenresAndRanking() = runBlocking {
        withRepository { repo, prefs, dao ->
            prefs.update { it.copy(videoLangs = setOf("en"), musicLangs = setOf("bgc")) }
            dao.upsertTitles(listOf(
                com.mytvsetup.launcher.data.TitleEntity.from(testTitle("english").copy(
                    offers = listOf(Offer("prv", listOf("en"), "prime")))),
                com.mytvsetup.launcher.data.TitleEntity.from(testTitle("hindi").copy(
                    offers = listOf(Offer("prv", listOf("hi"), "prime"))))
            ))
            assertEquals(setOf("english", "music:bgc"), repo.forYou.first().map { it.id }.toSet())
            repo.setFilters(Filters(kinds = setOf(Kind.MUSIC)))
            assertEquals(listOf("music:bgc"), repo.forYou.first().map { it.id })
            // Swipe rates movies and shows only, even while For you shows the Music tab.
            assertEquals(listOf("english"), repo.swipeQueue.first().map { it.id })
            assertEquals(listOf(GenreGroup("Folk", setOf("folk"))), repo.genreGroups.first())
            prefs.update { it.copy(musicLangs = setOf("pa")) }
            assertEquals(listOf("music:pa"), repo.forYou.first { it.any { t -> t.id == "music:pa" } }.map { it.id })
            prefs.update { it.copy(musicLangs = setOf("ta"), personalise = false) }
            assertTrue(repo.forYou.first { it.isEmpty() }.isEmpty())
            assertFalse(repo.genreGroups.first().any { it.name == "Tamil Only" })
        }
    }

    @Test fun cachedTamilCannotAppearAndCatalogueTitlesAreNotDuplicated() = runBlocking {
        withRepository { repo, _, dao ->
            dao.upsertTitles(playlists.map { com.mytvsetup.launcher.data.TitleEntity.from(it.toTitle()) })
            val titles = repo.forYou.first()
            assertEquals(4, titles.size)
            assertEquals(4, titles.map { it.id }.toSet().size)
            assertFalse(titles.any { it.id == "music:ta" })
        }
    }

    @Test fun musicDecisionsLearnPlaylistGenresWithoutVideoServices() = runBlocking {
        withRepository { repo, _, dao ->
            repo.decide(playlists.first().toTitle(), Signal.ACCEPT)
            assertEquals(mapOf("g:Pop" to 1.0, "g:Dance" to 1.0),
                dao.observeTaste().first().associate { it.feature to it.weight })
            assertFalse(repo.swipeQueue.first().any { it.id == "music:pa" })
        }
    }
}
