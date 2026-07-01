package com.lostf1sh.pixelplayeross.data.youtube.stream

/**
 * Target audio quality for YouTube stream resolution. Caps the bitrate we pick from the
 * available adaptive formats. Anonymous playback is effectively limited to ~128kbps;
 * [HIGH] only unlocks higher AAC formats once the user is signed in (Phase 3).
 */
enum class AudioQuality(val maxBitrate: Int) {
    LOW(96_000),
    MEDIUM(160_000),
    HIGH(Int.MAX_VALUE),
}
