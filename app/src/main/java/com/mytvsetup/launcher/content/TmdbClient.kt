package com.mytvsetup.launcher.content

import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class TmdbClient(http: OkHttpClient, private val key: String) {
    private val http = http.contentClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun enrich(t: Title): Title {
        if (key.isBlank() || t.tmdbId.isNullOrBlank()) return t
        val type = when (t.kind) {
            Kind.MOVIE -> "movie"
            Kind.SHOW -> "tv"
            else -> return t
        }
        val details = json.decodeFromString<TmdbDetails>(get(type, t.tmdbId))
        val detailsOnly = t.copy(originalLanguage = details.original_language)
        val keywords = try {
            json.decodeFromString<TmdbKeywords>(get(type, t.tmdbId, "keywords"))
        } catch (_: IOException) {
            return detailsOnly
        } catch (_: SerializationException) {
            return detailsOnly
        }
        val names = (if (t.kind == Kind.MOVIE) keywords.keywords else keywords.results).map { it.name }
        return detailsOnly.copy(labels = (t.labels + names).distinct())
    }

    private suspend fun get(type: String, id: String, suffix: String? = null): String {
        val url = "https://api.themoviedb.org/3/".toHttpUrl().newBuilder()
            .addPathSegment(type).addPathSegment(id).apply {
                if (suffix != null) addPathSegment(suffix)
            }.addQueryParameter("api_key", key).build()
        return requestText(http, Request.Builder().url(url).build(), retries = 1)
    }
}

@Serializable private data class TmdbDetails(val original_language: String? = null)
@Serializable private data class TmdbKeywords(
    val keywords: List<TmdbKeyword> = emptyList(), val results: List<TmdbKeyword> = emptyList()
)
@Serializable private data class TmdbKeyword(val name: String)
