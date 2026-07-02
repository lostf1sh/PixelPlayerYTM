package com.lostf1sh.pixelplayeross.data.stream.youtube

import android.net.Uri
import com.lostf1sh.pixelplayeross.data.network.youtube.InnerTubeClientId
import com.lostf1sh.pixelplayeross.data.stream.CloudStreamProxy
import com.lostf1sh.pixelplayeross.data.stream.CloudStreamSecurity
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loopback proxy for YouTube Music audio. Resolves `ytm://<videoId>` URIs to a fresh,
 * descrambled googlevideo stream URL (via [YouTubeStreamResolver]) and streams the bytes
 * to ExoPlayer through the shared [CloudStreamProxy] machinery — Range passthrough,
 * per-session token, and an SSRF allowlist locked to `*.googlevideo.com`.
 *
 * The `ytm://` scheme puts the 11-char videoId in the URI host.
 */
@Singleton
class YouTubeStreamProxy @Inject constructor(
    private val resolver: YouTubeStreamResolver,
    private val formatStore: YtStreamFormatStore,
    okHttpClient: OkHttpClient,
) : CloudStreamProxy<String>(okHttpClient) {

    override val allowedHostSuffixes: Set<String> = setOf("googlevideo.com")

    // googlevideo URLs carry an `expire` param (~6h). Cache comfortably under that so a
    // proxied URL never outlives its signature; the resolver applies its own tighter TTL.
    override val cacheExpirationMs: Long = 5L * 60 * 60 * 1000

    override val proxyTag: String = "YouTubeStreamProxy"
    override val routePath: String = "/ytm/{videoId}"
    override val routeParamName: String = "videoId"
    override val uriScheme: String = "ytm"
    override val routePrefix: String = "/ytm"

    /**
     * googlevideo stream URLs are minted for a specific InnerTube client (`c`/`cver`).
     * The media byte request must look like that same client or YouTube returns 403,
     * which ExoPlayer interprets as an item failure and skips to the next track.
     */
    override fun upstreamHeaders(streamUrl: String): Map<String, String> {
        val uri = Uri.parse(streamUrl)
        val clientName = uri.getQueryParameter("c").orEmpty().uppercase()
        val client = InnerTubeClientId.entries.firstOrNull { it.clientName == clientName }
            ?: InnerTubeClientId.IOS

        return buildMap {
            put("User-Agent", client.userAgent)
            client.referer?.let { referer ->
                put("Referer", referer)
                put("Origin", referer.trimEnd('/'))
            }
        }
    }

    /**
     * iOS googlevideo `svpuc` streams reject an open-ended `bytes=N-` range (and a full
     * GET). Bound only those requests to the object's last byte via `clen`. Web/TV
     * fallback URLs may carry different authorization semantics, so leave their Range
     * header as ExoPlayer sent it.
     */
    override fun resolveUpstreamRange(streamUrl: String, clientRange: String?): String? {
        if (!requiresClosedRange(streamUrl)) return clientRange

        val match = RANGE.find(clientRange ?: "bytes=0-")
        val start = match?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val explicitEnd = match?.groupValues?.getOrNull(2)?.toLongOrNull()
        val end = explicitEnd
            ?: Uri.parse(streamUrl).getQueryParameter("clen")?.toLongOrNull()?.let { it - 1 }
            ?: (start + FALLBACK_SPAN_BYTES - 1)
        return "bytes=$start-$end"
    }

    private fun requiresClosedRange(streamUrl: String): Boolean {
        val uri = Uri.parse(streamUrl)
        val clientName = uri.getQueryParameter("c").orEmpty().uppercase()
        val hasSvpuc = uri.getQueryParameter("svpuc") != null
        return hasSvpuc || clientName == InnerTubeClientId.IOS.clientName
    }

    /**
     * The last-known audio itag rides on the proxy URL so the player's disk cache can key
     * spans by (videoId, itag) — see [YtStreamFormatStore]. Absent until the first resolve
     * of a video; the data source layer then bypasses the disk cache for that open.
     */
    override fun proxyUrlExtraParams(id: String): String? =
        formatStore.itagFor(id)?.let { "itag=$it" }

    override fun parseRouteParam(value: String): String? = value.takeIf { it.isNotBlank() }

    private companion object {
        val RANGE = Regex("""bytes=(\d+)-(\d*)""")

        /** Only used if a URL somehow lacks `clen`; ample for one audio track. */
        const val FALLBACK_SPAN_BYTES = 16L * 1024 * 1024
    }

    override fun validateId(id: String): Boolean = CloudStreamSecurity.validateYouTubeVideoId(id)

    override fun formatIdForUrl(id: String): String = id

    override suspend fun resolveStreamUrl(id: String): String? = try {
        resolver.resolve(id).url
    } catch (e: Exception) {
        Timber.tag(proxyTag).w(e, "failed to resolve stream URL for %s", id)
        null
    }

    override fun extractIdFromUri(uri: Uri): String? = uri.host ?: uri.path?.removePrefix("/")

    /** Map a `ytm://<videoId>` URI to the local proxy URL, or null if not ours. */
    fun resolveYouTubeUri(uriString: String): String? = resolveUri(uriString)

    /** Pre-fetch and cache the stream URL so ExoPlayer's first byte-range request is served instantly. */
    suspend fun warmUpStreamUrl(uriString: String) {
        val uri = Uri.parse(uriString)
        if (uri.scheme != uriScheme) return
        val videoId = extractIdFromUri(uri) ?: return
        if (!validateId(videoId)) return
        try {
            getOrFetchStreamUrl(videoId)
        } catch (e: Exception) {
            Timber.tag(proxyTag).w(e, "warmUpStreamUrl failed for %s", videoId)
        }
    }
}
