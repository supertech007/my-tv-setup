package com.mytvsetup.launcher.tv

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class LgTvClientTest {
    private val server = MockWebServer().apply { start() }
    private val received = CopyOnWriteArrayList<JsonObject>()
    private val keys = mutableMapOf<String, String>()
    private val store = object : LgTvClient.KeyStore {
        override fun get(host: String) = keys[host]
        override fun put(host: String, key: String) { keys[host] = key }
    }

    /** A fake webOS TV: prompts on first pairing, then answers commands like the real LG (webOS 4.5). */
    private fun fakeTv(commandOk: Boolean = true) = MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val msg = Json.parseToJsonElement(text).jsonObject
            received += msg
            when (msg["type"]?.jsonPrimitive?.content) {
                "register" -> {
                    if (msg["payload"]!!.jsonObject["client-key"] == null)
                        webSocket.send("""{"type":"response","id":"reg0","payload":{"pairingType":"PROMPT"}}""")
                    webSocket.send("""{"type":"registered","id":"reg0","payload":{"client-key":"KEY-1"}}""")
                }
                "request" -> webSocket.send("""{"type":"response","id":"${msg["id"]!!.jsonPrimitive.content}",
                    "payload":{"returnValue":$commandOk,"state":"Screen Off"}}""")
            }
        }
        override fun onOpen(webSocket: WebSocket, response: Response) = Unit
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
    }).build()

    private fun client() = LgTvClient(OkHttpClient(), """{"pairingType":"PROMPT","manifest":{}}""", store)
    private val url get() = server.url("/").toString().replace("http", "ws")

    @After fun stop() = server.close()

    @Test fun firstUsePairsSavesTheKeyAndTurnsTheScreenOff() = runBlocking {
        server.enqueue(fakeTv())
        var prompted = false
        assertTrue(client().screenOff(url, "tv") { prompted = true })
        assertTrue("the TV asked to allow the launcher", prompted)
        assertEquals("KEY-1", keys["tv"])
        val request = received.single { it["type"]!!.jsonPrimitive.content == "request" }
        assertEquals("ssap://com.webos.service.tvpower/power/turnOffScreen", request["uri"]!!.jsonPrimitive.content)
    }

    @Test fun aSavedKeyIsSentSoTheTvDoesNotAskAgain() = runBlocking {
        keys["tv"] = "KEY-1"
        server.enqueue(fakeTv())
        var prompted = false
        assertTrue(client().screenOn(url, "tv") { prompted = true })
        assertFalse(prompted)
        val register = received.first { it["type"]!!.jsonPrimitive.content == "register" }
        assertEquals("KEY-1", register["payload"]!!.jsonObject["client-key"]!!.jsonPrimitive.content)
        assertEquals("ssap://com.webos.service.tvpower/power/turnOnScreen",
            received.last()["uri"]!!.jsonPrimitive.content)
    }

    @Test fun aRefusedCommandReportsFailure() = runBlocking {
        server.enqueue(fakeTv(commandOk = false))
        assertFalse(client().screenOff(url, "tv"))
    }

    @Test fun anUnreachableTvReportsFailureInsteadOfThrowing() = runBlocking {
        server.close()
        assertFalse(client().screenOff(url, "tv"))
    }

    @Test fun discoveryReadsTheTvAddressFromItsSsdpReply() {
        val reply = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nLOCATION: http://192.168.0.20:1528/\r\n" +
            "SERVER: WebOS/4.1.0 UPnP/1.0\r\nST: urn:lge-com:service:webos-second-screen:1\r\n\r\n"
        assertEquals("192.168.0.20", LgDiscovery.hostFrom(reply))
        assertNull(LgDiscovery.hostFrom("HTTP/1.1 200 OK\r\nST: urn:other\r\nLOCATION: http://10.0.0.9/\r\n\r\n"))
    }

    @Test fun aNetworkScanCoversTheSticksOwnSubnetButNotItself() {
        // Some LG TVs ignore SSDP, so the stick checks its /24 for the TV's control port.
        val hosts = LgDiscovery.subnetHosts("192.168.0.15")
        assertEquals(253, hosts.size)
        assertTrue("192.168.0.20" in hosts)
        assertFalse("192.168.0.15" in hosts)
        assertFalse("192.168.1.0" in hosts || "192.168.1.255" in hosts)
    }

    @Test fun theTvIsRecognisedByItsControlPortGreeting() {
        assertTrue(LgDiscovery.isLgGreeting("Hello world"))
        assertTrue(LgDiscovery.isLgGreeting("Hello world\n"))
        assertFalse(LgDiscovery.isLgGreeting("<html>dev server</html>"))
    }

    @Test fun onlyAnLgTvCertificateOnAHomeAddressIsTrusted() {
        // The TV's own certificate, in the form an LG TV presents on port 3001.
        val lg = "CN=LG_TV_0123456789abcdef,O=LG Electronics U.S.A\\, Inc.,ST=New Jersey,C=US"
        assertTrue(LgTv.isLgTvCertificate(lg))
        assertFalse(LgTv.isLgTvCertificate("CN=evil.example,O=Someone"))
        assertFalse(LgTv.isLgTvCertificate("CN=LG_TV_x,O=Not LG"))
        assertTrue(LgTv.isHomeAddress("192.168.0.20"))
        assertFalse(LgTv.isHomeAddress("8.8.8.8"))
        assertFalse(LgTv.isHomeAddress("example.com"))
    }
}
