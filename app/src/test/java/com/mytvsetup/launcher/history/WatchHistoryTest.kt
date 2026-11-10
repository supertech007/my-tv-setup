package com.mytvsetup.launcher.history

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WatchHistoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val day = 86_400_000L
    private var now = 100 * day
    private fun history(file: java.io.File = temporary.root.resolve("history.json")) = WatchHistory(file) { now }
    private fun movie(ref: String, name: String = ref) =
        HistoryEntry(HistoryKind.MOVIE, ref, name, "Prime Video", null, "com.amazon.amazonvideo.livingroom", "https://x/$ref")

    @Test fun newestComesFirstAndReopeningMovesItToTheTop() {
        val h = history()
        h.record(movie("a"), keepDays = 30); now += 1_000
        h.record(movie("b"), keepDays = 30); now += 1_000
        h.record(movie("a"), keepDays = 30)
        assertEquals(listOf("a", "b"), h.entries.value.map { it.ref })
        assertEquals(now, h.entries.value.first().at)
    }

    @Test fun entriesOlderThanTheKeepDaysAreDropped() {
        val h = history()
        h.record(movie("old"), keepDays = 7)
        now += 8 * day
        h.record(movie("new"), keepDays = 7)
        assertEquals(listOf("new"), h.entries.value.map { it.ref })
    }

    @Test fun shorteningTheKeepDaysPrunesStraightAway() {
        val h = history()
        h.record(movie("a"), keepDays = 90); now += 10 * day
        h.prune(keepDays = 7)
        assertTrue(h.entries.value.isEmpty())
    }

    @Test fun keepForeverNeverDrops() {
        val h = history()
        h.record(movie("a"), keepDays = FOREVER); now += 3_000 * day
        h.prune(FOREVER)
        assertEquals(1, h.entries.value.size)
    }

    @Test fun clearEmptiesItAndSurvivesARestart() {
        val file = temporary.root.resolve("h.json")
        val h = history(file)
        h.record(movie("a"), keepDays = 30)
        assertEquals(listOf("a"), history(file).entries.value.map { it.ref })
        h.clear()
        assertTrue(history(file).entries.value.isEmpty())
    }

    @Test fun aDamagedFileStartsEmpty() {
        val file = temporary.root.resolve("bad.json").apply { writeText("{not json") }
        assertTrue(history(file).entries.value.isEmpty())
    }

    @Test fun sameRefOfADifferentKindIsAnotherEntry() {
        val h = history()
        h.record(movie("x"), 30)
        h.record(movie("x").copy(kind = HistoryKind.APP), 30)
        assertEquals(2, h.entries.value.size)
    }
}

class HistoryLabelsTest {
    private val zone = java.time.ZoneId.of("Asia/Kolkata")
    private val today = java.time.LocalDate.of(2026, 11, 10)
    private fun at(y: Int, m: Int, d: Int, h: Int = 21, min: Int = 5) =
        java.time.ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun daysReadNaturally() {
        assertEquals("Today", historyDay(at(2026, 11, 10), today, zone))
        assertEquals("Yesterday", historyDay(at(2026, 11, 9, 23, 59), today, zone))
        assertEquals("Sun, 8 Nov", historyDay(at(2026, 11, 8), today, zone))
        assertEquals("8 Nov 2025", historyDay(at(2025, 11, 8), today, zone))
    }

    @Test fun timesAreTwelveHour() {
        assertEquals("9:05 pm", historyTime(at(2026, 11, 10), zone))
    }

    @Test fun keepChoicesHaveFriendlyNames() {
        assertEquals(listOf("7 days", "30 days", "90 days", "1 year", "Forever"), KEEP_DAY_CHOICES.map(::keepDaysLabel))
    }
}

class NowPlayingTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun aSmartTubeVideoBecomesAReplayableYouTubeEntry() {
        val e = nowPlayingEntry("org.smarttube.stable", "SmartTube", "Me at the zoo", "jawed", "jNQXAC9IVRw", null)!!
        assertEquals(HistoryKind.YOUTUBE, e.kind)
        assertEquals("jawed · SmartTube", e.detail)
        assertEquals("https://www.youtube.com/watch?v=jNQXAC9IVRw", e.url)
    }

    @Test fun otherAppsGiveTheTitleNotTheApp() {
        val e = nowPlayingEntry("com.amazon.amazonvideo.livingroom", "Prime Video", "Panchayat", "S3 E4", null, "https://img/x.jpg")!!
        assertEquals(HistoryKind.VIDEO, e.kind)
        assertEquals("Panchayat", e.name)
        assertEquals("S3 E4 · Prime Video", e.detail)
        assertNull(e.url)
        assertEquals(HistoryKind.MUSIC, nowPlayingEntry("com.jio.media.jiobeats", "JioSaavn", "Kesariya", "Arijit Singh", null, null)!!.kind)
    }

    @Test fun noTitleOrOnlyTheAppNameIsSkipped() {
        assertNull(nowPlayingEntry("org.smarttube.stable", "SmartTube", null, null, null, null))
        assertNull(nowPlayingEntry("org.smarttube.stable", "SmartTube", "  ", null, null, null))
        assertNull(nowPlayingEntry("com.sonyliv", "Sony LIV", "Sony LIV", null, null, null))
    }

    @Test fun aTitleOpenedFromMyTvAndThenReportedPlayingStaysOneEntryWithItsLink() {
        val h = WatchHistory(temporary.root.resolve("h.json")) { 5_000 }
        val opened = HistoryEntry(HistoryKind.MOVIE, "tm1", "Panchayat", "Prime Video", "https://p/1.jpg",
            "com.amazon.amazonvideo.livingroom", "https://watch/1")
        h.record(opened, 30)
        h.record(nowPlayingEntry("com.amazon.amazonvideo.livingroom", "Prime Video", "Panchayat", null, null, null)!!, 30)
        assertEquals(1, h.entries.value.size)
        assertEquals("https://watch/1", h.entries.value.single().url)
    }
}
