package com.lostf1sh.pixelplayeross.data.stream.youtube

import kotlinx.serialization.Serializable

/**
 * The slice of a `player` response we need to pick and unlock an audio stream. Decoded
 * leniently (unknown keys ignored) since the full response is enormous.
 */
@Serializable
data class PlayerResponse(
    val playabilityStatus: PlayabilityStatus? = null,
    val streamingData: StreamingData? = null,
    val playbackTracking: PlaybackTracking? = null,
) {
    val isPlayable: Boolean get() = playabilityStatus?.status == "OK"

    @Serializable
    data class PlayabilityStatus(
        val status: String? = null,
        val reason: String? = null,
    )

    /** Stats ping endpoints; the playback one is what writes a play into YT history. */
    @Serializable
    data class PlaybackTracking(
        val videostatsPlaybackUrl: TrackingUrl? = null,
    ) {
        @Serializable
        data class TrackingUrl(val baseUrl: String? = null)
    }

    @Serializable
    data class StreamingData(
        val expiresInSeconds: String? = null,
        val formats: List<Format> = emptyList(),
        val adaptiveFormats: List<Format> = emptyList(),
    ) {
        @Serializable
        data class Format(
            val itag: Int? = null,
            val url: String? = null,
            val signatureCipher: String? = null,
            val cipher: String? = null,
            val mimeType: String? = null,
            val bitrate: Long? = null,
            val averageBitrate: Long? = null,
            val audioSampleRate: String? = null,
            val loudnessDb: Double? = null,
        ) {
            val isAudio: Boolean get() = mimeType?.startsWith("audio/") == true
            val effectiveBitrate: Long get() = bitrate ?: averageBitrate ?: 0L
            val hasSomeSource: Boolean get() = url != null || signatureCipher != null || cipher != null
        }
    }
}
