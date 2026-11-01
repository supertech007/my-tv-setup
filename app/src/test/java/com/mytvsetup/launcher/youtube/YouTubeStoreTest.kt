package com.mytvsetup.launcher.youtube

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import javax.crypto.KeyGenerator

class YouTubeStoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun tokensAreEncryptedAndVisibilitySurvivesReopening() = runBlocking {
        val file = folder.newFolder().resolve("youtube.preferences_pb")
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        suspend fun open(block: suspend (AccountStore) -> Unit) {
            val job = SupervisorJob()
            val scope = CoroutineScope(job + Dispatchers.IO)
            try {
                val data = PreferenceDataStoreFactory.create(scope = scope) { file }
                val store = AccountStore(data, TokenCipher { key })
                block(store)
                assertFalse(data.data.first().asMap().values.joinToString().contains("very-private-refresh"))
            } finally { scope.cancel(); job.join() }
        }
        open {
            it.add(YtAccount("A", "", "Channel A"), Token("access", "very-private-refresh", 3600))
            it.setHidden("A", true)
        }
        open {
            assertEquals("very-private-refresh", it.refreshToken("A"))
            assertEquals(setOf("A"), it.state.first().hidden)
            assertEquals("Channel A", it.state.first().accounts.single().channelTitle)
            it.setHidden("A", false)
            assertTrue(it.state.first().hidden.isEmpty())
        }
    }

    @Test fun cipherUsesRandomIvAndAuthenticatesAccountIdentity() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val cipher = TokenCipher { key }
        val sealed = cipher.encrypt("A", "secret")
        assertNotEquals(sealed, cipher.encrypt("A", "secret"))
        assertEquals("secret", cipher.decrypt("A", sealed))
        assertThrows(Exception::class.java) { cipher.decrypt("B", sealed) }
        val changed = sealed.dropLast(4) + "AAAA"
        assertThrows(Exception::class.java) { cipher.decrypt("A", changed) }
    }

    @Test fun feedCachesThrottlesAndFiltersAccountsImmediately() = runBlocking {
        withStore { store ->
            for (id in listOf("A", "B")) store.add(YtAccount(id, "", id), Token("a", "r", 3600))
            var now = 1000L
            val calls = mutableListOf<String>()
            val source = object : YouTubeSource {
                override suspend fun latest(acct: YtAccount): List<YtVideo> {
                    calls += acct.id
                    return listOf(YtVideo(acct.id, "Video", acct.id, "", Instant.EPOCH, acct.id))
                }
            }
            val feed = YouTubeFeed(store, source) { now }
            feed.onHomeShown()
            assertEquals(listOf("A", "B"), feed.videos.first().map { it.id })
            // A second feed represents a fresh UI/process consumer using the persistent cache.
            val reopened = YouTubeFeed(store, source) { now }
            reopened.onHomeShown()
            assertEquals(listOf("A", "B"), calls)
            store.setHidden("B", true)
            assertEquals(listOf("A"), reopened.videos.first().map { it.id })
            store.setHidden("B", false)
            assertEquals(listOf("A", "B"), reopened.videos.first().map { it.id })
            now += 7_199_999L
            reopened.onHomeShown()
            assertEquals(2, calls.size)
            now += 1
            reopened.onHomeShown()
            assertEquals(listOf("A", "B", "A", "B"), calls)
        }
    }

    @Test fun failedRefreshKeepsCachedVideosAndDoesNotHammerQuota() = runBlocking {
        withStore { store ->
            store.add(YtAccount("A", "", "A"), Token("a", "r", 3600))
            var calls = 0
            var now = 10L
            val source = object : YouTubeSource {
                override suspend fun latest(acct: YtAccount): List<YtVideo> {
                    calls++
                    if (calls == 2) throw java.io.IOException("offline")
                    return listOf(YtVideo("v", "Cached", "A", "", Instant.EPOCH, "A"))
                }
            }
            val feed = YouTubeFeed(store, source) { now }
            feed.onHomeShown()
            now += 7_200_000
            feed.onHomeShown()
            feed.onHomeShown()
            assertEquals(2, calls)
            assertEquals("Cached", feed.videos.first().single().title)
            assertNotNull(feed.message.value)
            val reopened = YouTubeFeed(store, source) { now }
            now += 899_999L
            reopened.onHomeShown()
            assertEquals(2, calls)
            now += 1
            reopened.onHomeShown()
            assertEquals(3, calls)
            assertEquals("Cached", reopened.videos.first().single().title)
            assertNull(reopened.message.value)
            now += 7_199_999L
            reopened.onHomeShown()
            assertEquals(3, calls)
            now += 1
            reopened.onHomeShown()
            assertEquals(4, calls)
        }
    }

    @Test fun offlineFirstAttemptDoesNotThrottleAnEmptyCache() = runBlocking {
        withStore { store ->
            store.add(YtAccount("A", "", "A"), Token("a", "r", 3600))
            var calls = 0
            val source = object : YouTubeSource {
                override suspend fun latest(acct: YtAccount): List<YtVideo> {
                    if (++calls == 1) throw java.io.IOException("offline")
                    return listOf(YtVideo("v", "Recovered", "A", "", Instant.EPOCH, "A"))
                }
            }
            val first = YouTubeFeed(store, source) { 1000L }
            first.onHomeShown()
            assertTrue(first.videos.first().isEmpty())
            assertNotNull(first.message.value)
            val reopened = YouTubeFeed(store, source) { 1000L }
            reopened.onHomeShown()
            assertEquals("Recovered", reopened.videos.first().single().title)
            assertNull(reopened.message.value)
            reopened.onHomeShown()
            assertEquals(2, calls)
        }
    }

    @Test fun successfulEmptyCacheCanRefreshOnNextHomeVisit() = runBlocking {
        withStore { store ->
            store.add(YtAccount("A", "", "A"), Token("a", "r", 3600))
            var calls = 0
            val source = object : YouTubeSource {
                override suspend fun latest(acct: YtAccount): List<YtVideo> {
                    if (++calls == 1) return emptyList()
                    return listOf(YtVideo("v", "New upload", "A", "", Instant.EPOCH, "A"))
                }
            }
            val feed = YouTubeFeed(store, source) { 1000L }
            feed.onHomeShown()
            feed.onHomeShown()
            assertEquals("New upload", feed.videos.first().single().title)
        }
    }

    @Test fun concurrentConsumersDoNotDuplicateAnEmptyCacheRefresh() = runBlocking {
        withStore { store -> coroutineScope {
            store.add(YtAccount("A", "", "A"), Token("a", "r", 3600))
            val started = CompletableDeferred<Unit>()
            val complete = CompletableDeferred<Unit>()
            var calls = 0
            val source = object : YouTubeSource {
                override suspend fun latest(acct: YtAccount): List<YtVideo> {
                    calls++
                    started.complete(Unit)
                    complete.await()
                    return listOf(YtVideo("v", "Loaded", "A", "", Instant.EPOCH, "A"))
                }
            }
            val first = launch { YouTubeFeed(store, source) { 1000L }.onHomeShown() }
            started.await()
            try {
                withTimeout(1000L) { YouTubeFeed(store, source) { 1000L }.onHomeShown() }
                assertEquals(1, calls)
            } finally { complete.complete(Unit) }
            first.join()
        } }
    }

    @Test fun cancelledEmptyCacheRefreshReleasesItsReservation() = runBlocking {
        withStore { store -> coroutineScope {
            store.add(YtAccount("A", "", "A"), Token("a", "r", 3600))
            val started = CompletableDeferred<Unit>()
            val wait = CompletableDeferred<Unit>()
            var calls = 0
            val source = object : YouTubeSource {
                override suspend fun latest(acct: YtAccount): List<YtVideo> {
                    if (++calls == 1) {
                        started.complete(Unit)
                        wait.await()
                    }
                    return listOf(YtVideo("v", "Recovered", "A", "", Instant.EPOCH, "A"))
                }
            }
            val feed = YouTubeFeed(store, source) { 1000L }
            val first = launch { feed.onHomeShown() }
            started.await()
            first.cancelAndJoin()
            feed.onHomeShown()
            assertEquals("Recovered", feed.videos.first().single().title)
            assertEquals(2, calls)
        } }
    }

    @Test fun failedReservationWriteDoesNotBlockTheNextRefresh() = runBlocking {
        var rejectWrite = false
        withStore(wrap = { data -> object : DataStore<Preferences> by data {
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                data.updateData { old ->
                    val updated = transform(old)
                    if (rejectWrite) {
                        rejectWrite = false
                        throw java.io.IOException("disk unavailable")
                    }
                    updated
                }
        } }) { store ->
            store.add(YtAccount("A", "", "A"), Token("a", "r", 3600))
            val source = object : YouTubeSource {
                override suspend fun latest(acct: YtAccount) =
                    listOf(YtVideo("v", "Recovered", "A", "", Instant.EPOCH, "A"))
            }
            val feed = YouTubeFeed(store, source) { 1000L }
            rejectWrite = true
            assertThrows(java.io.IOException::class.java) { runBlocking { feed.onHomeShown() } }
            feed.onHomeShown()
            assertEquals("Recovered", feed.videos.first().single().title)
        }
    }

    @Test fun missingKeysDoNotConsumeRefreshWindow() = runBlocking {
        withStore { store ->
            store.add(YtAccount("A", "", "A"), Token("a", "r", 3600))
            var configured = false
            var calls = 0
            val source = object : YouTubeSource {
                override suspend fun latest(acct: YtAccount): List<YtVideo> {
                    calls++
                    return emptyList()
                }
            }
            val feed = YouTubeFeed(store, source, enabled = { configured }) { 1000L }
            feed.onHomeShown()
            assertEquals(0, calls)
            configured = true
            feed.onHomeShown()
            assertEquals(1, calls)
        }
    }

    @Test fun hidingRemovesCachedPlaylistsLikedAndPlaylistVideosAndUnhidingRestores() = runBlocking {
        withStore { store ->
            val account = YtAccount("B", "", "Private account")
            val playlist = YtPlaylist("p", "Private playlist", "B")
            val video = YtVideo("v", "Private liked video", "Creator", "", Instant.EPOCH, "B")
            store.add(account, Token("a", "r", 3600))
            val source = object : YouTubeSource {
                override suspend fun latest(acct: YtAccount) = listOf(video)
                override suspend fun liked(acct: YtAccount) = listOf(video)
                override suspend fun playlists(acct: YtAccount) = listOf(playlist)
                override suspend fun playlistVideos(acct: YtAccount, playlistId: String) = listOf(video)
            }
            val feed = YouTubeFeed(store, source) { 1000L }
            feed.refreshLibrary()
            feed.openPlaylist(playlist)
            assertEquals(listOf(playlist), feed.playlists.first())
            assertEquals(listOf(video), feed.liked.first())
            assertEquals(listOf(video), feed.playlistVideos(playlist).first())
            store.setHidden("B", true)
            assertTrue(feed.playlists.first().isEmpty())
            assertTrue(feed.liked.first().isEmpty())
            assertTrue(feed.playlistVideos(playlist).first().isEmpty())
            assertTrue(store.state.first().visibleAccounts.isEmpty())
            store.setHidden("B", false)
            assertEquals(listOf(playlist), feed.playlists.first())
            assertEquals(listOf(video), feed.liked.first())
            assertEquals(listOf(video), feed.playlistVideos(playlist).first())
        }
    }

    @Test fun readdingAccountAllowsImmediateRetryAndInvalidatesOldAccessToken() = runBlocking {
        withStore { store ->
            val account = YtAccount("A", "", "A")
            store.add(account, Token("a", "old-refresh", 3600))
            var calls = 0
            val source = object : YouTubeSource {
                override suspend fun latest(acct: YtAccount): List<YtVideo> { calls++; return emptyList() }
            }
            val feed = YouTubeFeed(store, source) { 1000L }
            feed.onHomeShown()
            com.mytvsetup.launcher.content.ContentTestServer().use { api ->
                api.enqueue("""{"access_token":"old-access","expires_in":3600,"token_type":"Bearer"}""")
                api.enqueue("""{"access_token":"new-access","expires_in":3600,"token_type":"Bearer"}""")
                val tokens = YouTubeTokens(store, DeviceAuth(api.http, "client", "secret"))
                assertEquals("old-access", tokens.access(account))
                assertEquals("old-access", tokens.access(account))
                store.add(account, Token("a", "new-refresh", 3600))
                assertEquals("new-access", tokens.access(account))
                assertEquals(2, api.server.requestCount)
                api.request()
                assertTrue(api.request().body!!.utf8().contains("refresh_token=new-refresh"))
            }
            feed.onHomeShown()
            assertEquals(2, calls)
        }
    }

    @Test fun reauthorizationDuringRefreshDoesNotCacheOldAccessUnderNewCredentials() = runBlocking {
        withStore { store ->
            val account = YtAccount("A", "", "A")
            store.add(account, Token("a", "old-refresh", 3600))
            com.mytvsetup.launcher.content.ContentTestServer().use { api ->
                api.enqueue("""{"access_token":"old-access","expires_in":3600,"token_type":"Bearer"}""")
                api.enqueue("""{"access_token":"new-access","expires_in":3600,"token_type":"Bearer"}""")
                var replaced = false
                val http = api.http.newBuilder().addInterceptor { chain ->
                    val response = chain.proceed(chain.request())
                    if (!replaced) {
                        replaced = true
                        runBlocking { store.add(account, Token("a", "new-refresh", 3600)) }
                    }
                    response
                }.build()
                val tokens = YouTubeTokens(store, DeviceAuth(http, "client", "secret"))
                assertEquals("old-access", tokens.access(account))
                assertEquals("new-access", tokens.access(account))
                assertEquals("new-access", tokens.access(account))
                assertEquals(2, api.server.requestCount)
                assertTrue(api.request().body!!.utf8().contains("refresh_token=old-refresh"))
                assertTrue(api.request().body!!.utf8().contains("refresh_token=new-refresh"))
            }
        }
    }

    private suspend fun withStore(
        wrap: (DataStore<Preferences>) -> DataStore<Preferences> = { it },
        block: suspend (AccountStore) -> Unit
    ) {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        val file = folder.newFolder().resolve("youtube.preferences_pb")
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        try { block(AccountStore(wrap(PreferenceDataStoreFactory.create(scope = scope) { file }), TokenCipher { key })) }
        finally { scope.cancel(); job.join() }
    }
}
