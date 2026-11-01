package com.mytvsetup.launcher.content

fun bestOffer(t: Title, langs: Set<String>, services: List<String>): Offer? =
    services.firstNotNullOfOrNull { service ->
        t.offers.firstOrNull { offer ->
            offer.service == service && if (offer.audio.isEmpty()) {
                t.originalLanguage != null && t.originalLanguage in langs
            } else {
                offer.audio.any { it in langs }
            }
        }
    }

fun isAllowed(
    t: Title, langs: Set<String>, services: List<String>,
    musicLangs: Set<String> = MUSIC_LANGUAGES
): Boolean = if (t.kind == Kind.MUSIC) {
    t.originalLanguage in musicLangs.intersect(MUSIC_LANGUAGES)
} else {
    bestOffer(t, langs, services) != null
}
