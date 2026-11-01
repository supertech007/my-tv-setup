package com.mytvsetup.launcher.content

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.mytvsetup.launcher.data.ContentDao
import com.mytvsetup.launcher.data.DecisionEntity
import com.mytvsetup.launcher.data.TasteEntity
import com.mytvsetup.launcher.data.TitleEntity
import com.mytvsetup.launcher.settings.PrefsRepository
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RepositoryOfflineTest {
    @get:Rule val temporary = TemporaryFolder()
    private val genres = GenreCatalog(mapOf("Horror" to listOf("paranormal"), "Comedy" to listOf("comedy")))

    private suspend fun withRepository(
        dao: MemoryContentDao = MemoryContentDao(),
        fetch: suspend (Kind, String?, Int?) -> Pair<List<Title>, String?> = { _, _, _ -> throw IOException("offline") },
        enrich: suspend (Title) -> Title = { it },
        check: suspend (ContentRepository, MemoryContentDao, PrefsRepository, CoroutineScope) -> Unit
    ) {
        val job = SupervisorJob()
        val scope = CoroutineScope(Dispatchers.Default + job)
        val prefs = PrefsRepository(PreferenceDataStoreFactory.create(scope = scope) {
            temporary.newFolder().resolve("prefs.preferences_pb")
        })
        try {
            val repo = ContentRepository(dao, genres, prefs, scope, fetch, enrich)
            withTimeout(5_000) { check(repo, dao, prefs, scope) }
        } finally {
            scope.cancel()
            job.join()
        }
    }

    @Test fun refreshFailureLeavesCachedTitlesAvailableWithoutEscaping() = runBlocking {
        val cached = testTitle("cached", listOf("paranormal"))
        withRepository(MemoryContentDao(listOf(cached))) { repo, _, _, _ ->
            val result = repo.refresh()
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IOException)
            assertEquals(listOf(cached), repo.forYou.first())
            assertEquals(listOf(cached), repo.swipeQueue.first())
            assertEquals(listOf(GenreGroup("Horror", setOf("paranormal"))), repo.genreGroups.first())
        }
    }

    @Test fun decisionsPersistTasteAndExcludeAllDecidedTitlesFromSwipeEvenWithPersonaliseOff() = runBlocking {
        val accepted = testTitle("accept", listOf("paranormal"))
        val rejected = testTitle("reject", listOf("comedy"))
        val played = testTitle("play", listOf("paranormal"))
        val unseen = testTitle("unseen", listOf("paranormal"))
        withRepository(MemoryContentDao(listOf(accepted, rejected, played, unseen))) { repo, dao, prefs, scope ->
            repo.decide(accepted, Signal.ACCEPT)
            repo.decide(rejected, Signal.REJECT)
            repo.decide(played, Signal.PLAY)
            assertEquals(listOf(unseen), repo.swipeQueue.first())
            assertFalse(repo.forYou.first().any { it.id == rejected.id })
            assertEquals(3.0, dao.observeTaste().first().associate { it.feature to it.weight }.getValue("g:Horror"), 0.0)
            repo.setPersonalise(false)
            prefs.prefs.first { !it.personalise }
            assertEquals(4, repo.forYou.first().size)
            assertEquals(listOf(unseen), repo.swipeQueue.first())
            val recreated = ContentRepository(dao, genres, prefs, scope, { _, _, _ -> throw IOException() }, { it })
            assertFalse(recreated.personalise.first { !it })
            assertEquals(listOf(unseen), recreated.swipeQueue.first())
        }
    }

    @Test fun refreshFetchesSixPagesPerKindAndEnrichesBeforeCheckingLanguage() = runBlocking {
        val requests = mutableListOf<Pair<Kind, String?>>()
        val enriched = mutableListOf<String>()
        val foreign = testTitle("foreign").copy(offers = listOf(Offer("prv", listOf("te"), "prime")))
        withRepository(fetch = { kind, cursor, yearMin ->
            assertNull(yearMin)
            requests += kind to cursor
            val page = cursor?.toInt() ?: 0
            listOf(testTitle("$kind$page", kind = kind).copy(
                offers = listOf(Offer("prv", emptyList(), "prime"))), foreign) to (page + 1).toString()
        }, enrich = { title -> enriched += title.id; title.copy(originalLanguage = "en") }) { repo, _, _, _ ->
            assertTrue(repo.refresh().isSuccess)
            assertEquals(12, requests.size)
            for (kind in listOf(Kind.MOVIE, Kind.SHOW)) {
                assertEquals(listOf(null, "1", "2", "3", "4", "5"), requests.filter { it.first == kind }.map { it.second })
            }
            assertEquals(12, repo.forYou.first().size)
            assertTrue(repo.forYou.first().all { it.originalLanguage == "en" })
            assertTrue(enriched.contains("MOVIE0"))
            assertFalse(repo.forYou.first().any { it.id == "foreign" })
        }
    }

    @Test fun refreshStopsAtEndOfPagesAndUpsertsWithoutDeletingCache() = runBlocking {
        val cached = testTitle("cached")
        var calls = 0
        withRepository(MemoryContentDao(listOf(cached)), fetch = { kind, _, _ ->
            calls++
            listOf(testTitle(kind.name, kind = kind)) to null
        }) { repo, _, _, _ ->
            repo.setFilters(Filters(kinds = setOf(Kind.MOVIE)))
            assertEquals(setOf(Kind.MOVIE), repo.filters.value.kinds)
            assertTrue(repo.refresh().isSuccess)
            assertTrue(repo.refresh().isSuccess)
            assertEquals(4, calls)
            assertEquals(setOf("cached", "MOVIE"), repo.forYou.first().map { it.id }.toSet())
        }
    }

    @Test fun failureOnPageThreeKeepsFirstTwoPagesAndReportsPartialSuccess() = runBlocking {
        val cached = testTitle("cached")
        val cache = MemoryContentDao(listOf(cached))
        withRepository(cache, fetch = { _, cursor, _ ->
            when (cursor) {
                null -> listOf(testTitle("page1")) to "page2"
                "page2" -> {
                    assertEquals(setOf("cached", "page1"), cache.observeTitles().first().map { it.id }.toSet())
                    listOf(testTitle("page2")) to "page3"
                }
                else -> {
                    assertEquals(setOf("cached", "page1", "page2"), cache.observeTitles().first().map { it.id }.toSet())
                    throw IOException("page 3 failed")
                }
            }
        }) { repo, dao, _, _ ->
            assertTrue(repo.refresh().isSuccess)
            assertEquals(setOf("cached", "page1", "page2"), dao.observeTitles().first().map { it.id }.toSet())
            assertEquals(setOf("cached", "page1", "page2"), repo.forYou.first().map { it.id }.toSet())
        }
    }

    @Test fun failureAfterOnlyIneligiblePagesReportsFailureDespiteExistingCache() = runBlocking {
        val foreign = testTitle("foreign").copy(offers = listOf(Offer("prv", listOf("te"), "prime")))
        withRepository(MemoryContentDao(listOf(testTitle("cached"))), fetch = { _, cursor, _ ->
            if (cursor != null) throw IOException("page 2 failed")
            listOf(foreign) to "next"
        }) { repo, _, _, _ ->
            assertTrue(repo.refresh().isFailure)
            assertEquals(listOf("cached"), repo.forYou.first().map { it.id })
        }
    }

    @Test fun enrichmentIOExceptionKeepsConfirmedAudioAndContinuesOtherTitles() = runBlocking {
        val confirmed = testTitle("confirmed")
        val unconfirmed = testTitle("unconfirmed").copy(offers = listOf(Offer("prv", emptyList(), "prime")))
        val foreign = testTitle("foreign").copy(offers = listOf(Offer("prv", listOf("te"), "prime")))
        val enriched = testTitle("enriched").copy(originalLanguage = "en")
        withRepository(fetch = { _, _, _ ->
            listOf(confirmed, unconfirmed, foreign, testTitle("enriched")) to null
        }, enrich = {
            if (it.id != "enriched") throw IOException("TMDB unavailable")
            enriched
        }) { repo, _, _, _ ->
            assertTrue(repo.refresh().isSuccess)
            assertEquals(setOf(confirmed, enriched), repo.forYou.first().toSet())
        }
    }

    @Test fun rankingAndDecisionsUseTheSamePreferenceSelectedOffer() = runBlocking {
        val dual = testTitle("dual").copy(offers = listOf(
            Offer("prv", listOf("hi"), "prime"), Offer("snl", listOf("en"), "sony")))
        val control = testTitle("control", imdb = 7.5).copy(
            offers = listOf(Offer("prv", listOf("hi", "en"), "prime")))
        withRepository(MemoryContentDao(listOf(control, dual))) { repo, dao, prefs, _ ->
            prefs.update { it.copy(videoLangs = setOf("en")) }
            repo.decide(dual, Signal.ACCEPT)
            assertEquals(mapOf("svc:snl" to 1.0, "lang:en" to 1.0),
                dao.observeTaste().first().associate { it.feature to it.weight })
            assertEquals(listOf("dual", "control"), repo.forYou.first().map { it.id })
            prefs.update { it.copy(videoLangs = setOf("hi")) }
            assertEquals(listOf("control", "dual"), repo.forYou.first().map { it.id })
        }
    }

    @Test fun cancellationPropagatesInsteadOfBeingReportedAsOffline() = runBlocking {
        withRepository(fetch = { _, _, _ -> throw CancellationException("stopped") }) { repo, _, _, _ ->
            var cancelled = false
            try { repo.refresh() } catch (_: CancellationException) { cancelled = true }
            assertTrue(cancelled)
        }
    }
}

/** R6: pure JVM fake of the production DAO contract, with reactive snapshots. */
internal class MemoryContentDao(initial: List<Title> = emptyList()) : ContentDao {
    private val titles = MutableStateFlow(initial.map(TitleEntity::from))
    private val taste = MutableStateFlow(emptyList<TasteEntity>())
    private val decisions = MutableStateFlow(emptyList<DecisionEntity>())
    override fun observeTitles(): Flow<List<TitleEntity>> = titles
    override fun observeTaste(): Flow<List<TasteEntity>> = taste
    override fun observeDecisions(): Flow<List<DecisionEntity>> = decisions
    override suspend fun upsertTitles(items: List<TitleEntity>) {
        titles.value = (titles.value + items).associateBy { it.id }.values.toList()
    }
    override suspend fun recordDecision(titleId: String, signal: Signal, at: Long, features: Set<String>) {
        val weights = Taste(taste.value.associate { it.feature to it.weight })
        weights.record(features, signal)
        taste.value = weights.snapshot().map { (feature, weight) -> TasteEntity(feature, weight) }
        decisions.value += DecisionEntity(titleId = titleId, signal = signal, at = at)
    }
    override suspend fun clearLearning() { taste.value = emptyList(); decisions.value = emptyList() }
}
