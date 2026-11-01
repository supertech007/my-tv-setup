package com.mytvsetup.launcher.content

class Taste(initial: Map<String, Double> = emptyMap()) {
    private val weights = initial.toMutableMap()

    fun record(features: Set<String>, s: Signal) {
        features.forEach { feature -> weights[feature] = weights.getOrDefault(feature, 0.0) + s.weight }
    }

    fun score(features: Set<String>): Double =
        features.sumOf { weights.getOrDefault(it, 0.0).coerceIn(-5.0, 5.0) }

    fun top(n: Int): List<Pair<String, Double>> = weights.toList().sortedByDescending { it.second }.take(n)

    fun snapshot(): Map<String, Double> = weights.toMap()
}

fun features(t: Title, genres: GenreCatalog, offer: Offer?): Set<String> = buildSet {
    genres.groupsOf(t.labels).forEach { add("g:$it") }
    if (offer != null) {
        add("svc:${offer.service}")
        val audio = if (offer.audio.isEmpty()) listOfNotNull(t.originalLanguage) else offer.audio
        audio.filter { it in VIDEO_LANGUAGES }.forEach { add("lang:$it") }
    }
    t.cast.take(3).forEach { add("cast:$it") }
}
