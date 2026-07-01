package com.lostf1sh.pixelplayerytm.data.innertube.model.response

import kotlinx.serialization.Serializable

@Serializable
data class PlayerResponse(
    val playabilityStatus: PlayabilityStatus? = null,
    val streamingData: StreamingData? = null,
    val videoDetails: VideoDetails? = null,
    val responseContext: ResponseContext? = null,
) {
    val isPlayable: Boolean
        get() = playabilityStatus?.status == "OK"

    @Serializable
    data class PlayabilityStatus(
        val status: String? = null,
        val reason: String? = null,
    )

    @Serializable
    data class StreamingData(
        val expiresInSeconds: String? = null,
        val formats: List<Format>? = null,
        val adaptiveFormats: List<Format>? = null,
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
            val contentLength: Long? = null,
            val audioQuality: String? = null,
            val audioSampleRate: String? = null,
            val approxDurationMs: String? = null,
            val loudnessDb: Double? = null,
        ) {
            val isAudio: Boolean
                get() = mimeType?.startsWith("audio/") == true
        }
    }

    @Serializable
    data class VideoDetails(
        val videoId: String? = null,
        val title: String? = null,
        val author: String? = null,
        val channelId: String? = null,
        val lengthSeconds: String? = null,
        val thumbnail: Thumbnails? = null,
        val musicVideoType: String? = null,
    )
}
