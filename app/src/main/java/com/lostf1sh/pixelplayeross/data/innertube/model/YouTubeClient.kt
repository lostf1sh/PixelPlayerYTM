package com.lostf1sh.pixelplayeross.data.innertube.model

/**
 * InnerTube client identities.
 *
 * YouTube's private `youtubei/v1` API keys every request to a "client" (the app
 * pretending to make the call). Different clients unlock different data and,
 * crucially, different `player` responses:
 *
 * - [WEB_REMIX] is the YouTube Music web client — the canonical source for browse,
 *   search, and library data.
 * - [ANDROID_MUSIC] returns `player` responses whose adaptive formats carry a plain,
 *   directly-streamable `url` (no `signatureCipher`), which lets us avoid running
 *   YouTube's obfuscated `base.js` entirely. This is the primary stream-resolution path.
 * - [TVHTML5_SIMPLY_EMBEDDED_PLAYER] is a fallback player client for age/embed-gated
 *   videos.
 *
 * These constants (client name, version, API key, numeric client id) are public,
 * widely-used values. They drift over time; if InnerTube starts rejecting requests
 * with 400s or empty responses, bump [clientVersion] (and re-check the API keys).
 */
data class YouTubeClient(
    val clientName: String,
    val clientVersion: String,
    val apiKey: String,
    /** Numeric id sent as the `X-YouTube-Client-Name` header. */
    val clientId: String,
    val userAgent: String,
    val androidSdkVersion: Int? = null,
    val referer: String? = null,
) {
    companion object {
        private const val USER_AGENT_WEB =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        /** YouTube Music web client — browse, search, next, library. */
        val WEB_REMIX = YouTubeClient(
            clientName = "WEB_REMIX",
            clientVersion = "1.20241023.01.00",
            apiKey = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30",
            clientId = "67",
            userAgent = USER_AGENT_WEB,
            referer = "https://music.youtube.com/",
        )

        /** Android Music client — returns `player` formats with plain (un-ciphered) URLs. */
        val ANDROID_MUSIC = YouTubeClient(
            clientName = "ANDROID_MUSIC",
            clientVersion = "7.27.52",
            apiKey = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI",
            clientId = "21",
            userAgent = "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 11) gzip",
            androidSdkVersion = 30,
        )

        /** Embedded-player fallback for age/embed-gated content. */
        val TVHTML5 = YouTubeClient(
            clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
            clientVersion = "2.0",
            apiKey = "AIzaSyDCU8hByM-4DrUqRUYnGn-3llEO78bcxq8",
            clientId = "85",
            userAgent = "Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/16.0 Safari/605.1.15",
            referer = "https://www.youtube.com/",
        )
    }
}
