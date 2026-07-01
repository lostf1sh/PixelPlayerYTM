package com.lostf1sh.pixelplayeross.data.youtube.stream

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
 * Resolves playable audio URLs from YouTube's `player` formats, handling the two things that
 * make YouTube streaming hard:
 *
 *  1. **signatureCipher** — some formats ship an obfuscated signature that must be run
 *     through a transform function defined in YouTube's `base.js`.
 *  2. **the `n` parameter** — every stream URL carries an `n` throttling param that must be
 *     transformed by another `base.js` function, or downloads are throttled to a crawl / 403.
 *
 * Both transforms are extracted from `base.js` with regexes (the proven NewPipe/yt-dlp
 * patterns) and executed in an embedded Duktape JS engine. Extraction is cached per player
 * version. Everything degrades gracefully: if extraction fails we return the best URL we can
 * (plain URLs from the mobile client often still play), rather than throwing.
 */
@Singleton
class YouTubeCipherManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private class Cipher(
        val playerId: String,
        val signatureFunction: String?,
        val nFunction: String?,
    )

    private val mutex = Mutex()
    @Volatile
    private var cached: Cipher? = null

    /** Turn a chosen player format into a fully playable URL (signature + n applied). */
    suspend fun buildPlayableUrl(url: String?, signatureCipher: String?): String? {
        val cipher = runCatching { getCipher() }.getOrNull()

        val baseUrl: String = when {
            !url.isNullOrBlank() -> url
            !signatureCipher.isNullOrBlank() -> {
                val params = parseQueryString(signatureCipher)
                val streamUrl = params["url"] ?: return null
                val s = params["s"]
                if (s == null) {
                    streamUrl
                } else {
                    val sp = params["sp"] ?: "signature"
                    val decoded = URLDecoder.decode(s, "UTF-8")
                    val sig = cipher?.signatureFunction?.let { runSignature(it, decoded) }
                        ?: return null // Can't play a ciphered stream without the transform.
                    "$streamUrl&$sp=${URLEncoder.encode(sig, "UTF-8")}"
                }
            }
            else -> return null
        }

        return applyNParameter(baseUrl, cipher)
    }

    private fun applyNParameter(url: String, cipher: Cipher?): String {
        val nFunction = cipher?.nFunction ?: return url
        val n = extractQueryParam(url, "n") ?: return url
        val transformed = runCatching { runN(nFunction, URLDecoder.decode(n, "UTF-8")) }
            .getOrNull()?.takeIf { it.isNotBlank() && it != "enhanced_except" } ?: return url
        return replaceQueryParam(url, "n", URLEncoder.encode(transformed, "UTF-8"))
    }

    // ─── base.js extraction ────────────────────────────────────────────

    private suspend fun getCipher(): Cipher = mutex.withLock {
        val playerId = fetchPlayerId()
        cached?.takeIf { it.playerId == playerId }?.let { return it }

        val baseJs = fetchBaseJs(playerId)
        val signature = runCatching { extractSignatureFunction(baseJs) }
            .onFailure { Timber.w(it, "signature function extraction failed") }.getOrNull()
        val nFunc = runCatching { extractNFunction(baseJs) }
            .onFailure { Timber.w(it, "n function extraction failed") }.getOrNull()

        Cipher(playerId, signature, nFunc).also { cached = it }
    }

    private suspend fun fetchPlayerId(): String = withContext(Dispatchers.IO) {
        val body = get("https://www.youtube.com/iframe_api")
        PLAYER_ID_REGEX.find(body)?.groupValues?.get(1)
            ?: error("Could not locate player id in iframe_api")
    }

    private suspend fun fetchBaseJs(playerId: String): String = withContext(Dispatchers.IO) {
        get("https://www.youtube.com/s/player/$playerId/player_ias.vflset/en_US/base.js")
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url)
            .header("User-Agent", DESKTOP_UA)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} for $url")
            return response.body?.string().orEmpty()
        }
    }

    /**
     * Extract the signature transform: a `foo=function(a){a=a.split("");Bar.xx(a,1);...
     * return a.join("")}` plus the helper object `var Bar={...}` it calls.
     */
    private fun extractSignatureFunction(baseJs: String): String {
        val funcName = SIGNATURE_NAME_REGEXES.firstNotNullOfOrNull { it.find(baseJs)?.groupValues?.get(1) }
            ?: error("no signature function name")
        val escaped = Regex.escape(funcName)
        val funcBody = Regex("(?:function\\s+$escaped|$escaped\\s*=\\s*function)\\s*\\(([\\w$]+)\\)\\{(.+?)\\}", RegexOption.DOT_MATCHES_ALL)
            .find(baseJs) ?: error("no signature function body")
        val arg = funcBody.groupValues[1]
        val body = funcBody.groupValues[2]

        // Helper object referenced inside the body, e.g. "Bar" in "Bar.xx(a,1)".
        val helperName = Regex(";([\\w$]+)\\.[\\w$]+\\(").find(body)?.groupValues?.get(1)
        val helperDef = helperName?.let {
            val he = Regex.escape(it)
            Regex("var $he=\\{(.+?)\\};", RegexOption.DOT_MATCHES_ALL).find(baseJs)?.value
        }.orEmpty()

        return "$helperDef function __sig($arg){$body} __sig"
    }

    /** Extract the `n` throttling transform function. */
    private fun extractNFunction(baseJs: String): String {
        // Capture the referenced name and, when present, its array index (group 2).
        val match = N_NAME_REGEXES.firstNotNullOfOrNull { regex ->
            regex.find(baseJs)?.takeIf { it.groupValues.getOrNull(1).orEmpty().isNotBlank() }
        } ?: error("no n function name")
        val name = match.groupValues[1]
        val index = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }
        val funcName = if (index != null) "$name[$index]" else name

        // The name may reference an array element: var arr=[foo,bar]; ... arr[0](n)
        val realName = resolveArrayReference(baseJs, funcName) ?: funcName
        val escaped = Regex.escape(realName)
        val funcBody = Regex("(?:function\\s+$escaped|$escaped\\s*=\\s*function)\\s*\\(([\\w$]+)\\)\\{(.+?return [\\w$]+\\.join\\(\"\"\\)\\})", RegexOption.DOT_MATCHES_ALL)
            .find(baseJs)
            ?: Regex("(?:function\\s+$escaped|$escaped\\s*=\\s*function)\\s*\\(([\\w$]+)\\)\\{(.+?\\})", RegexOption.DOT_MATCHES_ALL).find(baseJs)
            ?: error("no n function body")
        val arg = funcBody.groupValues[1]
        val body = funcBody.groupValues[2]
        return "function __n($arg){$body} __n"
    }

    private fun resolveArrayReference(baseJs: String, name: String): String? {
        // name like "Xy[0]" — resolve to the actual function name in the array literal.
        val m = Regex("([\\w$]+)\\[(\\d+)\\]").matchEntire(name) ?: return null
        val arrayName = Regex.escape(m.groupValues[1])
        val index = m.groupValues[2].toInt()
        val arrayLiteral = Regex("var $arrayName=\\[(.+?)\\]", RegexOption.DOT_MATCHES_ALL)
            .find(baseJs)?.groupValues?.get(1) ?: return null
        return arrayLiteral.split(",").getOrNull(index)?.trim()
    }

    // ─── Duktape execution ─────────────────────────────────────────────

    private fun runSignature(functionSource: String, input: String): String? = runJs(functionSource, input)
    private fun runN(functionSource: String, input: String): String? = runJs(functionSource, input)

    private fun runJs(functionSource: String, input: String): String? {
        return runCatching {
            val duktape = Duktape.create()
            try {
                // Define the extracted function and immediately call it with the input.
                val script = "var __f=($functionSource); __f(${jsString(input)});"
                duktape.evaluate(script) as? String
            } finally {
                duktape.close()
            }
        }.getOrElse {
            Timber.w(it, "JS execution failed")
            null
        }
    }

    // ─── URL / query helpers ───────────────────────────────────────────

    private fun parseQueryString(query: String): Map<String, String> =
        query.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) null else {
                val key = pair.substring(0, idx)
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                key to value
            }
        }.toMap()

    private fun extractQueryParam(url: String, key: String): String? {
        val regex = Regex("[?&]${Regex.escape(key)}=([^&]+)")
        return regex.find(url)?.groupValues?.get(1)
    }

    private fun replaceQueryParam(url: String, key: String, encodedValue: String): String {
        val regex = Regex("([?&]${Regex.escape(key)}=)[^&]+")
        return regex.replace(url, "$1$encodedValue")
    }

    private fun jsString(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private companion object {
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        val PLAYER_ID_REGEX = Regex("player\\\\?/([0-9a-fA-F]{8})\\\\?/")

        val SIGNATURE_NAME_REGEXES = listOf(
            Regex("\\bm=([a-zA-Z0-9$]{2,})\\(decodeURIComponent\\(h\\.s\\)\\)"),
            Regex("\\bc&&\\(c=([a-zA-Z0-9$]{2,})\\(decodeURIComponent\\(c\\)\\)"),
            Regex("(?:\\b|[^a-zA-Z0-9$])([a-zA-Z0-9$]{2,})\\s*=\\s*function\\(\\s*a\\s*\\)\\s*\\{\\s*a\\s*=\\s*a\\.split\\(\\s*\"\"\\s*\\)"),
            Regex("([\\w$]+)\\s*=\\s*function\\((\\w+)\\)\\{\\s*\\w+=\\w+\\.split\\(\"\"\\);"),
        )

        val N_NAME_REGEXES = listOf(
            Regex("\\.get\\(\"n\"\\)\\)&&\\(b=([a-zA-Z0-9$]+)(?:\\[(\\d+)\\])?\\([a-zA-Z0-9]\\)"),
            Regex("\\(b=String\\.fromCharCode\\(110\\),c=a\\.get\\(b\\)\\)&&\\(c=([a-zA-Z0-9$]+)(?:\\[(\\d+)\\])?\\(c\\)"),
            Regex("([a-zA-Z0-9$]+)&&\\(b=a\\.get\\(\"n\"\\)\\)&&\\(b=([a-zA-Z0-9$]+)(?:\\[(\\d+)\\])?\\(b\\)"),
        )
    }
}
