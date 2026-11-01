package com.mytvsetup.launcher.content

private val SHORT_GENRES = mapOf("Kids & Family" to "Family", "Music & Musical" to "Musical", "Reality TV" to "Reality",
    "Documentary" to "Docu", "Animation" to "Animated")

/** Fits a genre into the small badge on a poster: "Action & Adventure" becomes "Action". */
fun shortGenre(group: String): String = SHORT_GENRES[group] ?: group.substringBefore(" & ").trim()

/** The first genre a title carries; JustWatch genres come before TMDB keywords in its labels. */
fun GenreCatalog.primaryGenre(t: Title): String? = groupsOf(t.labels).firstOrNull()?.let(::shortGenre)

/** Line above the IMDb badge: genre and year, or just the year when a genre filter already says the genre. */
fun posterInfo(genre: String?, year: Int?, genreFiltered: Boolean): String? =
    listOfNotNull(genre.takeUnless { genreFiltered }, year?.toString()).joinToString(" · ").ifEmpty { null }
