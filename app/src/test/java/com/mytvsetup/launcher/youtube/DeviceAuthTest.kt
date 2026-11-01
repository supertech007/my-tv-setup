package com.mytvsetup.launcher.youtube

import com.mytvsetup.launcher.content.ContentTestServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DeviceAuthTest {
    private val code = DeviceCode("ABCD-EFGH", "https://www.google.com/device", 5, "device", 1800)

    @Test fun startRequestsReadonlyScopeAndParsesDeviceCode() = runBlocking {
        ContentTestServer().use { api ->
            api.enqueue("""{"device_code":"device","user_code":"ABCD-EFGH","verification_url":"https://www.google.com/device","expires_in":1800,"interval":5}""")
            assertEquals(code, DeviceAuth(api.http, "client", "secret").start())
            assertEquals("https://oauth2.googleapis.com/device/code", api.urls.single().toString())
            val body = api.request().body!!.utf8()
            assertTrue(body.contains("scope=https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fyoutube.readonly"))
            assertTrue(body.contains("client_id=client"))
        }
    }

    @Test fun authorizationPendingReturnsNull() = runBlocking {
        ContentTestServer().use { api ->
            api.enqueue("""{"error":"authorization_pending"}""", 428)
            assertNull(DeviceAuth(api.http, "client", "secret").poll(code.copy()))
        }
    }

    @Test fun slowDownAddsFiveSecondsBeforeNextPoll() = runBlocking {
        ContentTestServer().use { api ->
            api.enqueue("""{"error":"slow_down"}""", 403)
            val pollingCode = code.copy()
            assertNull(DeviceAuth(api.http, "client", "secret").poll(pollingCode))
            assertEquals(10, pollingCode.interval)
        }
    }

    @Test fun successReturnsTokenAndUsesDeviceGrant() = runBlocking {
        ContentTestServer().use { api ->
            api.enqueue("""{"access_token":"access","refresh_token":"refresh","expires_in":3600,"token_type":"Bearer","scope":"https://www.googleapis.com/auth/youtube.readonly"}""")
            val token = DeviceAuth(api.http, "client", "secret").poll(code.copy())!!
            assertEquals("access", token.accessToken)
            assertEquals("refresh", token.refreshToken)
            assertEquals(3600L, token.expiresIn)
            assertEquals("https://oauth2.googleapis.com/token", api.urls.single().toString())
            val body = api.request().body!!.utf8()
            assertTrue(body.contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code"))
            assertTrue(body.contains("device_code=device"))
            assertTrue(body.contains("client_secret=secret"))
        }
    }

    @Test fun expiredCodeAndDenialStopPollingWithSafeError() {
        for (error in listOf("expired_token", "access_denied")) {
            ContentTestServer().use { api ->
                api.enqueue("""{"error":"$error","error_description":"secret device"}""", 400)
                val result = assertThrows(DeviceAuthException::class.java) {
                    runBlocking { DeviceAuth(api.http, "client", "secret").poll(code.copy()) }
                }
                assertEquals(error, result.reason)
                assertFalse(result.message.orEmpty().contains("secret"))
            }
        }
    }

    @Test fun blankCredentialsNeverMakeRequests() = runBlocking {
        ContentTestServer().use { api ->
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { DeviceAuth(api.http, "", "").start() }
            }
            assertEquals(0, api.server.requestCount)
        }
    }
}
