package com.lostf1sh.pixelplayerytm.data.stream

import com.squareup.duktape.Duktape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves YouTube stream URLs that require signature descrambling and/or the
 * `n` throttling-parameter transform, by executing the relevant functions from
 * the player `base.js` inside a Duktape JS engine.
 *
 * Streams from ANDROID_MUSIC are usually plain URLs and skip this entirely;
 * WEB_REMIX / TVHTML5 fall back through here.
 */
@Singleton
class YouTubeCipherManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private data class PlayerFunctions(
        val playerUrl: String,
        val extraction: PlayerBaseJsExtractor.Extraction,
    )

    @Volatile
    private var cached: PlayerFunctions? = null
    private val mutex = Mutex()

    val signatureTimestamp: Int?
        get() = cached?.extraction?.signatureTimestamp

    fun playerUrlOrThrow(): String =
        cached?.playerUrl ?: error("Player base.js not prepared yet")

    /** Ensures base.js is fetched and parsed. Returns the signatureTimestamp. */
    suspend fun prepare(playerJsUrl: String? = null): Int? {
        val url = playerJsUrl ?: fetchPlayerUrl()
        ensureFunctions(url)
        return cached?.extraction?.signatureTimestamp
    }

    private suspend fun ensureFunctions(playerUrl: String): PlayerFunctions {
        cached?.let { if (it.playerUrl == playerUrl) return it }
        return mutex.withLock {
            cached?.let { if (it.playerUrl == playerUrl) return it }
            val baseJs = downloadText(playerUrl)
            val extraction = PlayerBaseJsExtractor.extract(baseJs)
            PlayerFunctions(playerUrl, extraction).also { cached = it }
        }
    }

    /**
     * Takes a `signatureCipher` query string (with s, sp, url) and returns a
     * fully playable URL with the descrambled signature and transformed n param.
     */
    suspend fun resolveSignatureCipher(signatureCipher: String, playerUrl: String): String {
        val functions = ensureFunctions(playerUrl)
        val params = parseQuery(signatureCipher)
        val scrambled = params["s"] ?: error("signatureCipher missing s")
        val sigParam = params["sp"] ?: "signature"
        val baseUrl = params["url"] ?: error("signatureCipher missing url")

        val signature = runJs(functions.extraction.sigFunctionCode, functions.extraction.sigFunctionName, scrambled)
        val withSig = appendQuery(baseUrl, sigParam, signature)
        return transformNParam(withSig, functions)
    }

    /** Applies only the `n` param transform to an already-signed URL. */
    suspend fun resolveNParam(url: String, playerUrl: String): String {
        val functions = ensureFunctions(playerUrl)
        return transformNParam(url, functions)
    }

    private suspend fun transformNParam(url: String, functions: PlayerFunctions): String {
        val n = parseQuery(url.substringAfter('?', ""))["n"]
            ?: Regex("""[?&]n=([^&]+)""").find(url)?.groupValues?.get(1)
            ?: return url
        val decodedN = URLDecoder.decode(n, "UTF-8")
        val transformed = runJs(functions.extraction.nFunctionCode, functions.extraction.nFunctionName, decodedN)
        if (transformed.isEmpty() || transformed.startsWith("enhanced_except")) return url
        return replaceQuery(url, "n", transformed)
    }

    private suspend fun runJs(functionCode: String, functionName: String, input: String): String =
        withContext(Dispatchers.Default) {
            val duktape = Duktape.create()
            try {
                val escaped = input.replace("\\", "\\\\").replace("'", "\\'")
                duktape.evaluate("$functionCode; var __result = $functionName('$escaped');")
                duktape.evaluate("__result").toString()
            } finally {
                duktape.close()
            }
        }

    private suspend fun fetchPlayerUrl(): String {
        val html = downloadText("https://music.youtube.com/")
        val path = Regex("""/s/player/[a-zA-Z0-9_/.-]+/base\.js""").find(html)?.value
            ?: Regex(""""jsUrl":"([^"]+base\.js)"""").find(html)?.groupValues?.get(1)
            ?: error("Could not locate player base.js url")
        return if (path.startsWith("http")) path else "https://music.youtube.com$path"
    }

    private suspend fun downloadText(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Failed to download $url: HTTP ${response.code}")
            response.body.string()
        }
    }

    // ---------- URL helpers ----------

    private fun parseQuery(query: String): Map<String, String> =
        query.split('&').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) return@mapNotNull null
            val key = pair.substring(0, idx)
            val value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name())
            key to value
        }.toMap()

    private fun appendQuery(url: String, key: String, value: String): String {
        val separator = if (url.contains('?')) '&' else '?'
        return "$url$separator$key=${java.net.URLEncoder.encode(value, "UTF-8")}"
    }

    private fun replaceQuery(url: String, key: String, value: String): String {
        val encoded = java.net.URLEncoder.encode(value, "UTF-8")
        val regex = Regex("""([?&])$key=[^&]*""")
        return if (regex.containsMatchIn(url)) {
            regex.replace(url) { "${it.groupValues[1]}$key=$encoded" }
        } else {
            appendQuery(url, key, value)
        }
    }
}
