package com.lostf1sh.pixelplayeross.data.innertube.parser

import com.lostf1sh.pixelplayeross.data.innertube.model.response.PlayerResponse
import com.lostf1sh.pixelplayeross.data.youtube.stream.AudioQuality

/**
 * Selects the best audio [PlayerResponse.Format] from a `player` response. Both plain-URL
 * formats and `signatureCipher` formats are eligible; turning the chosen format into a
 * playable URL (signature + n-param transforms) is done by
 * [YouTubeCipherManager][com.lostf1sh.pixelplayeross.data.youtube.stream.YouTubeCipherManager].
 */
internal object PlayerParser {

    fun selectAudioFormat(response: PlayerResponse, quality: AudioQuality): PlayerResponse.Format? {
        if (response.playabilityStatus?.isOk == false) return null
        val streaming = response.streamingData ?: return null

        val candidates = (streaming.adaptiveFormats + streaming.formats)
            .filter { it.isAudio && (!it.url.isNullOrBlank() || !it.signatureCipher.isNullOrBlank()) }
            .filter { (it.averageBitrate ?: it.bitrate ?: 0) <= quality.maxBitrate || quality == AudioQuality.HIGH }

        return candidates.maxByOrNull { format ->
            // Prefer opus, then higher bitrate.
            val opusBonus = if (format.mimeType?.contains("opus") == true) 1_000_000 else 0
            opusBonus + (format.averageBitrate ?: format.bitrate ?: 0)
        }
    }
}
