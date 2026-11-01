package com.mytvsetup.launcher.youtube

import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

@Serializable
data class YtAccount(val id: String, val email: String, val channelTitle: String)

@Serializable
data class YtVideo(
    val id: String, val title: String, val channel: String, val thumb: String,
    @Serializable(with = InstantTextSerializer::class) val publishedAt: Instant, val accountId: String
)

@Serializable
data class YtPlaylist(val id: String, val title: String, val accountId: String)

fun visibleVideos(all: List<YtVideo>, hidden: Set<String>): List<YtVideo> =
    all.filterNot { it.accountId in hidden }

internal object InstantTextSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
