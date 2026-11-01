package com.mytvsetup.launcher.content

enum class Signal(val weight: Double) { ACCEPT(1.0), REJECT(-1.0), PLAY(2.0) }
enum class Sort { IMDB, NEWEST }
data class Filters(
    val kinds: Set<Kind> = emptySet(), // empty = all
    val yearMin: Int? = null,
    val group: String? = null,
    val tag: String? = null,
    val sort: Sort = Sort.IMDB
)

internal val VIDEO_SERVICES = listOf("prv", "jhs", "snl", "zee", "atp")
internal val VIDEO_LANGUAGES = setOf("hi", "en")
/** A keyword shared by fewer titles than this is too rare to be a genre chip. */
internal const val MIN_KEYWORD_TITLES = 12
internal val MUSIC_LANGUAGES = setOf("pa", "bgc", "hi", "en")
