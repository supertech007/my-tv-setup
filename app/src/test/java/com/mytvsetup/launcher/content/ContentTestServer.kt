package com.mytvsetup.launcher.content

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal fun fixture(name: String): String =
    checkNotNull(ContentTestServer::class.java.getResource("/$name")).readText()

internal fun jwResponse(connection: String = "popularTitles"): String = buildJsonObject {
    put("data", buildJsonObject {
        put("popularTitles", Json.parseToJsonElement(fixture("jw_popular.json"))
            .jsonObject.getValue("data").jsonObject.getValue(connection))
    })
}.toString()

internal class ContentTestServer : AutoCloseable {
    val server = MockWebServer().apply { start() }
    val urls = mutableListOf<HttpUrl>()
    val http = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        urls += request.url
        val local = server.url(request.url.encodedPath).newBuilder()
            .encodedQuery(request.url.encodedQuery).build()
        chain.proceed(request.newBuilder().url(local).build())
    }.build()

    fun enqueue(body: String, code: Int = 200) = server.enqueue(MockResponse(code = code, body = body))
    fun request() = checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))
    override fun close() {
        http.connectionPool.evictAll()
        http.dispatcher.executorService.shutdown()
        server.close()
    }
}
