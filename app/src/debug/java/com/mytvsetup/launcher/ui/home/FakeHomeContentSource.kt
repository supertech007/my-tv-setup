package com.mytvsetup.launcher.ui.home

import android.content.Context
import com.mytvsetup.launcher.content.*
import kotlinx.coroutines.flow.*
import com.mytvsetup.launcher.ui.swipe.TasteSummary
import kotlin.math.abs

fun defaultHomeContentSource(context: Context): HomeContentSource = FakeHomeContentSource(
    GenreCatalog.fromJson(context.assets.open("genre_synonyms.json").bufferedReader().use { it.readText() })
)

/** Debug snapshot only. Confirmed Hindi/English FLATRATE offers are still required. */
class FakeHomeContentSource(private val genres: GenreCatalog) : HomeContentSource {
    private val allowed = debugTitles.filter { t -> t.offers.any { o -> o.audio.any { it in setOf("hi", "en") } } }
    private val weights = mutableMapOf<String, Double>()
    val tasteSummary = TasteSummary { n -> weights.entries.sortedByDescending { abs(it.value) }.take(n).map { it.key to it.value } }
    private val decided = MutableStateFlow(setOf<String>())
    private val rejected = MutableStateFlow(setOf<String>())
    override val personalise = MutableStateFlow(true)
    override val filters = MutableStateFlow(Filters())
    override val genreGroups = MutableStateFlow(genres.build(allowed.flatMap { it.labels }))
    override val forYou = combine(filters, personalise, rejected) { f, personal, hidden ->
        allowed.filter { t ->
            (f.kinds.isEmpty() || t.kind in f.kinds) &&
                (f.yearMin == null || (t.year ?: 0) >= f.yearMin) &&
                (f.group == null || f.group in genres.groupsOf(t.labels)) &&
                (f.tag == null || t.labels.any { genres.normalise(it) == f.tag }) &&
                (!personal || t.id !in hidden)
        }.let { titles ->
            if (f.sort == Sort.NEWEST) titles.sortedByDescending { it.year ?: 0 }
            else titles.sortedByDescending { it.imdb ?: -1.0 }
        }
    }
    override val swipeQueue = combine(forYou, decided) { titles, seen -> titles.filterNot { it.id in seen } }
    override fun resetTaste() {
        weights.clear()
        decided.value = emptySet()
        rejected.value = emptySet()
    }
    override fun setFilters(f: Filters) { filters.value = f }
    override fun setPersonalise(on: Boolean) { personalise.value = on }
    override suspend fun decide(t: Title, s: Signal) {
        genres.groupsOf(t.labels).forEach { group -> weights[group] = (weights[group] ?: 0.0) + s.weight }
        decided.value += t.id
        if (s == Signal.REJECT) rejected.value += t.id
    }
}
