package com.mytvsetup.launcher.content

import kotlinx.serialization.Serializable

@Serializable
enum class Kind { MOVIE, SHOW, MUSIC, YOUTUBE }

@Serializable
data class Offer(val service: String, val audio: List<String>, val url: String)

@Serializable
data class Title(
    val id: String,
    val kind: Kind,
    val title: String,
    val year: Int?,
    val imdb: Double?,
    val labels: List<String>,
    val synopsis: String,
    val cast: List<String>,
    val posterUrl: String?,
    val backdropUrl: String?,
    val offers: List<Offer>,
    val tmdbId: String?,
    val originalLanguage: String? = null,
    val runtimeMin: Int? = null
)
