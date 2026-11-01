package com.mytvsetup.launcher.youtube

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Mutable interval is shared with the polling loop so slow_down affects its next delay. */
data class DeviceCode(
    val userCode: String,
    val verificationUrl: String,
    var interval: Int,
    val deviceCode: String,
    val expiresIn: Int = 1800
) {
    override fun toString() = "DeviceCode(userCode=$userCode, interval=$interval)"
}

data class Token(val accessToken: String, val refreshToken: String?, val expiresIn: Long) {
    override fun toString() = "Token([redacted])"
}

class DeviceAuthException(val reason: String) : IOException(when (reason) {
    "expired_token" -> "Code expired. Choose Add account to try again."
    "access_denied" -> "Sign-in was declined."
    "invalid_grant" -> "Please add this account again."
    else -> "YouTube sign-in failed. Check the YouTube client in secrets.properties."
})

class DeviceAuth(http: OkHttpClient, private val clientId: String, private val clientSecret: String) {
    private val http = http.youtubeHttp()
    val configured: Boolean get() = clientId.isNotBlank() && clientSecret.isNotBlank()

    suspend fun start(): DeviceCode {
        require(configured) { "Add the YouTube key in secrets.properties" }
        val (status, response) = post("device/code", FormBody.Builder()
            .add("client_id", clientId)
            .add("scope", "https://www.googleapis.com/auth/youtube.readonly").build())
        checkResponse(status, response)
        return DeviceCode(
            response.required("user_code"), response.required("verification_url"),
            (response.text("interval")?.toIntOrNull() ?: 5).coerceAtLeast(5),
            response.required("device_code"),
            (response.text("expires_in")?.toIntOrNull() ?: 1800).coerceIn(1, 3600)
        )
    }

    /** Makes one request. The caller waits code.interval seconds before each poll. */
    suspend fun poll(code: DeviceCode): Token? {
        require(configured) { "Add the YouTube key in secrets.properties" }
        val (status, response) = post("token", credentials()
            .add("device_code", code.deviceCode)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code").build())
        when (response.text("error")) {
            "authorization_pending" -> return null
            "slow_down" -> { code.interval += 5; return null }
        }
        checkResponse(status, response)
        return token(response)
    }

    /** Waits at the server's interval until approved, expired, or cancelled. */
    suspend fun awaitToken(code: DeviceCode): Token = withTimeout(code.expiresIn * 1000L) {
        var consecutiveFailures = 0
        var token: Token? = null
        while (token == null) {
            delay(code.interval * 1000L)
            try {
                token = poll(code)
                consecutiveFailures = 0
            } catch (failure: DeviceAuthException) { throw failure }
            catch (failure: IOException) {
                if (++consecutiveFailures > 3) throw failure
            }
        }
        token
    }

    suspend fun refresh(refreshToken: String): Token {
        require(configured) { "Add the YouTube key in secrets.properties" }
        val (status, response) = post("token", credentials()
            .add("refresh_token", refreshToken).add("grant_type", "refresh_token").build())
        checkResponse(status, response)
        return token(response)
    }

    private fun credentials() = FormBody.Builder().add("client_id", clientId).add("client_secret", clientSecret)
    private suspend fun post(path: String, form: FormBody) = http.youtubeRequest(
        Request.Builder().url("https://oauth2.googleapis.com/$path").post(form).build()
    )
    private fun checkResponse(status: Int, response: JsonObject) {
        if (status !in 200..299 || response.text("error") != null) {
            val reason = response.text("error").takeIf {
                it in setOf("expired_token", "access_denied", "invalid_grant")
            } ?: "oauth_error"
            throw DeviceAuthException(reason)
        }
    }
    private fun token(response: JsonObject) = Token(
        response.required("access_token"), response.text("refresh_token"),
        response.text("expires_in")?.toLongOrNull()?.coerceIn(1, 86400) ?: 3600
    )
    private fun JsonObject.required(key: String): String = text(key)?.takeIf { it.isNotBlank() }
        ?: throw IOException("Incomplete YouTube sign-in response")
}
