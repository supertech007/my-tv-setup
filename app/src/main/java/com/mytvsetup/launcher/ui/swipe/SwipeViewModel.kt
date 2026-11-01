package com.mytvsetup.launcher.ui.swipe

import com.mytvsetup.launcher.apps.LaunchResult
import com.mytvsetup.launcher.content.*
import com.mytvsetup.launcher.ui.home.HomeContentSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Year
import kotlin.math.abs

fun interface TasteSummary { fun top(n: Int): List<Pair<String, Double>> }
fun interface SwipeLauncher { fun openUrlIn(pkg: String, url: String, label: String): LaunchResult }
data class Chip(val id: String, val selected: Boolean)
data class SwipeState(val current: Title? = null, val index: Int = 0, val total: Int = 0,
    val chips: List<Chip> = emptyList(), val taste: List<Pair<String, Double>> = emptyList(), val toast: String? = null)

/** Scope is owned by the screen so collectors stop as soon as Back returns home. */
class SwipeViewModel(private val repo: HomeContentSource, private val launcher: SwipeLauncher,
    private val taste: TasteSummary, private val scope: CoroutineScope,
    private val currentYear: () -> Int = { Year.now().value }) {
    private val mutableState = MutableStateFlow(SwipeState())
    val state: StateFlow<SwipeState> = mutableState.asStateFlow()
    private var queue = emptyList<Title>()
    private var groups = emptyList<GenreGroup>()
    private var filters = Filters()
    private var completed = emptySet<String>()
    private var busy = false

    init {
        // Swipe starts with both video kinds, independent of the Home tab last visited.
        repo.setFilters(filters)
        scope.launch { repo.swipeQueue.collect { queue = it; publish() } }
        scope.launch { repo.genreGroups.collect { groups = it; publish() } }
    }

    fun left() = decide(Signal.REJECT)
    fun right() = decide(Signal.ACCEPT)
    fun ok() {
        val title = state.value.current ?: return
        if (busy) return
        val offer = swipeOffer(title) ?: return feedback("No confirmed Hindi or English offer available")
        val result = launcher.openUrlIn(servicePackages.getValue(offer.service), playUrl(offer.service, offer.url), serviceLabel(offer.service))
        when (result) {
            LaunchResult.Ok -> decide(Signal.PLAY)
            is LaunchResult.NotInstalled -> feedback("${result.label} isn't installed on this TV yet")
        }
    }

    fun toggleChip(id: String) {
        if (busy) return
        val next = when (id) {
            "Movies" -> filters.copy(kinds = setOf(Kind.MOVIE))
            "TV shows" -> filters.copy(kinds = setOf(Kind.SHOW))
            "Both" -> filters.copy(kinds = emptySet())
            "This year" -> filters.copy(yearMin = currentYear())
            "Any year" -> filters.copy(yearMin = null)
            else -> if (groups.any { it.name == id }) filters.copy(group = id.takeUnless { filters.group == id }) else return
        }
        if (next == filters) return
        filters = next
        completed = emptySet()
        repo.setFilters(filters)
        publish()
    }

    fun clearToast() { mutableState.value = state.value.copy(toast = null) }

    private fun decide(signal: Signal) {
        val title = state.value.current ?: return
        if (busy) return
        busy = true
        scope.launch {
            try {
                repo.decide(title, signal)
                completed += title.id
                busy = false
                publish()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { busy = false; feedback("Couldn't save your rating. Try again.") }
        }
    }

    private fun feedback(message: String) { mutableState.value = state.value.copy(toast = message) }

    private fun publish() {
        if (busy) return
        val remaining = queue.filter { it.id !in completed && it.kind in setOf(Kind.MOVIE, Kind.SHOW) }
        val chips = listOf(Chip("Movies", filters.kinds == setOf(Kind.MOVIE)),
            Chip("TV shows", filters.kinds == setOf(Kind.SHOW)), Chip("Both", filters.kinds.isEmpty()),
            Chip("This year", filters.yearMin != null), Chip("Any year", filters.yearMin == null)) +
            groups.map { Chip(it.name, filters.group == it.name) }
        mutableState.value = SwipeState(remaining.firstOrNull(), completed.size, completed.size + remaining.size,
            chips, taste.top(5).sortedByDescending { abs(it.second) }.take(5), state.value.toast)
    }
}

internal val servicePackages = mapOf("prv" to "com.amazon.amazonvideo.livingroom", "jhs" to "in.startv.hotstar",
    "snl" to "com.sonyliv", "zee" to "com.graymatrix.did", "atp" to "com.apple.atve.androidtv.appletv")

/** Replace with LanguageRule.bestOffer at the controller merge; that module is absent here. */
internal fun swipeOffer(title: Title): Offer? = title.offers.firstOrNull { offer ->
    offer.service in servicePackages && (offer.audio.any { it == "hi" || it == "en" } ||
        (offer.audio.isEmpty() && title.originalLanguage in setOf("hi", "en")))
}

internal fun swipeAudioLabel(title: Title): String? {
    val offer = swipeOffer(title) ?: return null
    val audio = offer.audio.ifEmpty { listOfNotNull(title.originalLanguage) }
    val allowed = listOf("hi" to "Hindi", "en" to "English")
        .filter { it.first in audio }.map { it.second }
    return allowed.takeIf { it.isNotEmpty() }?.joinToString(" · ", prefix = "Audio: ")
}

internal fun serviceLabel(code: String): String = when (code) {
    "prv" -> "Prime Video"; "jhs" -> "JioHotstar"; "snl" -> "SonyLIV"; "zee" -> "ZEE5"; "atp" -> "Apple TV"
    else -> code
}
