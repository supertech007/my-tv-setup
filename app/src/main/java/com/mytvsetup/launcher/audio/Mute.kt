package com.mytvsetup.launcher.audio

import android.content.Context
import android.media.AudioManager

/**
 * The Mi remote has no mute button. This mutes the way Android's own mute key does: the stick's sound goes silent
 * (checked on the TV, which plays through an HDMI ARC speaker). The remote's volume buttons go to that speaker and
 * never clear this mute: unmute with the dock tile or Back + Up.
 */
object Mute {
    fun mute(context: Context) = adjust(context, AudioManager.ADJUST_MUTE)
    fun unmute(context: Context) = adjust(context, AudioManager.ADJUST_UNMUTE)
    fun isMuted(context: Context) = audio(context).isStreamMute(AudioManager.STREAM_MUSIC)
    /** The dock tile mutes and unmutes: the remote's volume buttons go to the TV over HDMI and cannot. */
    fun toggle(context: Context) = if (isMuted(context)) unmute(context) else mute(context)

    private fun audio(context: Context) = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private fun adjust(context: Context, direction: Int) = audio(context).adjustSuggestedStreamVolume(
        direction, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
}
