package com.mytvsetup.launcher.content

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class TmdbClientTest {
    private val movie = Title(
        "tm884323", Kind.MOVIE, "Pushpa: The Rise", 2021, 7.6, listOf("act", "smugglers"),
        "synopsis", listOf("Allu Arjun"), "poster", "backdrop", listOf(Offer("prv", listOf("hi"), "prime")), "690957"
    )
    private fun enqueueMovie(api: ContentTestServer) {
        api.enqueue(fixture("tmdb_movie.json"))
        api.enqueue(Json.parseToJsonElement(fixture("tmdb_movie.json")).jsonObject.getValue("keywords").toString())
    }

    @Test fun movieDetailsSetOriginalLanguageAndKeywordEndpointExtendsLabels() = runBlocking {
        ContentTestServer().use { api ->
            enqueueMovie(api)
            val enriched = TmdbClient(api.http, "test-key").enrich(movie)
            assertEquals(movie.copy(originalLanguage = "te", labels = listOf(
                "act", "smugglers", "rags to riches", "sandalwood", "egotistical", "authoritarian"
            )), enriched)
            assertEquals(listOf("/3/movie/690957", "/3/movie/690957/keywords"), api.urls.map { it.encodedPath })
            assertTrue(api.urls.all { it.host == "api.themoviedb.org" && it.scheme == "https" })
            assertTrue(api.urls.all { it.queryParameter("api_key") == "test-key" })
        }
    }

    @Test fun tvKeywordsReadResultsAndHiOriginalAllowsAnEmptyAudioOffer() = runBlocking {
        ContentTestServer().use { api ->
            api.enqueue("""{"id":123,"original_language":"hi"}""")
            api.enqueue("""{"id":123,"results":[{"id":1,"name":"family"}]}""")
            val t = movie.copy(kind = Kind.SHOW, tmdbId = "123", offers = listOf(Offer("prv", emptyList(), "prime")))
            val enriched = TmdbClient(api.http, "test-key").enrich(t)
            assertEquals("hi", enriched.originalLanguage)
            assertEquals(listOf("act", "smugglers", "family"), enriched.labels)
            assertTrue(isAllowed(enriched, setOf("hi", "en"), listOf("prv")))
            assertEquals(listOf("/3/tv/123", "/3/tv/123/keywords"), api.urls.map { it.encodedPath })
        }
    }

    @Test fun keywordHttpFailureKeepsDetailsOriginalLanguageAndExistingLabels() = runBlocking {
        ContentTestServer().use { api ->
            api.enqueue("""{"original_language":"hi"}""")
            api.enqueue("{}", 503)
            val title = movie.copy(offers = listOf(Offer("prv", emptyList(), "prime")))
            val enriched = TmdbClient(api.http, "test-key").enrich(title)
            assertEquals(title.copy(originalLanguage = "hi"), enriched)
            assertTrue(isAllowed(enriched, setOf("hi", "en"), listOf("prv")))
            assertEquals(2, api.server.requestCount)
        }
    }

    @Test fun keywordIOExceptionKeepsDetailsAfterOneRetry() = runBlocking {
        ContentTestServer().use { api ->
            api.enqueue("""{"original_language":"en"}""")
            var keywordAttempts = 0
            val http = api.http.newBuilder().addInterceptor { chain ->
                if (chain.request().url.encodedPath.endsWith("/keywords")) {
                    keywordAttempts++
                    throw IOException("keywords offline")
                }
                chain.proceed(chain.request())
            }.build()
            assertEquals(movie.copy(originalLanguage = "en"), TmdbClient(http, "test-key").enrich(movie))
            assertEquals(2, keywordAttempts)
        }
    }

    @Test fun malformedKeywordsKeepDetails() = runBlocking {
        ContentTestServer().use { api ->
            api.enqueue("""{"original_language":"en"}""")
            api.enqueue("invalid JSON")
            assertEquals(movie.copy(originalLanguage = "en"), TmdbClient(api.http, "test-key").enrich(movie))
        }
    }

    @Test fun blankKeyMissingTmdbIdAndUnsupportedKindsMakeNoRequest() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor { throw AssertionError("Unexpected network request") }.build()
        assertSame(movie, TmdbClient(http, "   ").enrich(movie))
        val noId = movie.copy(tmdbId = null)
        assertSame(noId, TmdbClient(http, "test-key").enrich(noId))
        for (kind in listOf(Kind.MUSIC, Kind.YOUTUBE)) {
            val t = movie.copy(kind = kind)
            assertSame(t, TmdbClient(http, "test-key").enrich(t))
        }
    }

    @Test fun retriesOneIOExceptionThenEnriches() = runBlocking {
        ContentTestServer().use { api ->
            enqueueMovie(api)
            var attempts = 0
            val http = api.http.newBuilder().addInterceptor { chain ->
                attempts++
                if (attempts == 1) throw IOException("transient TLS failure")
                chain.proceed(chain.request())
            }.build()
            assertEquals("te", TmdbClient(http, "test-key").enrich(movie).originalLanguage)
            assertEquals(3, attempts) // two details attempts, one keyword request
        }
    }

    @Test fun repeatedIOExceptionStopsAfterOneRetry() {
        var attempts = 0
        val http = OkHttpClient.Builder().addInterceptor {
            attempts++
            throw IOException("offline")
        }.build()
        assertThrows(IOException::class.java) { runBlocking { TmdbClient(http, "test-key").enrich(movie) } }
        assertEquals(2, attempts)
    }

    @Test fun httpErrorsAreSurfacedWithoutRetryOrCredentialInMessage() {
        ContentTestServer().use { api ->
            api.enqueue("{}", 401)
            val error = assertThrows(IOException::class.java) {
                runBlocking { TmdbClient(api.http, "private-test-key").enrich(movie) }
            }
            assertFalse(error.message.orEmpty().contains("private-test-key"))
            assertEquals(1, api.server.requestCount)
        }
    }
}
