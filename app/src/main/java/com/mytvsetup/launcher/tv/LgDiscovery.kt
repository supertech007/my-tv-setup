package com.mytvsetup.launcher.tv

import android.content.Context
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

/** Finds an LG webOS TV on the home network by asking for its "second screen" service over SSDP. */
object LgDiscovery {
    private const val SERVICE = "urn:lge-com:service:webos-second-screen:1"

    fun hostFrom(reply: String): String? {
        if (!reply.contains(SERVICE, ignoreCase = true)) return null
        return Regex("(?im)^LOCATION:\\s*https?://([^:/\\s]+)").find(reply)?.groupValues?.get(1)
    }

    fun subnetHosts(own: String): List<String> {
        val prefix = own.substringBeforeLast('.')
        return (1..254).map { "$prefix.$it" }.filter { it != own }
    }

    /** An LG webOS TV answers a plain request on its control port with exactly this. */
    fun isLgGreeting(body: String) = body.trim() == "Hello world"

    suspend fun find(context: Context): String? = ssdp(context) ?: scan()

    /** Some LG TVs ignore SSDP; check every address on the stick's /24 for the control port. */
    private suspend fun scan(): String? = withContext(Dispatchers.IO) {
        val own = NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
            ?.hostAddress ?: return@withContext null
        val open = subnetHosts(own).chunked(64).flatMap { batch ->
            batch.map { host -> async { host.takeIf { portOpen(it, 3000) } } }.awaitAll().filterNotNull()
        }
        open.firstOrNull { host ->
            runCatching {
                (URL("http://$host:3000/").openConnection() as HttpURLConnection).run {
                    connectTimeout = 800; readTimeout = 800
                    try { isLgGreeting(inputStream.bufferedReader().readText()) } finally { disconnect() }
                }
            }.getOrDefault(false)
        }
    }

    private fun portOpen(host: String, port: Int) = runCatching {
        Socket().use { it.connect(InetSocketAddress(host, port), 300) }; true
    }.getOrDefault(false)

    private suspend fun ssdp(context: Context, waitMs: Int = 2_000): String? = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("lg-discovery")?.apply { setReferenceCounted(false); acquire() }
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = waitMs
                val search = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $SERVICE\r\n\r\n"
                    .toByteArray()
                socket.send(DatagramPacket(search, search.size, InetAddress.getByName("239.255.255.250"), 1900))
                val buffer = ByteArray(2048)
                while (true) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try { socket.receive(packet) } catch (_: SocketTimeoutException) { return@use null }
                    hostFrom(String(packet.data, 0, packet.length))?.let { return@use it }
                }
                @Suppress("UNREACHABLE_CODE") null
            }
        } catch (_: Exception) {
            null
        } finally {
            lock?.release()
        }
    }
}
