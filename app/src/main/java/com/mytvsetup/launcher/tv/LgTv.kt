package com.mytvsetup.launcher.tv

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import okhttp3.OkHttpClient

/** The launcher's LG TV: finds it once, remembers its address and pairing key, and switches its screen. */
class LgTv private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("lg_tv", Context.MODE_PRIVATE)
    private val client = LgTvClient(lgHttpClient(),
        context.assets.open("lg_pairing.json").bufferedReader().use { it.readText() },
        object : LgTvClient.KeyStore {
            override fun get(host: String) = prefs.getString("key:$host", null)
            override fun put(host: String, key: String) { prefs.edit().putString("key:$host", key).apply() }
        })
    /** Outlives the screen that asked, so "screen on" still runs after the black page closes. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun screenOff(onPrompt: () -> Unit): Boolean = withTv { url, host -> client.screenOff(url, host, onPrompt) }

    fun screenOnLater() { scope.launch { withTv { url, host -> client.screenOn(url, host) } } }

    // A saved address is tried first; if the TV moved (new IP), it is found again once.
    private suspend fun withTv(action: suspend (String, String) -> Boolean): Boolean {
        prefs.getString("host", null)?.let { if (action("wss://$it:3001", it)) return true }
        val found = LgDiscovery.find(context) ?: return false.also { Log.w(TAG, "No LG TV found on the network") }
        Log.i(TAG, "LG TV found at $found")
        prefs.edit().putString("host", found).apply()
        return action("wss://$found:3001", found).also { if (!it) Log.w(TAG, "LG TV at $found refused or did not answer") }
    }

    companion object {
        private const val TAG = "LgTv"

        fun isLgTvCertificate(subject: String) =
            Regex("(^|,)\\s*CN=LG_TV_").containsMatchIn(subject) && subject.contains("O=LG Electronics")

        fun isHomeAddress(host: String) = runCatching {
            host.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}")) && InetAddress.getByName(host).isSiteLocalAddress
        }.getOrDefault(false)

        /**
         * Android blocks unencrypted sockets, so the TV is reached on its encrypted port 3001. The TV signs that
         * with its own LG certificate, which no public authority vouches for; this client trusts exactly that
         * kind of certificate, and only on a home-network address. It is used for nothing else.
         */
        private fun lgHttpClient(): OkHttpClient {
            val trust = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
                    throw CertificateException("Not a server")
                override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                    val cert = chain.firstOrNull() ?: throw CertificateException("No certificate")
                    cert.checkValidity()
                    if (!isLgTvCertificate(cert.subjectX500Principal.name)) throw CertificateException("Not an LG TV")
                }
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
            return OkHttpClient.Builder().sslSocketFactory(tls.socketFactory, trust)
                .hostnameVerifier { host, _ -> isHomeAddress(host) }.build()
        }
        @Volatile private var instance: LgTv? = null
        fun get(context: Context): LgTv = instance ?: synchronized(this) {
            instance ?: LgTv(context.applicationContext).also { instance = it }
        }
    }
}
