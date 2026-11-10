package com.mytvsetup.launcher.history

/** How long a paused video keeps the screen awake before the screensaver may start as usual. */
const val PAUSED_AWAKE_MS = 3 * 60 * 60 * 1000L

/** One app's player as Android reports it; [pausedSince] is when it was paused (elapsed-realtime ms). */
data class PlayerState(val pkg: String, val playing: Boolean, val pausedSince: Long?)

/**
 * Whether the screen should stay awake. The stick's screensaver starts after 15 minutes without a button press,
 * even under a playing or paused video; it stops the app behind it and drops the TV out of HDR, and apps such
 * as JioHotstar then reload the player (seen on the TV, 10 Nov 2026). Music is left alone: its screen may sleep.
 */
fun keepScreenAwake(players: List<PlayerState>, now: Long): Boolean = players.any { p ->
    p.pkg !in MUSIC_APPS && (p.playing || (p.pausedSince != null && now - p.pausedSince < PAUSED_AWAKE_MS))
}
