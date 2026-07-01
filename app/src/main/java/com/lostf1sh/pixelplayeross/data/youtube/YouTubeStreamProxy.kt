package com.lostf1sh.pixelplayeross.data.youtube

import android.net.Uri
import com.lostf1sh.pixelplayeross.data.repository.YouTubeMusicRepository
import com.lostf1sh.pixelplayeross.data.stream.CloudStreamProxy
import com.lostf1sh.pixelplayeross.data.stream.CloudStreamSecurity
import okhttp3.OkHttpClient
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local loopback proxy for YouTube Music audio. Resolves `ytm://<videoId>` URIs by asking
 * [YouTubeMusicRepository] for a fresh googlevideo stream URL, then streams the bytes to
 * ExoPlayer through the shared [CloudStreamProxy] machinery (Range passthrough, per-session
 * token, SSRF allowlist). Only `*.googlevideo.com` upstreams are permitted.
 */
@Singleton
class YouTubeStreamProxy @Inject constructor(
    private val repository: YouTubeMusicRepository,
    okHttpClient: OkHttpClient,
) : CloudStreamProxy<String>(okHttpClient) {

    override val allowedHostSuffixes: Set<String> = setOf("googlevideo.com")

    // googlevideo URLs carry an `expire` param (~6h). Cache comfortably under that so a
    // resolved URL never outlives its signature.
    override val cacheExpirationMs = 5L * 60 * 60 * 1000

    override val proxyTag = "YouTubeStreamProxy"
    override val routePath = "/ytm/{videoId}"
    override val routeParamName = "videoId"
    override val uriScheme = "ytm"
    override val routePrefix = "/ytm"

    override fun parseRouteParam(value: String): String? = value.takeIf { it.isNotBlank() }

    override fun validateId(id: String): Boolean = CloudStreamSecurity.validateYouTubeVideoId(id)

    override fun formatIdForUrl(id: String): String = id

    override suspend fun resolveStreamUrl(id: String): String? {
        return try {
            repository.resolveStreamUrl(id)
        } catch (e: Exception) {
            Timber.w(e, "YouTubeStreamProxy: failed to resolve stream URL for %s", id)
            null
        }
    }

    // ytm://<videoId> — the id lives in the URI host.
    override fun extractIdFromUri(uri: Uri): String? = uri.host ?: uri.path?.removePrefix("/")

    fun resolveYouTubeUri(uriString: String): String? = resolveUri(uriString)

    /** Pre-fetch and cache the stream URL so ExoPlayer's first request is served instantly. */
    suspend fun warmUpStreamUrl(uriString: String) {
        val uri = Uri.parse(uriString)
        if (uri.scheme != uriScheme) return
        val videoId = uri.host ?: uri.path?.removePrefix("/") ?: return
        if (!CloudStreamSecurity.validateYouTubeVideoId(videoId)) return
        try {
            getOrFetchStreamUrl(videoId)
        } catch (e: Exception) {
            Timber.w(e, "YouTubeStreamProxy: warmUpStreamUrl failed for %s", videoId)
        }
    }
}
