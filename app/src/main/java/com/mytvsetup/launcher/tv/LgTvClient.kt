package com.mytvsetup.launcher.tv

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Talks to an LG webOS TV over its local "second screen" socket (port 3000). The first connection makes the TV
 * ask once whether to allow this device; the key it returns is saved so later commands need no prompt.
 * Checked on an LG TV (webOS 4.5): screen off keeps the sound playing, screen on brings the picture back.
 */
class LgTvClient(private val http: OkHttpClient, private val pairing: String, private val keys: KeyStore) {
    interface KeyStore {
        fun get(host: String): String?
        fun put(host: String, key: String)
    }

    suspend fun screenOff(url: String, host: String, onPrompt: () -> Unit = {}) =
        command(url, host, "ssap://com.webos.service.tvpower/power/turnOffScreen", onPrompt)

    suspend fun screenOn(url: String, host: String, onPrompt: () -> Unit = {}) =
        command(url, host, "ssap://com.webos.service.tvpower/power/turnOnScreen", onPrompt)

    private suspend fun command(url: String, host: String, uri: String, onPrompt: () -> Unit): Boolean {
        val inbox = Channel<JsonObject?>(Channel.UNLIMITED)
        val socket = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                inbox.trySend(runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull())
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                android.util.Log.w("LgTv", "Socket to $url failed: ${t.javaClass.simpleName} ${t.message}")
                inbox.trySend(null)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { inbox.trySend(null) }
        })
        return try {
            val payload = Json.parseToJsonElement(pairing).jsonObject.toMutableMap()
            keys.get(host)?.let { payload["client-key"] = JsonPrimitive(it) }
            socket.send(buildJsonObject {
                put("type", "register"); put("id", "reg0"); put("payload", JsonObject(payload))
            }.toString())
            // Up to a minute: the TV waits for someone to accept its prompt with the LG remote.
            withTimeout(PAIRING_MS) {
                while (true) {
                    val msg = inbox.receive() ?: return@withTimeout false
                    val payloadIn = msg["payload"] as? JsonObject
                    when (msg["type"]?.jsonPrimitive?.contentOrNull) {
                        "response" -> if (payloadIn?.get("pairingType") != null) onPrompt()
                            else if (msg["id"]?.jsonPrimitive?.contentOrNull == "cmd")
                                return@withTimeout payloadIn?.get("returnValue")?.jsonPrimitive?.booleanOrNull == true
                        "registered" -> {
                            payloadIn?.get("client-key")?.jsonPrimitive?.contentOrNull?.let { keys.put(host, it) }
                            socket.send(buildJsonObject {
                                put("type", "request"); put("id", "cmd"); put("uri", uri); put("payload", JsonObject(emptyMap()))
                            }.toString())
                        }
                        "error" -> return@withTimeout false
                    }
                }
                @Suppress("UNREACHABLE_CODE") false
            }
        } catch (_: TimeoutCancellationException) {
            false
        } finally {
            socket.close(1000, null)
        }
    }

    companion object {
        const val PAIRING_MS = 60_000L
    }
}
