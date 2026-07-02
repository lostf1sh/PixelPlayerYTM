package com.lostf1sh.pixelplayeross.data.model

/**
 * The bitrate ceiling to request for YTM streams.
 *
 * [AUTO] keeps the historical behavior: best available when signed in (premium accounts
 * unlock ~256 kbps), ~160 kbps anonymously. [HIGH] always asks for the best available.
 * [LOW] caps at ~64 kbps (the small Opus/AAC ladder rungs) for metered connections.
 */
enum class YtAudioQuality {
    AUTO,
    HIGH,
    LOW;

    companion object {
        fun fromName(name: String?): YtAudioQuality =
            entries.firstOrNull { it.name == name } ?: AUTO
    }
}
