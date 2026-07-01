package com.lostf1sh.pixelplayeross.data.stream.youtube

import android.net.Uri
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

    override fun parseRouteParam(value: String): String? = value.takeIf { it.isNotBlank() }

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
