package com.mytvsetup.launcher.settings

import kotlinx.serialization.Serializable

val DEFAULT_DOCK = listOf(
    "com.amazon.amazonvideo.livingroom",
    "in.startv.hotstar",
    "com.sonyliv",
    "com.graymatrix.did",
    "tv.airtel.xstream.tvapp",
    "org.smarttube.stable",
    "com.apple.atve.androidtv.appletv",
    "com.jio.media.jiobeats",
    "com.amazon.music.tv"
)

@Serializable
data class Prefs(
    val name: String = "",
    val city: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val wallpaper: String = "rain",
    val accent: String = "ice",
    val dockOrder: List<String> = DEFAULT_DOCK,
    val hiddenApps: Set<String> = emptySet(),
    val hiddenCards: Set<String> = emptySet(),
    val personalise: Boolean = true,
    val videoLangs: Set<String> = setOf("hi", "en"),
    val musicLangs: Set<String> = setOf("pa", "bgc", "hi", "en"),
    val musicApp: String = "com.jio.media.jiobeats",
    val hiddenYtAccounts: Set<String> = emptySet(),
    val historyDays: Int = com.mytvsetup.launcher.history.DEFAULT_KEEP_DAYS
)

fun moveInDock(order: List<String>, pkg: String, delta: Int): List<String> {
    val from = order.indexOf(pkg)
    val to = from.toLong() + delta
    if (from < 0 || to < 0 || to >= order.size || delta == 0) return order
    return order.toMutableList().apply {
        removeAt(from)
        add(to.toInt(), pkg)
    }
}
