package com.mytvsetup.launcher.content

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class JustWatchParseTest {
    @Test fun livePushpaFixtureHasHindiPrimeLinkAndExpandedImages() = runBlocking {
        ContentTestServer().use { api ->
            api.enqueue(jwResponse("pushpa"))
            val t = JustWatchClient(api.http).search("Pushpa").first { it.title == "Pushpa: The Rise" }
            assertEquals("tm884323", t.id)
            assertEquals(Kind.MOVIE, t.kind)
            assertEquals(2021, t.year)
            assertEquals("690957", t.tmdbId)
            val prime = t.offers.single { it.service == "prv" }
            assertEquals(listOf("hi"), prime.audio)
            assertTrue(prime.url.startsWith("https://app.primevideo.com/detail?gti="))
            assertEquals("https://images.justwatch.com/poster/346785875/s332/pushpa.webp", t.posterUrl)
            val backdrop = checkNotNull(t.backdropUrl)
            assertTrue(backdrop.startsWith("https://images.justwatch.com/backdrop/"))
            assertTrue(backdrop.contains("/s1440/"))
            assertTrue(backdrop.endsWith(".webp"))
            assertFalse(t.synopsis.isBlank())
            assertFalse(t.cast.isEmpty())
            assertFalse(t.labels.isEmpty())
            assertTrue(t.imdb!! > 0)
            assertTrue(t.runtimeMin!! > 0)
        }
    }

    @Test fun livePopularPageDeduplicatesAndRetainsOnlySubscribedFlatrateOffers() = runBlocking {
        ContentTestServer().use { api ->
            api.enqueue(jwResponse())
            val (titles, cursor) = JustWatchClient(api.http).page(Kind.SHOW, null, null)
            assertEquals(40, titles.size)
            assertEquals("NDA=", cursor)
            titles.forEach { t ->
                assertEquals(t.offers.map { it.service }.distinct().size, t.offers.size)
                assertTrue(t.offers.all { it.service in setOf("prv", "jhs", "snl", "zee", "atp") })
            }
            val foreignAudio = titles.first { it.title == "The Traitors (Telugu)" }
            assertEquals(listOf("te"), foreignAudio.offers.single().audio)
            assertFalse(isAllowed(foreignAudio, setOf("hi", "en"), listOf("prv")))
            // Broken But Beautiful has Hindi ADS offers, but the only subscribed FLATRATE is Tamil.
            val broken = titles.first { it.title == "Broken But Beautiful" }
            assertEquals(listOf(Offer("zee", listOf("ta"), broken.offers.single().url)), broken.offers)
        }
    }

    @Test fun unknownAndUnsupportedObjectTypesAreSkippedWithoutLosingPageOrCursor() = runBlocking {
        ContentTestServer().use { api ->
            api.enqueue("""{"data":{"popularTitles":{"edges":[
              {"node":{"id":"unknown","objectType":"SEASON","content":{"title":"Unknown"}}},
              {"node":{"id":"movie","objectType":"MOVIE","content":{"title":"Movie"}}},
              {"node":{"id":"music","objectType":"MUSIC","content":{"title":"Music"}}},
              {"node":{"id":"show","objectType":"SHOW","content":{"title":"Show"}}}
            ],"pageInfo":{"endCursor":"next-page","hasNextPage":true}}}}""")
            val (titles, cursor) = JustWatchClient(api.http).page(Kind.MOVIE, null, null)
            assertEquals(listOf("movie", "show"), titles.map { it.id })
            assertEquals(listOf(Kind.MOVIE, Kind.SHOW), titles.map { it.kind })
            assertEquals("next-page", cursor)
        }
    }

    @Test fun liveOppenheimerFixturePicksHotstar() = runBlocking {
        ContentTestServer().use { api ->
            api.enqueue(jwResponse("oppenheimer"))
            val t = JustWatchClient(api.http).search("Oppenheimer").first { it.title == "Oppenheimer" }
            val offer = bestOffer(t, setOf("hi", "en"), listOf("jhs", "prv"))!!
            assertEquals("jhs", offer.service)
            assertTrue(offer.audio.containsAll(listOf("hi", "en")))
        }
    }

    @Test fun pageSendsMovieFilterYearCursorAndVerifiedFieldsToGraphql() = runBlocking {
        ContentTestServer().use { api ->
            api.enqueue("""{"data":{"popularTitles":{"edges":[],"pageInfo":{"endCursor":"end","hasNextPage":false}}}}""")
            val result = JustWatchClient(api.http).page(Kind.MOVIE, "NDA=", 2020)
            assertTrue(result.first.isEmpty())
            assertNull(result.second)
            assertEquals("https://apis.justwatch.com/graphql", api.urls.single().toString())
            val request = api.request()
            assertEquals("POST", request.method)
            val body = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
            val query = body.getValue("query").jsonPrimitive.content.replace(Regex("\\s+"), "")
            assertTrue(query.contains("first:40"))
            assertTrue(query.contains("objectTypes:[MOVIE]"))
            assertTrue(query.contains("releaseYear:{min:2020}"))
            assertTrue(query.contains("packages:[\"prv\",\"jhs\",\"snl\",\"zee\",\"atp\"]"))
            assertTrue(query.contains("monetizationTypes:[FLATRATE]"))
            assertTrue(query.contains("content(country:IN,language:en)"))
            assertTrue(query.contains("credits(role:ACTOR){name}"))
            assertTrue(query.contains("offers(country:IN,platform:WEB)"))
            assertEquals("NDA=", body.getValue("variables").jsonObject.getValue("cursor").jsonPrimitive.content)
        }
    }

    @Test fun searchUsesVariablesForQuotedUserInput() = runBlocking {
        ContentTestServer().use { api ->
            api.enqueue(jwResponse("pushpa"))
            val input = "Pushpa \" hi \\ end"
            JustWatchClient(api.http).search(input)
            val body = Json.parseToJsonElement(api.request().body!!.utf8()).jsonObject
            assertEquals(input, body.getValue("variables").jsonObject.getValue("search").jsonPrimitive.content)
            assertFalse(body.getValue("query").jsonPrimitive.content.contains(input))
        }
    }

    @Test fun graphQlErrorsAreNotTreatedAsAnEmptySuccessfulPage() = runBlocking {
        ContentTestServer().use { api ->
            api.enqueue("""{"data":null,"errors":[{"message":"unavailable"}]}""")
            try {
                JustWatchClient(api.http).page(Kind.MOVIE, null, null)
                fail("Expected IOException")
            } catch (_: IOException) { }
        }
    }
}
