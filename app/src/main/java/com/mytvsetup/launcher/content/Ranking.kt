package com.mytvsetup.launcher.content

fun rank(
    items: List<Title>, f: Filters, personalise: Boolean, taste: Taste,
    rejected: Set<String>, genres: GenreCatalog, languages: Set<String> = VIDEO_LANGUAGES,
    musicLanguages: Set<String> = MUSIC_LANGUAGES
): List<Title> {
    val filtered = items.filter { t ->
        (t.kind != Kind.MUSIC || isAllowed(t, languages, VIDEO_SERVICES, musicLanguages)) &&
            (f.kinds.isEmpty() || t.kind in f.kinds) &&
            (f.yearMin == null || (t.year != null && t.year >= f.yearMin)) &&
            (f.group == null || f.group in genres.groupsOf(t.labels)) &&
            (f.tag == null || t.labels.any { genres.normalise(it) == genres.normalise(f.tag) }) &&
            (!personalise || t.id !in rejected)
    }
    // Score each title once; a selector inside the comparator would recompute features per comparison.
    // Personalise adds taste to IMDb; the Newest/IMDb choice always decides the order.
    val score = filtered.associate { t ->
        t.id to if (personalise) (t.imdb ?: 6.0) + 0.6 * taste.score(features(t, genres, bestOffer(t, languages, VIDEO_SERVICES)))
        else t.imdb
    }
    return when (f.sort) {
        Sort.IMDB -> filtered.sortedWith(compareByDescending<Title> { score[it.id] != null }.thenByDescending { score[it.id] })
        Sort.NEWEST -> filtered.sortedWith(compareByDescending<Title> { it.year != null }.thenByDescending { it.year }
            .thenByDescending { score[it.id] })
    }
}
