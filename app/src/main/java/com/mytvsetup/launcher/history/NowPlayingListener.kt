package com.mytvsetup.launcher.history

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import com.mytvsetup.launcher.content.ContentRuntime
import java.io.File
import java.security.MessageDigest

/**
 * Adds to History whatever any app says it is playing (title, channel, artwork), however it was opened.
 * Android only shares that with an app the user has allowed under Notification access; notifications themselves
 * are never read.
 */
class NowPlayingListener : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private val watched = mutableMapOf<MediaSession, MediaController.Callback>()
    private var sessions: MediaSessionManager? = null
    private val onSessions = MediaSessionManager.OnActiveSessionsChangedListener { follow(it.orEmpty()) }
    private var lastKey: String? = null
    private val players = mutableMapOf<MediaSession, PlayerState>()
    private var awake: android.os.PowerManager.WakeLock? = null
    // Re-checks while something is paused, so the three-hour limit ends even without a new player event.
    private val recheck = object : Runnable {
        override fun run() { updateAwake(); handler.postDelayed(this, 5 * 60_000L) }
    }

    override fun onListenerConnected() {
        val manager = getSystemService(MediaSessionManager::class.java) ?: return
        sessions = manager
        val me = ComponentName(this, NowPlayingListener::class.java)
        runCatching {
            manager.addOnActiveSessionsChangedListener(onSessions, me, handler)
            follow(manager.getActiveSessions(me))
        }
        handler.post(recheck)
    }

    override fun onListenerDisconnected() {
        runCatching { sessions?.removeOnActiveSessionsChangedListener(onSessions) }
        watched.forEach { (session, callback) -> session.controller.unregisterCallback(callback) }
        watched.clear()
        players.clear()
        handler.removeCallbacks(recheck)
        updateAwake()
    }

    private fun follow(controllers: List<MediaController>) {
        val current = controllers.filter { it.packageName != packageName }.associateBy { MediaSession(it.sessionToken, it) }
        (watched.keys - current.keys).forEach { session ->
            watched.remove(session)?.let(session.controller::unregisterCallback)
            players.remove(session)
        }
        (current.keys - watched.keys).forEach { session ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) { track(session); check(session.controller) }
                override fun onMetadataChanged(metadata: MediaMetadata?) = check(session.controller)
            }
            session.controller.registerCallback(callback, handler)
            watched[session] = callback
            track(session)
            check(session.controller)
        }
        updateAwake()
    }

    private fun track(session: MediaSession) {
        val state = session.controller.playbackState?.state
        val before = players[session]
        players[session] = PlayerState(session.controller.packageName,
            playing = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING,
            pausedSince = if (state == PlaybackState.STATE_PAUSED) before?.pausedSince ?: android.os.SystemClock.elapsedRealtime() else null)
        updateAwake()
    }

    /** Holds the screen on (no screensaver) while [keepScreenAwake] says so; renewed by [recheck]. */
    @Suppress("DEPRECATION")
    private fun updateAwake() {
        val keep = keepScreenAwake(players.values.toList(), android.os.SystemClock.elapsedRealtime())
        if (keep) {
            val lock = awake ?: getSystemService(android.os.PowerManager::class.java)
                .newWakeLock(android.os.PowerManager.SCREEN_DIM_WAKE_LOCK, "MyTV:video").also { it.setReferenceCounted(false); awake = it }
            lock.acquire(10 * 60_000L)
        } else awake?.takeIf { it.isHeld }?.release()
    }

    /** Records once per title while it plays; pausing and resuming the same thing adds nothing. */
    private fun check(controller: MediaController) {
        if (controller.playbackState?.state != PlaybackState.STATE_PLAYING) return
        val meta = controller.metadata ?: return
        val title = meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: meta.getString(MediaMetadata.METADATA_KEY_TITLE)
        val subtitle = meta.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val key = "${controller.packageName}|$title"
        if (key == lastKey) return
        val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(controller.packageName, 0)).toString() }
            .getOrDefault(controller.packageName)
        val art = artUri(meta) ?: artBitmap(meta)?.let { saveArt(this, key, it) }
        val entry = nowPlayingEntry(controller.packageName, label, title, subtitle,
            meta.getString(MediaMetadata.METADATA_KEY_MEDIA_ID), art) ?: return
        lastKey = key
        Thread { ContentRuntime.history(this).record(entry) }.start()
    }

    private fun artUri(meta: MediaMetadata) = listOf(MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
        MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI).firstNotNullOfOrNull { meta.getString(it) }?.takeIf { it.startsWith("http") }

    private fun artBitmap(meta: MediaMetadata): Bitmap? = listOf(MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART,
        MediaMetadata.METADATA_KEY_DISPLAY_ICON).firstNotNullOfOrNull { meta.getBitmap(it) }

    /** Wraps a controller so sessions can be compared by token. */
    private data class MediaSession(val token: android.media.session.MediaSession.Token, val controller: MediaController) {
        override fun equals(other: Any?) = other is MediaSession && other.token == token
        override fun hashCode() = token.hashCode()
    }

    companion object {
        fun isAllowed(context: Context) = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

        /** Artwork handed over as a picture is kept as a small file, since History outlives the playing app. */
        private fun saveArt(context: Context, key: String, bitmap: Bitmap): String? = runCatching {
            val dir = File(context.filesDir, "history_art").apply { mkdirs() }
            val name = MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
            val file = File(dir, "$name.jpg")
            val scale = minOf(1f, 480f / maxOf(bitmap.width, bitmap.height))
            val small = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1), true)
            file.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            "file://${file.absolutePath}"
        }.getOrNull()
    }
}
