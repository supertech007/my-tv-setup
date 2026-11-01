package com.mytvsetup.launcher.content

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class JustWatchClient(http: OkHttpClient) {
    private val http = http.contentClient()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun page(kind: Kind, cursor: String?, yearMin: Int?): Pair<List<Title>, String?> {
        require(kind == Kind.MOVIE || kind == Kind.SHOW) { "JustWatch supports movies and shows" }
        return fetch(kind, cursor, yearMin, null)
    }

    suspend fun search(query: String): List<Title> = fetch(null, null, null, query).first

    private suspend fun fetch(kind: Kind?, cursor: String?, yearMin: Int?, search: String?): Pair<List<Title>, String?> {
        val filters = buildList {
            add("packages:[\"prv\",\"jhs\",\"snl\",\"zee\",\"atp\"]")
            add("monetizationTypes:[FLATRATE]")
            if (kind != null) add("objectTypes:[${kind.name}]")
            if (yearMin != null) add("releaseYear:{min:$yearMin}")
            add("searchQuery:\$search")
        }.joinToString(",")
        val query = """
            query(${'$'}cursor:String,${'$'}search:String) {
              popularTitles(country:IN,first:40,after:${'$'}cursor,filter:{$filters}) {
                edges { node { id objectType
                  content(country:IN,language:en){title shortDescription originalReleaseYear posterUrl
                    backdrops{backdropUrl} scoring{imdbScore} genres{shortName}
                    credits(role:ACTOR){name} runtime externalIds{tmdbId}}
                  offers(country:IN,platform:WEB){monetizationType package{shortName} audioLanguages standardWebURL}
                } }
                pageInfo { endCursor hasNextPage }
              }
            }
        """.trimIndent()
        val body = buildJsonObject {
            put("query", query)
            put("variables", buildJsonObject {
                put("cursor", cursor?.let(::JsonPrimitive) ?: JsonNull)
                put("search", search?.let(::JsonPrimitive) ?: JsonNull)
            })
        }.toString()
        val request = Request.Builder().url("https://apis.justwatch.com/graphql")
            .post(body.toRequestBody("application/json".toMediaType())).build()
        val response = json.decodeFromString<JwResponse>(requestText(http, request))
        if (response.errors.isNotEmpty()) throw IOException("JustWatch GraphQL errors")
        val page = response.data?.popularTitles ?: throw IOException("Missing JustWatch page")
        return page.edges.mapNotNull { it.node.toTitle() } to page.pageInfo.run {
            endCursor.takeIf { hasNextPage }
        }
    }
}

private val videoServices = setOf("prv", "jhs", "snl", "zee", "atp")

@Serializable private data class JwResponse(val data: JwData? = null, val errors: List<JsonObject> = emptyList())
@Serializable private data class JwData(val popularTitles: JwPage)
@Serializable private data class JwPage(val edges: List<JwEdge>, val pageInfo: JwPageInfo)
@Serializable private data class JwPageInfo(val endCursor: String? = null, val hasNextPage: Boolean = false)
@Serializable private data class JwEdge(val node: JwNode)
@Serializable private data class JwNode(
    val id: String,
    val objectType: String? = null,
    val content: JwContent,
    val offers: List<JwOffer> = emptyList()
) {
    fun toTitle(): Title? {
        val kind = when (objectType) {
            "MOVIE" -> Kind.MOVIE
            "SHOW" -> Kind.SHOW
            else -> return null
        }
        return Title(
            id = id, kind = kind, title = content.title,
            year = content.originalReleaseYear, imdb = content.scoring?.imdbScore,
            labels = content.genres.map { it.shortName }, synopsis = content.shortDescription.orEmpty(),
            cast = content.credits.map { it.name }, posterUrl = content.posterUrl.imageUrl("s332"),
            backdropUrl = content.backdrops.firstOrNull()?.backdropUrl.imageUrl("s1440"),
            offers = offers.filter { it.monetizationType == "FLATRATE" && it.`package`.shortName in videoServices }
                .filter { !it.standardWebURL.isNullOrBlank() }
                .distinctBy { it.`package`.shortName }
                .map { Offer(it.`package`.shortName, it.audioLanguages.orEmpty(), it.standardWebURL!!) },
            tmdbId = content.externalIds?.tmdbId, runtimeMin = content.runtime
        )
    }
}
@Serializable private data class JwContent(
    val title: String,
    val shortDescription: String? = null,
    val originalReleaseYear: Int? = null,
    val posterUrl: String? = null,
    val backdrops: List<JwBackdrop> = emptyList(),
    val scoring: JwScoring? = null,
    val genres: List<JwGenre> = emptyList(),
    val credits: List<JwCredit> = emptyList(),
    val runtime: Int? = null,
    val externalIds: JwExternalIds? = null
)
@Serializable private data class JwBackdrop(val backdropUrl: String? = null)
@Serializable private data class JwScoring(val imdbScore: Double? = null)
@Serializable private data class JwGenre(val shortName: String)
@Serializable private data class JwCredit(val name: String)
@Serializable private data class JwExternalIds(val tmdbId: String? = null)
@Serializable private data class JwOffer(
    val monetizationType: String,
    val `package`: JwPackage,
    val audioLanguages: List<String>? = null,
    val standardWebURL: String? = null
)
@Serializable private data class JwPackage(val shortName: String)

private fun String?.imageUrl(profile: String): String? = this?.takeIf { it.isNotBlank() }?.let {
    "https://images.justwatch.com" + it.replace("{profile}", profile).replace("{format}", "webp")
}

internal fun OkHttpClient.contentClient(): OkHttpClient = newBuilder()
    .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
    .writeTimeout(10, TimeUnit.SECONDS).callTimeout(10, TimeUnit.SECONDS)
    .retryOnConnectionFailure(false).followRedirects(false).build()

private class ContentHttpException(code: Int) : IOException("Content HTTP $code")

// Share timeout/error handling; TMDB opts into exactly one retry, including body-read failures.
internal suspend fun requestText(http: OkHttpClient, request: Request, retries: Int = 0): String =
    withContext(Dispatchers.IO) {
        var attempt = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            try {
                return@withContext http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw ContentHttpException(response.code)
                    response.body.string()
                }
            } catch (error: IOException) {
                if (error is ContentHttpException || attempt++ >= retries) throw error
            }
        }
        @Suppress("UNREACHABLE_CODE") error("unreachable")
    }
