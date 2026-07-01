package com.lostf1sh.pixelplayerytm.data.innertube.model

import kotlinx.serialization.Serializable

/**
 * An InnerTube client identity. Client versions drift over time; if requests
 * start failing with 400s, bump the versions here first.
 */
@Serializable
data class YouTubeClient(
    val clientName: String,
    val clientVersion: String,
    val clientId: Int,
    val apiKey: String,
    val userAgent: String,
    val osVersion: String? = null,
    val androidSdkVersion: Int? = null,
    val referer: String? = null,
    /** Whether this client returns plain stream URLs (no signatureCipher). */
    val useSignatureTimestamp: Boolean = false,
) {
    fun toContext(visitorData: String?, hl: String, gl: String): Context = Context(
        client = Context.Client(
            clientName = clientName,
            clientVersion = clientVersion,
            osVersion = osVersion,
            androidSdkVersion = androidSdkVersion,
            userAgent = userAgent,
            hl = hl,
            gl = gl,
            visitorData = visitorData,
        ),
    )

    companion object {
        const val ORIGIN_MUSIC = "https://music.youtube.com"

        val WEB_REMIX = YouTubeClient(
            clientName = "WEB_REMIX",
            clientVersion = "1.20250310.01.00",
            clientId = 67,
            apiKey = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36",
            referer = ORIGIN_MUSIC,
            useSignatureTimestamp = true,
        )

        val ANDROID_MUSIC = YouTubeClient(
            clientName = "ANDROID_MUSIC",
            clientVersion = "7.27.52",
            clientId = 21,
            apiKey = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI",
            userAgent = "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 14) gzip",
            osVersion = "14",
            androidSdkVersion = 34,
        )

        val TVHTML5 = YouTubeClient(
            clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
            clientVersion = "2.0",
            clientId = 85,
            apiKey = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30",
            userAgent = "Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.0 Safari/605.1.15",
            referer = "https://www.youtube.com",
            useSignatureTimestamp = true,
        )
    }
}

@Serializable
data class Context(
    val client: Client,
    val thirdParty: ThirdParty? = null,
) {
    @Serializable
    data class Client(
        val clientName: String,
        val clientVersion: String,
        val osVersion: String? = null,
        val androidSdkVersion: Int? = null,
        val userAgent: String? = null,
        val hl: String = "en",
        val gl: String = "US",
        val visitorData: String? = null,
        val platform: String? = null,
    )

    @Serializable
    data class ThirdParty(
        val embedUrl: String,
    )
}
