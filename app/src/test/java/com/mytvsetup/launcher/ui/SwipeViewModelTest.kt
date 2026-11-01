package com.mytvsetup.launcher.ui

import com.mytvsetup.launcher.apps.LaunchResult
import com.mytvsetup.launcher.content.*
import com.mytvsetup.launcher.ui.home.HomeContentSource
import com.mytvsetup.launcher.ui.swipe.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class SwipeViewModelTest {
    private val horror = title("horror", listOf("hrr"))
    private val drama = title("drama", listOf("drm"))

    @Test fun rightRecordsAcceptAndAdvances() = fixture { repo, vm, _ ->
        vm.right()
        assertEquals(listOf("horror" to Signal.ACCEPT), repo.decisions)
        assertEquals("drama", vm.state.value.current?.id)
        assertEquals(1, vm.state.value.index)
        assertEquals(2, vm.state.value.total)
        assertEquals(listOf("Horror" to 1.0), vm.state.value.taste)
    }

    @Test fun leftRecordsRejectAndAdvances() = fixture { repo, vm, _ ->
        vm.left()
        assertEquals(listOf("horror" to Signal.REJECT), repo.decisions)
        assertEquals("drama", vm.state.value.current?.id)
    }

    @Test fun okRecordsPlayAndOpensConfirmedOfferInItsServicePackage() = fixture { repo, vm, launches ->
        vm.ok()
        assertEquals(listOf("horror" to Signal.PLAY), repo.decisions)
        assertEquals(listOf("com.sonyliv" to "https://www.sonyliv.com/horror"), launches)
        assertEquals("drama", vm.state.value.current?.id)
    }

    @Test fun togglingHorrorRestrictsQueueAndTogglingAgainRestoresBoth() = fixture { _, vm, _ ->
        vm.toggleChip("Horror")
        assertEquals("horror", vm.state.value.current?.id)
        assertEquals(1, vm.state.value.total)
        assertTrue(vm.state.value.chips.single { it.id == "Horror" }.selected)
        vm.toggleChip("Horror")
        assertEquals(2, vm.state.value.total)
    }

    @Test fun emptyQueueHasNoCurrentAndActionsAreNoOps() = fixture(emptyList()) { repo, vm, launches ->
        assertNull(vm.state.value.current)
        vm.left(); vm.right(); vm.ok()
        assertTrue(repo.decisions.isEmpty())
        assertTrue(launches.isEmpty())
        assertEquals(0, vm.state.value.total)
    }

    @Test fun unavailableAppKeepsCurrentAndShowsFeedback() = fixture(result = LaunchResult.NotInstalled("SonyLIV")) { repo, vm, _ ->
        vm.ok()
        assertTrue(repo.decisions.isEmpty())
        assertEquals("horror", vm.state.value.current?.id)
        assertEquals("SonyLIV isn't installed on this TV yet", vm.state.value.toast)
    }

    @Test fun movieAndYearChipsRestrictQueue() = fixture(listOf(horror, drama.copy(kind = Kind.SHOW, year = 2026))) { _, vm, _ ->
        vm.toggleChip("Movies")
        assertEquals(1, vm.state.value.total)
        vm.toggleChip("TV shows")
        vm.toggleChip("This year")
        assertEquals("drama", vm.state.value.current?.id)
        assertEquals(1, vm.state.value.total)
    }

    @Test fun unconfirmedAudioNeverLaunchesOrLearnsPlay() = fixture(listOf(horror.copy(offers = listOf(Offer("snl", listOf("fr"), "wrong"))))) { repo, vm, launches ->
        vm.ok()
        assertTrue(repo.decisions.isEmpty())
        assertTrue(launches.isEmpty())
        assertEquals("No confirmed Hindi or English offer available", vm.state.value.toast)
    }

    @Test fun audioLabelOnlyIncludesAllowedLanguagesOnChosenOffer() {
        val t = horror.copy(originalLanguage = "hi", offers = listOf(
            Offer("prv", listOf("ta"), "wrong"),
            Offer("snl", listOf("en", "hi", "kn", "ta", "te", "hi"), "chosen")
        ))
        assertEquals("Audio: Hindi · English", swipeAudioLabel(t))
        assertEquals("Audio: English", swipeAudioLabel(t.copy(offers = listOf(Offer("snl", listOf("en", "ta"), "chosen")))))
    }

    @Test fun audioLabelUsesOriginalLanguageOnlyWhenChosenOfferHasNoTracks() {
        val empty = horror.copy(originalLanguage = "hi", offers = listOf(Offer("snl", emptyList(), "chosen")))
        assertEquals("Audio: Hindi", swipeAudioLabel(empty))
        assertNull(swipeAudioLabel(empty.copy(originalLanguage = "fr")))
        assertNull(swipeAudioLabel(empty.copy(offers = listOf(Offer("snl", listOf("ta"), "wrong")))))
    }

    private fun fixture(titles: List<Title> = listOf(horror, drama), result: LaunchResult = LaunchResult.Ok,
                        block: (Repo, SwipeViewModel, MutableList<Pair<String, String>>) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo = Repo(titles)
        val launches = mutableListOf<Pair<String, String>>()
        val vm = SwipeViewModel(repo, SwipeLauncher { pkg, url, _ -> launches += pkg to url; result },
            TasteSummary { repo.weights.entries.sortedByDescending { kotlin.math.abs(it.value) }.take(it).map { it.key to it.value } }, scope,
            currentYear = { 2026 })
        try { block(repo, vm, launches) } finally { scope.cancel() }
    }

    private class Repo(titles: List<Title>) : HomeContentSource {
        val decisions = mutableListOf<Pair<String, Signal>>()
        val weights = mutableMapOf<String, Double>()
        private val catalogue = GenreCatalog(mapOf("Horror" to listOf("horror"), "Drama" to listOf("drama")))
        private val remaining = MutableStateFlow(titles)
        override val filters = MutableStateFlow(Filters())
        override val personalise = MutableStateFlow(true)
        override val genreGroups = MutableStateFlow(catalogue.build(titles.flatMap { it.labels }))
        override val swipeQueue = combine(remaining, filters) { queue, f -> queue.filter { t ->
            (f.kinds.isEmpty() || t.kind in f.kinds) && (f.yearMin == null || (t.year ?: 0) >= f.yearMin) &&
                (f.group == null || f.group in catalogue.groupsOf(t.labels))
        } }
        override val forYou = swipeQueue
        override fun resetTaste() { decisions.clear(); weights.clear() }
        override fun setFilters(f: Filters) { filters.value = f }
        override fun setPersonalise(on: Boolean) { personalise.value = on }
        override suspend fun decide(t: Title, s: Signal) {
            decisions += t.id to s
            catalogue.groupsOf(t.labels).forEach { weights[it] = (weights[it] ?: 0.0) + s.weight }
            remaining.value = remaining.value.filterNot { it.id == t.id }
        }
    }

    private fun title(id: String, labels: List<String>) = Title(id, Kind.MOVIE, id, 2020, 8.1,
        labels, "A story", listOf("An actor"), null, null, listOf(
            Offer("prv", listOf("fr"), "wrong"), Offer("snl", listOf("hi"), "https://www.sonyliv.com/$id")
        ), null)
}
