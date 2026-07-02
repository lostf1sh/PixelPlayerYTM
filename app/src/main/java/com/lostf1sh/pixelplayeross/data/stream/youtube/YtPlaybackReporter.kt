package com.lostf1sh.pixelplayeross.data.stream.youtube

import com.lostf1sh.pixelplayeross.di.YouTubeHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Sends YouTube's `videostats` playback ping when a YTM track actually starts playing —
 * that ping is what writes the play into the account's YouTube (Music) history, so
 * "Listen again", Quick picks and recommendations learn from plays made in this app.
 *
 * The ping URL comes from the `player` response (`playbackTracking.videostatsPlaybackUrl`),
 * captured by the stream resolver. Resolution and playback start are deliberately
 * decoupled — the engine pre-resolves neighbouring queue items that may never play, so
 * pinging at resolve time would fabricate history. Instead the player engine calls
 * [onTrackStarted] on every media-item transition:
 *  - URL already recorded → ping immediately (replays, pre-resolved tracks);
 *  - not yet (first play: the resolve races the transition) → arm a single pending slot
 *    that the matching [record] satisfies. The slot holds only the latest transition, so
 *    a track skipped away from before its resolve finishes is never reported.
 */
@Singleton
class YtPlaybackReporter @Inject constructor(
    @YouTubeHttp private val httpClient: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val lock = Any()
    private val urls = object : LinkedHashMap<String, String>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>) =
            size > URL_CACHE_LIMIT
    }
    private var pendingVideoId: String? = null

    /** Remember the ping URL for [videoId]; called by the resolver per `player` response. */
    fun record(videoId: String, playbackUrl: String) {
        val firePending: Boolean
        synchronized(lock) {
            urls[videoId] = playbackUrl
            firePending = pendingVideoId == videoId
            if (firePending) pendingVideoId = null
        }
        if (firePending) fire(videoId, playbackUrl)
    }

    /** Report a play of [videoId] (now, or as soon as its resolve delivers the URL). */
    fun onTrackStarted(videoId: String) {
        val url: String?
        synchronized(lock) {
            url = urls[videoId]
            pendingVideoId = if (url == null) videoId else null
        }
        if (url != null) fire(videoId, url)
    }

    private fun fire(videoId: String, baseUrl: String) {
        scope.launch {
            runCatching {
                val url = baseUrl.toHttpUrl().newBuilder()
                    .setQueryParameter("ver", "2")
                    .setQueryParameter("c", "WEB_REMIX")
                    .setQueryParameter("cpn", randomCpn())
                    .build()
                val request = Request.Builder()
                    .url(url)
                    // The auth interceptor keys off this header to attach the cookie +
                    // SAPISIDHASH; the stats host itself ignores it. Without auth the
                    // ping is anonymous (feeds the visitor profile only), same as web.
                    .header("X-YouTube-Client-Name", "67")
                    .header("Origin", ORIGIN)
                    .header("Referer", "$ORIGIN/")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    Timber.tag(TAG).d("playback ping %s -> HTTP %d", videoId, response.code)
                }
            }.onFailure { Timber.tag(TAG).d(it, "playback ping failed for %s", videoId) }
        }
    }

    /** Client playback nonce: 16 chars of the base64url alphabet, fresh per play. */
    private fun randomCpn(): String = buildString(CPN_LENGTH) {
        repeat(CPN_LENGTH) { append(CPN_ALPHABET[Random.nextInt(CPN_ALPHABET.length)]) }
    }

    private companion object {
        const val TAG = "YtPlaybackReporter"
        const val ORIGIN = "https://music.youtube.com"
        const val URL_CACHE_LIMIT = 100
        const val CPN_LENGTH = 16
        const val CPN_ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
    }
}
