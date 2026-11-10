package com.mytvsetup.launcher.history

import com.mytvsetup.launcher.content.Kind
import com.mytvsetup.launcher.content.Title
import com.mytvsetup.launcher.music.Playlist
import com.mytvsetup.launcher.youtube.YtVideo
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun Title.historyEntry(pkg: String, url: String, service: String) = HistoryEntry(
    if (kind == Kind.SHOW) HistoryKind.SHOW else HistoryKind.MOVIE, id, title, service, posterUrl, pkg, url)

fun Playlist.historyEntry(musicApp: String) = HistoryEntry(HistoryKind.MUSIC, id, name, "Music", image, musicApp, null)

fun YtVideo.historyEntry() = HistoryEntry(HistoryKind.YOUTUBE, id, title, channel, thumb, "org.smarttube.stable",
    "https://www.youtube.com/watch?v=$id")

/** "Today", "Yesterday" or a date such as "Mon, 9 Nov": how History groups what was opened. */
fun historyDay(at: Long, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String {
    val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofPattern(if (day.year == today.year) "EEE, d MMM" else "d MMM yyyy", Locale.ENGLISH))
    }
}

fun historyTime(at: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(at).atZone(zone).format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)).lowercase(Locale.ENGLISH)

fun keepDaysLabel(days: Int) = when (days) { FOREVER -> "Forever"; 365 -> "1 year"; else -> "$days days" }
