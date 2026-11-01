package com.mytvsetup.launcher.content

import com.mytvsetup.launcher.ui.home.HomeContentSource
import com.mytvsetup.launcher.ui.swipe.TasteSummary

import com.mytvsetup.launcher.data.ContentDao
import com.mytvsetup.launcher.data.TitleEntity
import com.mytvsetup.launcher.music.MusicCatalog
import com.mytvsetup.launcher.music.toTitle
import com.mytvsetup.launcher.settings.Prefs
import com.mytvsetup.launcher.settings.PrefsRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ContentRepository(
    private val dao: ContentDao,
    private val genres: GenreCatalog,
    private val prefs: PrefsRepository,
    private val scope: CoroutineScope,
    private val fetchPage: suspend (Kind, String?, Int?) -> Pair<List<Title>, String?>,
    private val enrich: suspend (Title) -> Title,
    private val music: MusicCatalog? = null
) : HomeContentSource {
    constructor(
        dao: ContentDao, genres: GenreCatalog, prefs: PrefsRepository, scope: CoroutineScope,
        justWatch: JustWatchClient, tmdb: TmdbClient, music: MusicCatalog? = null
    ) : this(dao, genres, prefs, scope, justWatch::page, tmdb::enrich, music)

    private val mutableFilters = MutableStateFlow(Filters())
    override val filters: StateFlow<Filters> = mutableFilters.asStateFlow()
    override val personalise: StateFlow<Boolean> = prefs.prefs.map { it.personalise }
        .stateIn(scope, SharingStarted.Eagerly, Prefs().personalise)
    private val refreshMutex = Mutex()

    private val learning = combine(dao.observeTaste(), dao.observeDecisions()) { weights, decisions ->
        val latest = decisions.associateBy { it.titleId }
        Learning(
            Taste(weights.associate { it.feature to it.weight }),
            latest.filterValues { it.signal == Signal.REJECT }.keys,
            latest.keys
        )
    }
    // Computed off the main thread so ranking never stalls focus movement on the stick's slow CPU.
    private fun <T> Flow<T>.offMain() = flowOn(Dispatchers.Default)

    // Playlists are part of the flow so a JioSaavn refresh shows up without a restart.
    private val content = combine(dao.observeTitles(), prefs.prefs, music?.playlists ?: flowOf(emptyList())) { rows, settings, _ ->
        val playlists = music?.forLangs(settings.musicLangs).orEmpty().map { it.toTitle() }
        (playlists + rows.map { it.toTitle() }).distinctBy { it.id }.filter {
            isAllowed(it, settings.videoLangs.intersect(VIDEO_LANGUAGES), VIDEO_SERVICES, settings.musicLangs)
        }
    }.offMain()
    private val ranked = combine(content, learning, prefs.prefs, filters) { titles, learned, settings, f ->
        rank(titles, f, settings.personalise, learned.taste, learned.rejected, genres,
            settings.videoLangs.intersect(VIDEO_LANGUAGES), settings.musicLangs) to learned.decided
    }.offMain()
    override val forYou: Flow<List<Title>> = ranked.map { it.first }
    // Swipe rates movies and shows only, so For you's Music tab must not empty it.
    override val swipeQueue: Flow<List<Title>> = combine(content, learning, prefs.prefs, filters) { titles, learned, settings, f ->
        rank(titles, f.copy(kinds = f.kinds - Kind.MUSIC), settings.personalise, learned.taste, learned.rejected, genres,
            settings.videoLangs.intersect(VIDEO_LANGUAGES), settings.musicLangs)
            .filter { it.kind != Kind.MUSIC && it.id !in learned.decided }
    }.offMain()
    // Chips follow the selected tab, so the Music tab offers music genres rather than movie ones.
    override val genreGroups: Flow<List<GenreGroup>> = combine(content, filters.map { it.kinds }.distinctUntilChanged()) { all, kinds ->
        val titles = if (kinds.isEmpty()) all else all.filter { it.kind in kinds }
        genres.build(titles.flatMap { it.labels }, MIN_KEYWORD_TITLES, titles.filter { it.kind == Kind.MUSIC }.flatMap { it.labels })
    }
        .offMain()

    override fun genreOf(t: Title): String? = genres.primaryGenre(t)

    suspend fun refresh(): Result<Unit> = refreshMutex.withLock {
        var stored = false
        try {
            val languages = prefs.prefs.first().videoLangs.intersect(VIDEO_LANGUAGES)
            for (kind in listOf(Kind.MOVIE, Kind.SHOW)) {
                var cursor: String? = null
                for (page in 0 until 6) {
                    val (titles, next) = fetchPage(kind, cursor, null)
                    val fetched = linkedMapOf<String, Title>()
                    for (title in titles) {
                        // Empty-audio offers need TMDB original_language before the final language gate.
                        val enriched = try { enrich(title) } catch (e: CancellationException) { throw e } catch (_: Exception) { title }
                        if (isAllowed(enriched, languages, VIDEO_SERVICES)) fetched[enriched.id] = enriched
                    }
                    dao.upsertTitles(fetched.values.map(TitleEntity::from))
                    stored = stored || fetched.isNotEmpty()
                    if (next == null || next == cursor) break
                    cursor = next
                }
            }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (stored) Result.success(Unit) else Result.failure(error)
        }
    }

    override suspend fun decide(t: Title, s: Signal) {
        val languages = prefs.prefs.first().videoLangs.intersect(VIDEO_LANGUAGES)
        dao.recordDecision(t.id, s, System.currentTimeMillis(), features(t, genres, bestOffer(t, languages, VIDEO_SERVICES)))
    }

    override fun setFilters(f: Filters) { mutableFilters.value = f }

    override fun resetTaste() { scope.launch { dao.clearLearning() } }

    private val latestTaste = learning.map { it.taste }.stateIn(scope, SharingStarted.Eagerly, Taste())
    val tasteSummary = TasteSummary { n -> latestTaste.value.top(n) }

    override fun setPersonalise(on: Boolean) {
        scope.launch { prefs.update { it.copy(personalise = on) } }
    }

    private data class Learning(val taste: Taste, val rejected: Set<String>, val decided: Set<String>)
}
