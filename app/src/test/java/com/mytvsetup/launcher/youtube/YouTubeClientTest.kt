package com.mytvsetup.launcher.youtube

import com.mytvsetup.launcher.content.ContentTestServer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class YouTubeClientTest {
    private fun client(api: ContentTestServer) = YouTubeClient(api.http, { "access" }, { true })
    private val account = YtAccount("A", "", "My channel")

    @Test fun latestFollowsPaginatedSubscriptionsUploadsAndSortsNewest() = runBlocking {
        ContentTestServer().use { api ->
            api.enqueue("""{"items":[{"snippet":{"resourceId":{"channelId":"C1"}}}],"nextPageToken":"page2"}""")
            api.enqueue("""{"items":[{"snippet":{"resourceId":{"channelId":"C2"}}}]}""")
            api.enqueue("""{"items":[{"id":"C1","contentDetails":{"relatedPlaylists":{"uploads":"U1"}}},{"id":"C2","contentDetails":{"relatedPlaylists":{"uploads":"U2"}}}]}""")
            api.enqueue(videos("v1", "2026-10-01T00:00:00Z"))
            api.enqueue(videos("v2", "2026-11-01T00:00:00Z"))
            val result = client(api).latest(account)
            assertEquals(listOf("v2", "v1"), result.map { it.id })
            assertTrue(result.all { it.accountId == "A" && it.channel == "Creator" })
            assertEquals(listOf("/youtube/v3/subscriptions", "/youtube/v3/subscriptions", "/youtube/v3/channels",
                "/youtube/v3/playlistItems", "/youtube/v3/playlistItems"), api.urls.map { it.encodedPath })
            assertEquals("page2", api.urls[1].queryParameter("pageToken"))
            assertEquals("C1,C2", api.urls[2].queryParameter("id"))
            assertEquals("3", api.urls[3].queryParameter("maxResults"))
            assertEquals("U2", api.urls[4].queryParameter("playlistId"))
            assertTrue(api.urls.all { it.host == "www.googleapis.com" })
            repeat(5) { assertEquals("Bearer access", api.request().headers["Authorization"]) }
        }
    }

    @Test fun hiddenAccountsReturnNoNamesPlaylistsOrLikedVideosWithoutRequests() = runBlocking {
        ContentTestServer().use { api ->
            val hidden = YouTubeClient(api.http, { throw AssertionError("Read hidden token") }, { false })
            assertTrue(hidden.latest(account).isEmpty())
            assertTrue(hidden.playlists(account).isEmpty())
            assertTrue(hidden.liked(account).isEmpty())
            assertTrue(hidden.playlistVideos(account, "p").isEmpty())
            assertEquals(0, api.server.requestCount)
        }
    }

    @Test fun playlistsAndLikedUseMineAndMyRatingWithoutSearchQuota() = runBlocking {
        ContentTestServer().use { api ->
            api.enqueue("""{"items":[{"id":"p","snippet":{"title":"My playlist"}}]}""")
            api.enqueue("""{"items":[{"id":"liked","snippet":{"title":"Liked video","channelTitle":"Creator","publishedAt":"2026-11-01T00:00:00Z"}}]}""")
            val client = client(api)
            assertEquals(listOf(YtPlaylist("p", "My playlist", "A")), client.playlists(account))
            assertEquals("liked", client.liked(account).single().id)
            assertEquals("true", api.urls[0].queryParameter("mine"))
            assertEquals("like", api.urls[1].queryParameter("myRating"))
        }
    }

    @Test fun hiddenDuringRequestSuppressesReturnedContent() = runBlocking {
        ContentTestServer().use { api ->
            api.enqueue("""{"items":[{"id":"p","snippet":{"title":"Private name"}}]}""")
            var checks = 0
            val client = YouTubeClient(api.http, { "access" }, { ++checks == 1 })
            assertTrue(client.playlists(account).isEmpty())
        }
    }

    @Test fun deletedOrForbiddenChannelKeepsOtherChannelsUploads() = runBlocking {
        for (status in listOf(404, 403)) {
            ContentTestServer().use { api ->
                enqueueTwoChannels(api)
                api.enqueue("""{"error":{"code":$status}}""", status)
                api.enqueue(videos("healthy", "2026-11-01T00:00:00Z"))
                assertEquals(listOf("healthy"), client(api).latest(account).map { it.id })
                assertEquals("U2", api.urls.last().queryParameter("playlistId"))
            }
        }
    }

    @Test fun allChannelFailuresStillFailTheRefresh() = runBlocking {
        ContentTestServer().use { api ->
            enqueueTwoChannels(api)
            api.enqueue("""{"error":{"code":404}}""", 404)
            api.enqueue("""{"error":{"code":403}}""", 403)
            try {
                client(api).latest(account)
                fail("All channels failed")
            } catch (_: IOException) { }
            assertEquals(4, api.server.requestCount)
        }
    }

    @Test fun successfulEmptyChannelIsNotTreatedAsTotalFailure() = runBlocking {
        ContentTestServer().use { api ->
            enqueueTwoChannels(api)
            api.enqueue("""{"items":[]}""")
            api.enqueue("""{"error":{"code":404}}""", 404)
            assertTrue(client(api).latest(account).isEmpty())
        }
    }

    @Test fun channelFetchCancellationIsRethrown() = runBlocking {
        ContentTestServer().use { api ->
            enqueueTwoChannels(api)
            var requests = 0
            val cancelled = CancellationException("stop")
            val client = YouTubeClient(api.http, {
                if (++requests == 3) throw cancelled
                "access"
            }, { true })
            try {
                client.latest(account)
                fail("Cancellation must stop the refresh")
            } catch (failure: CancellationException) { assertSame(cancelled, failure) }
            assertEquals(2, api.server.requestCount)
        }
    }

    private fun enqueueTwoChannels(api: ContentTestServer) {
        api.enqueue("""{"items":[{"snippet":{"resourceId":{"channelId":"C1"}}},{"snippet":{"resourceId":{"channelId":"C2"}}}]}""")
        api.enqueue("""{"items":[{"id":"C1","contentDetails":{"relatedPlaylists":{"uploads":"U1"}}},{"id":"C2","contentDetails":{"relatedPlaylists":{"uploads":"U2"}}}]}""")
    }

    private fun videos(id: String, published: String) = """{"items":[{"snippet":{"title":"Video $id","videoOwnerChannelTitle":"Creator","resourceId":{"videoId":"$id"},"publishedAt":"2026-11-01T00:00:00Z","thumbnails":{"medium":{"url":"https://i.ytimg.com/$id"}}},"contentDetails":{"videoId":"$id","videoPublishedAt":"$published"}}]}"""
}
