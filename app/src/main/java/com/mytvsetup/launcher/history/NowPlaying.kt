package com.mytvsetup.launcher.history

private const val SMARTTUBE = "org.smarttube.stable"
private val YOUTUBE_APPS = setOf(SMARTTUBE, "com.google.android.youtube.tv", "com.google.android.youtube.tvkids")
internal val MUSIC_APPS = setOf("com.jio.media.jiobeats", "com.amazon.music.tv", "com.spotify.tv.android",
    "com.google.android.youtube.tvmusic", "com.apple.android.music", "com.apple.android.music.tv")
private val YOUTUBE_ID = Regex("^[A-Za-z0-9_-]{11}$")

/**
 * What another app says it is playing, as a History entry: the title, not the app. Null when the app gave no
 * usable title (some only report their own name). A YouTube video id lets OK play the same video again.
 */
fun nowPlayingEntry(pkg: String, appLabel: String, title: String?, subtitle: String?, mediaId: String?, art: String?): HistoryEntry? {
    val name = title?.trim().orEmpty()
    if (name.isEmpty() || name.equals(appLabel, ignoreCase = true)) return null
    val detail = listOfNotNull(subtitle?.trim()?.takeIf { it.isNotEmpty() && !it.equals(name, true) }, appLabel)
        .distinct().joinToString(" · ")
    return when {
        pkg in YOUTUBE_APPS -> HistoryEntry(HistoryKind.YOUTUBE, mediaId?.takeIf { YOUTUBE_ID.matches(it) } ?: name, name, detail,
            art, pkg, mediaId?.takeIf { YOUTUBE_ID.matches(it) }?.let { "https://www.youtube.com/watch?v=$it" })
        pkg in MUSIC_APPS -> HistoryEntry(HistoryKind.MUSIC, "$pkg:$name", name, detail, art, pkg, null)
        else -> HistoryEntry(HistoryKind.VIDEO, "$pkg:$name", name, detail, art, pkg, null)
    }
}
