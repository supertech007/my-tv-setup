package com.mytvsetup.launcher.youtube

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Instant

interface YouTubeSource {
    suspend fun latest(acct: YtAccount): List<YtVideo>
    suspend fun playlists(acct: YtAccount): List<YtPlaylist> = emptyList()
    suspend fun liked(acct: YtAccount): List<YtVideo> = emptyList()
    suspend fun playlistVideos(acct: YtAccount, playlistId: String): List<YtVideo> = emptyList()
}

class YouTubeClient(
    http: OkHttpClient,
    private val accessToken: suspend (YtAccount) -> String,
    private val isVisible: suspend (String) -> Boolean
) : YouTubeSource {
    private val http = http.youtubeHttp()

    override suspend fun latest(acct: YtAccount): List<YtVideo> {
        if (!isVisible(acct.id)) return emptyList()
        val channelIds = mutableSetOf<String>()
        var page: String? = null
        do {
            val response = get(acct, "subscriptions", "part" to "snippet", "mine" to "true",
                "maxResults" to "50", "pageToken" to page)
            response.items().forEach { item ->
                item.obj("snippet")?.obj("resourceId")?.text("channelId")?.let { channelIds += it }
            }
            page = response.text("nextPageToken")
        } while (!page.isNullOrBlank() && isVisible(acct.id))
        val videos = mutableListOf<YtVideo>()
        var successfulChannels = 0
        var lastFailure: Exception? = null
        for (batch in channelIds.chunked(50)) {
            if (!isVisible(acct.id)) return emptyList()
            val channels = get(acct, "channels", "part" to "contentDetails", "id" to batch.joinToString(","))
            for (channel in channels.items()) {
                val uploads = channel.obj("contentDetails")?.obj("relatedPlaylists")?.text("uploads") ?: continue
                if (!isVisible(acct.id)) return emptyList()
                try {
                    videos += playlistItems(acct, uploads, 3)
                    successfulChannels++
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { lastFailure = failure }
            }
        }
        if (!isVisible(acct.id)) return emptyList()
        if (successfulChannels == 0) lastFailure?.let { throw it }
        return if (isVisible(acct.id)) videos.distinctBy { it.id }.sortedByDescending { it.publishedAt }.take(40)
            else emptyList()
    }

    override suspend fun playlists(acct: YtAccount): List<YtPlaylist> {
        if (!isVisible(acct.id)) return emptyList()
        val result = mutableListOf<YtPlaylist>()
        var page: String? = null
        do {
            val response = get(acct, "playlists", "part" to "snippet", "mine" to "true",
                "maxResults" to "50", "pageToken" to page)
            result += response.items().mapNotNull { item ->
                val id = item.text("id") ?: return@mapNotNull null
                YtPlaylist(id, item.obj("snippet")?.text("title") ?: "Playlist", acct.id)
            }
            page = response.text("nextPageToken")
        } while (!page.isNullOrBlank() && isVisible(acct.id))
        return if (isVisible(acct.id)) result else emptyList()
    }

    override suspend fun liked(acct: YtAccount): List<YtVideo> {
        if (!isVisible(acct.id)) return emptyList()
        val result = get(acct, "videos", "part" to "snippet", "myRating" to "like", "maxResults" to "40")
            .items().mapNotNull { video(it, acct.id, playlistItem = false) }
        return if (isVisible(acct.id)) result else emptyList()
    }

    override suspend fun playlistVideos(acct: YtAccount, playlistId: String): List<YtVideo> {
        if (!isVisible(acct.id)) return emptyList()
        val result = playlistItems(acct, playlistId, 40)
        return if (isVisible(acct.id)) result else emptyList()
    }

    /** youtube.readonly supplies channel identity, not the Google account's email. */
    suspend fun account(token: Token): YtAccount {
        val result = request("channels", token.accessToken, "part" to "snippet", "mine" to "true").items().firstOrNull()
            ?: throw IOException("This Google account needs a YouTube channel before it can be added.")
        return YtAccount(result.text("id") ?: throw IOException("Missing YouTube channel"), "",
            result.obj("snippet")?.text("title") ?: "YouTube account")
    }

    private suspend fun playlistItems(acct: YtAccount, id: String, limit: Int) = get(acct, "playlistItems",
        "part" to "snippet,contentDetails", "playlistId" to id, "maxResults" to limit.toString())
        .items().mapNotNull { video(it, acct.id, playlistItem = true) }.distinctBy { it.id }.take(limit)

    private fun video(item: JsonObject, accountId: String, playlistItem: Boolean): YtVideo? {
        val snippet = item.obj("snippet") ?: return null
        val details = item.obj("contentDetails")
        val id = if (playlistItem) details?.text("videoId") ?: snippet.obj("resourceId")?.text("videoId")
            else item.text("id")
        if (id.isNullOrBlank()) return null
        val title = snippet.text("title") ?: return null
        if (title in setOf("Private video", "Deleted video")) return null
        val published = if (playlistItem) details?.text("videoPublishedAt") ?: snippet.text("publishedAt")
            else snippet.text("publishedAt")
        val instant = try { Instant.parse(published) } catch (_: Exception) { return null }
        val thumbs = snippet.obj("thumbnails")
        val thumb = (thumbs?.obj("medium") ?: thumbs?.obj("default"))?.text("url").orEmpty()
        return YtVideo(id, title, snippet.text("videoOwnerChannelTitle") ?: snippet.text("channelTitle").orEmpty(),
            thumb, instant, accountId)
    }

    private suspend fun get(acct: YtAccount, path: String, vararg query: Pair<String, String?>): JsonObject =
        request(path, accessToken(acct), *query)

    private suspend fun request(path: String, token: String, vararg query: Pair<String, String?>): JsonObject {
        val url = "https://www.googleapis.com/youtube/v3/".toHttpUrl().newBuilder().addPathSegment(path)
        query.forEach { (key, value) -> if (value != null) url.addQueryParameter(key, value) }
        val (status, response) = http.youtubeRequest(Request.Builder().url(url.build())
            .header("Authorization", "Bearer $token").build())
        if (status !in 200..299 || response["error"] != null) {
            throw IOException(if (status == 401) "YouTube sign-in expired. Add the account again."
                else "YouTube unavailable (HTTP $status). Cached content is still available.")
        }
        return response
    }
}

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
internal fun JsonObject.items(): List<JsonObject> = this["items"]?.jsonArray?.map { it.jsonObject }.orEmpty()

/** Access tokens live only in memory and are shared across endpoint calls until nearly expired. */
internal class YouTubeTokens(private val store: AccountStore, private val auth: DeviceAuth) {
    private val mutex = Mutex()
    private data class CachedToken(val token: Token, val expiresAt: Long, val credentialVersion: String)
    private val tokens = mutableMapOf<String, CachedToken>()
    suspend fun access(acct: YtAccount): String = mutex.withLock {
        if (!store.isVisible(acct.id)) throw IOException("YouTube account is hidden")
        val version = store.credentialVersion(acct.id)
        val cached = tokens[acct.id]
        if (cached != null && cached.credentialVersion == version && cached.expiresAt > System.currentTimeMillis()) {
            return@withLock cached.token.accessToken
        }
        val token = auth.refresh(store.refreshToken(acct.id))
        if (token.refreshToken != null) store.rotateToken(acct, token)
        tokens[acct.id] = CachedToken(token,
            System.currentTimeMillis() + (token.expiresIn - 60).coerceAtLeast(0) * 1000,
            version)
        token.accessToken
    }
}
