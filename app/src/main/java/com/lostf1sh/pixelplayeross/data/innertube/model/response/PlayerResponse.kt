package com.lostf1sh.pixelplayeross.data.innertube.model.response

import kotlinx.serialization.Serializable

/**
 * Typed model of the InnerTube `player` response — the source of streamable audio URLs
 * and basic track metadata. Only the fields we consume are declared; the lenient
 * app-wide [kotlinx.serialization.json.Json] (`ignoreUnknownKeys = true`) drops the rest.
 */
@Serializable
data class PlayerResponse(
    val playabilityStatus: PlayabilityStatus? = null,
    val streamingData: StreamingData? = null,
    val videoDetails: VideoDetails? = null,
) {
    @Serializable
    data class PlayabilityStatus(
        val status: String? = null,
        val reason: String? = null,
    ) {
        val isOk: Boolean get() = status == "OK"
    }

    @Serializable
    data class StreamingData(
        val expiresInSeconds: String? = null,
        val formats: List<Format> = emptyList(),
        val adaptiveFormats: List<Format> = emptyList(),
    )

    @Serializable
    data class Format(
        val itag: Int,
        /** Present when the URL is directly streamable (mobile clients). */
        val url: String? = null,
        /** Present when the URL is obfuscated and needs base.js deciphering. */
        val signatureCipher: String? = null,
        val mimeType: String? = null,
        val bitrate: Int? = null,
        val averageBitrate: Int? = null,
        val contentLength: String? = null,
        val audioQuality: String? = null,
        val audioSampleRate: String? = null,
        val approxDurationMs: String? = null,
        val loudnessDb: Double? = null,
    ) {
        val isAudio: Boolean get() = mimeType?.startsWith("audio/") == true
    }

    @Serializable
    data class VideoDetails(
        val videoId: String? = null,
        val title: String? = null,
        val author: String? = null,
        val channelId: String? = null,
        val lengthSeconds: String? = null,
        val thumbnail: Thumbnails? = null,
    )

    @Serializable
    data class Thumbnails(
        val thumbnails: List<Thumbnail> = emptyList(),
    )

    @Serializable
    data class Thumbnail(
        val url: String,
        val width: Int? = null,
        val height: Int? = null,
    )
}
