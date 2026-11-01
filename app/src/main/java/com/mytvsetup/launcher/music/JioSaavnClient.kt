package com.mytvsetup.launcher.music

import com.mytvsetup.launcher.content.contentClient
import com.mytvsetup.launcher.content.requestText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Reads JioSaavn's public featured playlists for one of the chosen music languages. */
class JioSaavnClient(http: OkHttpClient) {
    private val http = http.contentClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun featured(lang: String): List<Playlist> {
        val url = "https://www.jiosaavn.com/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("__call", "content.getFeaturedPlaylists")
            .addQueryParameter("fetch_from_serialized_files", "true")
            .addQueryParameter("p", "1").addQueryParameter("n", "50")
            .addQueryParameter("api_version", "4").addQueryParameter("_format", "json")
            .addQueryParameter("_marker", "0").addQueryParameter("ctx", "web6dot0")
            .addQueryParameter("languages", LANGUAGES.getValue(lang)).build()
        val page = json.decodeFromString<SaavnPage>(requestText(http, Request.Builder().url(url).build(), retries = 1))
        return page.data.filter { it.perma_url.isNotBlank() && it.title.isNotBlank() }.map { item ->
            Playlist("js-${item.id}", item.title, lang, tagsFor(item.title), mapOf(SAAVN to item.perma_url),
                item.title, item.image.takeIf { it.startsWith("https://") })
        }
    }

    companion object {
        const val SAAVN = "com.jio.media.jiobeats"
        val LANGUAGES = mapOf("pa" to "punjabi", "bgc" to "haryanvi", "hi" to "hindi", "en" to "english")
        // JioSaavn gives no genre, so the playlist's own title supplies its tags.
        private val KEYWORDS = listOf("romance" to "Romance", "love" to "Romance", "dance" to "Dance", "party" to "Party",
            "hip hop" to "Hip Hop", "rap" to "Hip Hop", "sad" to "Sad", "chill" to "Chill", "lofi" to "Chill",
            "devotional" to "Devotional", "bhakti" to "Devotional", "90s" to "Retro", "80s" to "Retro", "retro" to "Retro",
            "workout" to "Workout", "rock" to "Rock", "pop" to "Pop", "folk" to "Folk", "hits" to "Hits", "chartbusters" to "Hits")

        internal fun tagsFor(title: String): List<String> {
            val lower = title.lowercase()
            val tags = KEYWORDS.filter { (word, _) -> Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(lower) }
                .map { it.second }.distinct()
            return tags.ifEmpty { listOf(if (lower.startsWith("let's play")) "Artist" else "Featured") }
        }
    }
}

@Serializable
private data class SaavnPage(val data: List<SaavnItem> = emptyList())

@Serializable
private data class SaavnItem(val id: String = "", val title: String = "", val image: String = "", val perma_url: String = "")
