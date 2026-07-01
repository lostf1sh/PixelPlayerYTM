package com.lostf1sh.pixelplayeross.data.stream.youtube

import com.lostf1sh.pixelplayeross.di.YouTubeBaseHttp
import com.squareup.duktape.Duktape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Descrambles YouTube stream URLs by executing the signature and `n`-param functions
 * pulled out of the player `base.js` (via [PlayerScriptParser]) inside a Duktape JS VM.
 *
 * `base.js` is fetched once per player version and the parsed functions cached; the
 * signatureTimestamp it yields is fed back into the `player` request so the returned
 * formats match the descrambler we hold. ANDROID_MUSIC streams are plain URLs and never
 * reach here; WEB_REMIX / TVHTML5 do.
 */
@Singleton
class PlayerCipher @Inject constructor(
    @YouTubeBaseHttp private val httpClient: OkHttpClient,
) {
    private data class Prepared(val playerUrl: String, val functions: PlayerScriptParser.Functions)

    @Volatile
    private var prepared: Prepared? = null
    private val mutex = Mutex()

    /** Fetch + parse base.js if needed and return its signatureTimestamp. */
    suspend fun prepare(playerUrl: String? = null): Int? =
        ensurePrepared(playerUrl ?: fetchPlayerUrl()).functions.signatureTimestamp

    fun playerUrlOrThrow(): String =
        prepared?.playerUrl ?: error("base.js not prepared yet")

    /** Turn a `signatureCipher` blob (`s`, `sp`, `url`) into a fully playable URL. */
    suspend fun resolveSignatureCipher(signatureCipher: String, playerUrl: String): String {
        val fns = ensurePrepared(playerUrl).functions
        val params = parseQuery(signatureCipher)
        val scrambled = params["s"] ?: error("signatureCipher missing 's'")
        val sigParam = params["sp"] ?: "signature"
        val baseUrl = params["url"] ?: error("signatureCipher missing 'url'")

        val signature = runJs(fns.signatureCode, fns.signatureName, scrambled)
        return applyNParam(appendParam(baseUrl, sigParam, signature), fns)
    }

    /** Apply only the `n`-param transform to an already-signed URL. */
    suspend fun resolveNParam(url: String, playerUrl: String): String =
        applyNParam(url, ensurePrepared(playerUrl).functions)

    private suspend fun applyNParam(url: String, fns: PlayerScriptParser.Functions): String {
        val n = Regex("""[?&]n=([^&]+)""").find(url)?.groupValues?.get(1) ?: return url
        val transformed = runJs(fns.nCode, fns.nName, URLDecoder.decode(n, "UTF-8"))
        // A failed transform returns the input unchanged or an "enhanced_except…" marker;
        // in both cases keep the original URL rather than corrupt it.
        if (transformed.isEmpty() || transformed.startsWith("enhanced_except")) return url
        return replaceParam(url, "n", transformed)
    }

    private suspend fun ensurePrepared(playerUrl: String): Prepared {
        prepared?.let { if (it.playerUrl == playerUrl) return it }
        return mutex.withLock {
            prepared?.let { if (it.playerUrl == playerUrl) return it }
            val baseJs = download(playerUrl)
            Prepared(playerUrl, PlayerScriptParser.parse(baseJs)).also {
                prepared = it
                Timber.tag(TAG).d("Prepared base.js: sig=%s n=%s", it.functions.signatureName, it.functions.nName)
            }
        }
    }

    private suspend fun runJs(functionCode: String, functionName: String, input: String): String =
        withContext(Dispatchers.Default) {
            val duktape = Duktape.create()
            try {
                val escaped = input.replace("\\", "\\\\").replace("'", "\\'")
                duktape.evaluate("$functionCode; var __r = $functionName('$escaped');")
                duktape.evaluate("__r").toString()
            } finally {
                duktape.close()
            }
        }

    private suspend fun fetchPlayerUrl(): String {
        val html = download("https://music.youtube.com/")
        val path = Regex("""/s/player/[a-zA-Z0-9_/.-]+/base\.js""").find(html)?.value
            ?: Regex(""""jsUrl":"([^"]+base\.js)"""").find(html)?.groupValues?.get(1)
            ?: error("could not locate player base.js url")
        return if (path.startsWith("http")) path else "https://music.youtube.com$path"
    }

    private suspend fun download(url: String): String = withContext(Dispatchers.IO) {
        httpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) error("download failed ($url): HTTP ${response.code}")
            response.body?.string() ?: error("empty body for $url")
        }
    }

    private fun parseQuery(query: String): Map<String, String> =
        query.split('&').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) null
            else pair.substring(0, idx) to URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
        }.toMap()

    private fun appendParam(url: String, key: String, value: String): String {
        val sep = if (url.contains('?')) '&' else '?'
        return "$url$sep$key=${URLEncoder.encode(value, "UTF-8")}"
    }

    private fun replaceParam(url: String, key: String, value: String): String {
        val encoded = URLEncoder.encode(value, "UTF-8")
        val regex = Regex("""([?&])$key=[^&]*""")
        return if (regex.containsMatchIn(url)) {
            regex.replace(url) { "${it.groupValues[1]}$key=$encoded" }
        } else {
            appendParam(url, key, value)
        }
    }

    private companion object {
        const val TAG = "PlayerCipher"
    }
}
