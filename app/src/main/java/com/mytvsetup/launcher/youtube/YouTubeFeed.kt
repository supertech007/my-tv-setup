package com.mytvsetup.launcher.youtube

import android.content.Context
import com.mytvsetup.launcher.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient

/** Call onHomeShown whenever Home becomes visible. Cached data emits without waiting for network. */
class YouTubeFeed(
    private val store: AccountStore,
    private val source: YouTubeSource,
    private val enabled: () -> Boolean = { true },
    private val now: () -> Long = System::currentTimeMillis
) {
    private val error = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = error.asStateFlow()
    val videos: Flow<List<YtVideo>> = videoFlow("latest")
    val liked: Flow<List<YtVideo>> = videoFlow("liked")
    val playlists: Flow<List<YtPlaylist>> = combine(store.caches, store.state) { caches, state ->
        state.visibleAccounts.flatMap { caches["playlists:${it.id}"]?.playlists.orEmpty() }
            .filter { playlist -> state.visibleAccounts.any { it.id == playlist.accountId } }
    }

    suspend fun onHomeShown() {
        if (!ready()) return
        for (acct in store.state.first().visibleAccounts) {
            refresh("latest:${acct.id}", acct) { FeedCache(videos = source.latest(acct).take(40)) }
        }
    }

    suspend fun refreshLibrary() {
        if (!ready()) return
        for (acct in store.state.first().visibleAccounts) {
            refresh("playlists:${acct.id}", acct) { FeedCache(playlists = source.playlists(acct)) }
            refresh("liked:${acct.id}", acct) { FeedCache(videos = source.liked(acct).take(40)) }
        }
    }

    fun playlistVideos(playlist: YtPlaylist): Flow<List<YtVideo>> =
        combine(store.caches, store.state) { caches, state ->
            if (state.visibleAccounts.none { it.id == playlist.accountId }) emptyList()
            else visibleVideos(caches[playlistKey(playlist)]?.videos.orEmpty(), state.hidden).distinctBy { it.id }
        }

    suspend fun openPlaylist(playlist: YtPlaylist) {
        if (!ready()) return
        val acct = store.state.first().visibleAccounts.firstOrNull { it.id == playlist.accountId } ?: return
        refresh(playlistKey(playlist), acct) { FeedCache(videos = source.playlistVideos(acct, playlist.id).take(40)) }
    }

    private fun videoFlow(kind: String): Flow<List<YtVideo>> = combine(store.caches, store.state) { caches, state ->
        visibleVideos(state.visibleAccounts.flatMap { caches["$kind:${it.id}"]?.videos.orEmpty() }, state.hidden)
            .filter { video -> state.visibleAccounts.any { it.id == video.accountId } }
            .distinctBy { it.id }.sortedByDescending { it.publishedAt }.take(40)
    }

    private fun ready(): Boolean {
        if (enabled()) return true
        error.value = "Add the YouTube key in secrets.properties"
        return false
    }

    private fun playlistKey(playlist: YtPlaylist) = "playlist:${playlist.accountId}:${playlist.id}"
    private suspend fun refresh(key: String, acct: YtAccount, load: suspend () -> FeedCache) {
        if (!store.claim(key, acct.id, now())) return
        try {
            store.saveCache(key, load())
            error.value = null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error.value = "YouTube could not refresh. Showing cached videos; check your connection or add the account again." }
        finally { store.release(key) }
    }
}

/** One shared store/client/feed for standalone screens and the home card after merge. */
class YouTubeRuntime private constructor(context: Context) {
    val store = AccountStore(context)
    private val http = OkHttpClient()
    val auth = DeviceAuth(http, BuildConfig.YT_CLIENT_ID, BuildConfig.YT_CLIENT_SECRET)
    private val tokens = YouTubeTokens(store, auth)
    val client = YouTubeClient(http, tokens::access, store::isVisible)
    val feed = YouTubeFeed(store, client, enabled = { auth.configured })

    companion object {
        @Volatile private var instance: YouTubeRuntime? = null
        fun get(context: Context): YouTubeRuntime = instance ?: synchronized(this) {
            instance ?: YouTubeRuntime(context.applicationContext).also { instance = it }
        }
    }
}
