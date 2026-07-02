package com.lostf1sh.pixelplayeross.data.stream.youtube

import com.lostf1sh.pixelplayeross.di.YouTubeBaseHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.CancellableCall
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import timber.log.Timber
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Signature/`n`-param descrambling via NewPipeExtractor's [YoutubeJavaScriptPlayerManager],
 * which fetches and interprets the player `base.js` itself and is kept current against
 * Google's frequent obfuscation changes. This replaces the hand-rolled Duktape parser,
 * which broke every time `base.js` was reshaped.
 *
 * NewPipe is initialised lazily with an OkHttp-backed [Downloader] sharing the app pool.
 */
@Singleton
class NewPipeStreamHelper @Inject constructor(
    @YouTubeBaseHttp private val okHttpClient: OkHttpClient,
) {
    @Volatile
    private var initialized = false

    private fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            NewPipe.init(OkHttpDownloader(okHttpClient))
            initialized = true
        }
    }

    /** The `signatureTimestamp` (`sts`) the current base.js expects, echoed back in `player`. */
    suspend fun signatureTimestamp(videoId: String): Int? = withContext(Dispatchers.IO) {
        ensureInitialized()
        runCatching { YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId) }
            .onFailure { Timber.tag(TAG).w(it, "signatureTimestamp failed for %s", videoId) }
            .getOrNull()
    }

    /**
     * Turn one streaming [format] into a directly playable URL: descramble the
     * `signatureCipher` (WEB_REMIX) if present, run the `n` throttling param through
     * base.js, and finally append the streaming-data [poToken] as `&pot=` so googlevideo
     * serves the full track instead of `svpuc`-throttling it to ~1 MB.
     */
    suspend fun resolveStreamUrl(
        videoId: String,
        format: PlayerResponse.StreamingData.Format,
        poToken: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        ensureInitialized()
        runCatching {
            val ciphered = format.url ?: run {
                val cipher = format.signatureCipher ?: format.cipher
                    ?: error("format has neither url nor signatureCipher")
                val params = parseQuery(cipher)
                val scrambled = params["s"] ?: error("signatureCipher missing 's'")
                val sigParam = params["sp"] ?: "signature"
                val baseUrl = params["url"] ?: error("signatureCipher missing 'url'")
                val solved = YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, scrambled)
                appendParam(baseUrl, sigParam, solved)
            }
            var url = deobfuscateNParam(videoId, ciphered)
            if (!poToken.isNullOrBlank()) url = appendParam(url, "pot", poToken)
            url
        }.onFailure {
            Timber.tag(TAG).w(it, "resolveStreamUrl failed for %s", videoId)
        }.getOrNull()
    }

    private fun deobfuscateNParam(videoId: String, url: String): String =
        runCatching {
            YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, url)
        }.getOrElse { url }

    private fun parseQuery(query: String): Map<String, String> =
        query.split('&').mapNotNull { pair ->
            val i = pair.indexOf('=')
            if (i <= 0) null
            else pair.substring(0, i) to URLDecoder.decode(pair.substring(i + 1), "UTF-8")
        }.toMap()

    private fun appendParam(url: String, key: String, value: String): String {
        val sep = if (url.contains('?')) '&' else '?'
        return "$url$sep$key=${java.net.URLEncoder.encode(value, "UTF-8")}"
    }

    /** Adapts NewPipe's [Downloader] contract onto the shared OkHttp client. */
    private class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {
        override fun execute(request: Request): Response {
            val body = request.dataToSend()?.toRequestBody()
            val builder = okhttp3.Request.Builder()
                .method(request.httpMethod(), body)
                .url(request.url())
            var hasUserAgent = false
            request.headers().forEach { (name, values) ->
                if (name.equals("User-Agent", ignoreCase = true) && values.isNotEmpty()) hasUserAgent = true
                builder.removeHeader(name)
                values.forEach { builder.addHeader(name, it) }
            }
            if (!hasUserAgent) builder.header("User-Agent", DEFAULT_USER_AGENT)

            client.newCall(builder.build()).execute().use { response ->
                if (response.code == 429) {
                    throw ReCaptchaException("reCaptcha Challenge requested", request.url())
                }
                val bodyString = response.body?.string()
                return Response(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    bodyString,
                    bodyString?.toByteArray(),
                    response.request.url.toString(),
                )
            }
        }

        /** The fork requires this; we only ever call [execute], so run it inline. */
        override fun executeAsync(
            request: Request,
            callback: Downloader.AsyncCallback,
        ): CancellableCall {
            val okCall = client.newCall(okhttp3.Request.Builder().url(request.url()).build())
            try {
                callback.onSuccess(execute(request))
            } catch (e: Exception) {
                callback.onError(e)
            }
            return CancellableCall(okCall)
        }
    }

    private companion object {
        const val TAG = "NewPipeStreamHelper"
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
