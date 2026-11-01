package com.mytvsetup.launcher.youtube

import com.mytvsetup.launcher.content.ContentTestServer
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class DeviceAuthPollingTest {
    private val code = DeviceCode("CODE", "https://www.google.com/device", 0, "device", 1800)
    private val success = """{"access_token":"access","refresh_token":"refresh","expires_in":3600,"token_type":"Bearer"}"""

    @Test fun threeTransientFailuresAreToleratedBeforeSuccess() = runBlocking {
        ContentTestServer().use { api ->
            api.enqueue(success)
            var polls = 0
            val http = api.http.newBuilder().addInterceptor { chain ->
                if (++polls <= 3) throw IOException("offline")
                chain.proceed(chain.request())
            }.build()
            assertEquals("access", DeviceAuth(http, "client", "secret").awaitToken(code.copy()).accessToken)
            assertEquals(4, polls)
        }
    }

    @Test fun fourthConsecutiveTransientFailureStopsPolling() {
        ContentTestServer().use { api ->
            var polls = 0
            val http = api.http.newBuilder().addInterceptor {
                polls++
                throw IOException("offline")
            }.build()
            assertThrows(IOException::class.java) {
                runBlocking { DeviceAuth(http, "client", "secret").awaitToken(code.copy()) }
            }
            assertEquals(4, polls)
        }
    }

    @Test fun pendingResponseResetsTheConsecutiveFailureCount() = runBlocking {
        ContentTestServer().use { api ->
            api.enqueue("""{"error":"authorization_pending"}""", 428)
            api.enqueue(success)
            var polls = 0
            val http = api.http.newBuilder().addInterceptor { chain ->
                if (++polls !in setOf(4, 8)) throw IOException("offline")
                chain.proceed(chain.request())
            }.build()
            assertEquals("access", DeviceAuth(http, "client", "secret").awaitToken(code.copy()).accessToken)
            assertEquals(8, polls)
        }
    }

    @Test fun terminalAuthErrorsStopImmediatelyWithoutRetry() {
        for (reason in listOf("access_denied", "expired_token", "invalid_grant")) {
            ContentTestServer().use { api ->
                api.enqueue("""{"error":"$reason"}""", 400)
                val failure = assertThrows(DeviceAuthException::class.java) {
                    runBlocking { DeviceAuth(api.http, "client", "secret").awaitToken(code.copy()) }
                }
                assertEquals(reason, failure.reason)
                assertEquals(1, api.server.requestCount)
            }
        }
    }

    @Test fun expirationAndCancellationStopBeforeTheNextPoll() = runBlocking {
        ContentTestServer().use { api ->
            val auth = DeviceAuth(api.http, "client", "secret")
            assertThrows(TimeoutCancellationException::class.java) {
                runBlocking { auth.awaitToken(code.copy(interval = 2, expiresIn = 1)) }
            }
            val job = launch { auth.awaitToken(code.copy(interval = 60)) }
            yield()
            job.cancelAndJoin()
            assertEquals(0, api.server.requestCount)
        }
    }
}
