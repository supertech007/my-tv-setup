package com.mytvsetup.launcher.youtube

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal fun OkHttpClient.youtubeHttp(): OkHttpClient = newBuilder()
    .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
    .callTimeout(30, TimeUnit.SECONDS).build()

internal suspend fun OkHttpClient.youtubeRequest(request: Request): Pair<Int, JsonObject> =
    suspendCancellableCoroutine { continuation ->
        val call = newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Network exceptions may contain URLs; never expose server bodies or credentials.
                if (!continuation.isCancelled) continuation.resumeWithException(IOException("YouTube is offline"))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val result = response.use {
                        val body = it.body.string()
                        it.code to Json.parseToJsonElement(body).jsonObject
                    }
                    continuation.resume(result)
                } catch (_: Exception) {
                    if (!continuation.isCancelled) continuation.resumeWithException(IOException("Invalid YouTube response"))
                }
            }
        })
    }

internal fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.content
