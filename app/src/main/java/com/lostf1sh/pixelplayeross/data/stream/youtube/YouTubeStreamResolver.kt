package com.lostf1sh.pixelplayeross.data.stream.youtube

import com.lostf1sh.pixelplayeross.data.network.youtube.InnerTubeClientId
import com.lostf1sh.pixelplayeross.data.network.youtube.InnerTubeService
import com.lostf1sh.pixelplayeross.data.network.youtube.YtVisitorStore
import com.lostf1sh.pixelplayeross.data.network.youtube.auth.YtAccountStore
import com.lostf1sh.pixelplayeross.data.stream.youtube.potoken.PoTokenGenerator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** A playable audio stream URL plus the metadata the proxy needs to serve/expire it. */
data class ResolvedStream(
    val url: String,
    val bitrate: Long,
    val mimeType: String,
    val expiresAtEpochMs: Long,
)

class StreamResolutionException(message: String) : Exception(message)

/**
 * Resolves a videoId to a single, progressive, directly-streamable audio URL.
 *
 * The signed-in WEB_REMIX client returns music streams as signatureCipher formats, which
 * [NewPipeStreamHelper] descrambles (signature + `n` param) against the live base.js. A
 * short-lived cache avoids re-hitting `player` for tracks played back-to-back (URLs are
 * ~6h-lived; we expire a minute early).
 */
@Singleton
class YouTubeStreamResolver @Inject constructor(
    private val innerTube: InnerTubeService,
    private val newPipe: NewPipeStreamHelper,
    private val accountStore: YtAccountStore,
    private val visitorStore: YtVisitorStore,
    private val poTokenGenerator: PoTokenGenerator,
    private val formatStore: YtStreamFormatStore,
    private val json: Json,
) {
    private val cache = LinkedHashMap<String, ResolvedStream>()
    private val cacheMutex = Mutex()

    suspend fun resolve(videoId: String): ResolvedStream {
        cacheMutex.withLock {
            cache[videoId]?.let { cached ->
                if (cached.expiresAtEpochMs > now() + 60_000) return cached
                cache.remove(videoId)
            }
        }

        var lastError: String? = null
        for (client in CLIENT_ORDER) {
            try {
                val stream = resolveWith(videoId, client) ?: continue
                cacheMutex.withLock {
                    cache[videoId] = stream
                    if (cache.size > CACHE_LIMIT) cache.remove(cache.keys.first())
                }
                return stream
            } catch (e: Exception) {
                lastError = "${client.clientName}: ${e.message}"
                Timber.tag(TAG).d("resolve %s via %s failed: %s", videoId, client.clientName, e.message)
            }
        }
        throw StreamResolutionException("no stream for $videoId ($lastError)")
    }

    private suspend fun resolveWith(videoId: String, client: InnerTubeClientId): ResolvedStream? {
        // WEB_REMIX hands back signatureCipher formats keyed to base.js; echo its
        // signatureTimestamp so the formats match the descrambler NewPipe will run.
        val signatureTimestamp =
            if (client.useSignatureTimestamp) newPipe.signatureTimestamp(videoId) else null

        // A BotGuard PoToken bound to the current visitor session. The player-request half
        // goes in the request body; the streaming half is appended to the stream URL. Without
        // it, googlevideo `svpuc`-throttles the stream to ~1 MB and playback skips. Best-effort:
        // null (no WebView / timeout) just yields the throttled stream rather than failing.
        val sessionId = visitorStore.current
        val poToken = if (client.useWebPoTokens && sessionId != null) {
            poTokenGenerator.getWebClientPoToken(videoId, sessionId)
        } else {
            null
        }
        // A web client without a pot only yields `svpuc`-throttled (~1 MB) streams, so don't
        // waste it — bail to the next client (WEB_CREATOR, then the no-pot ANDROID_VR).
        if (client.useWebPoTokens && poToken == null) {
            throw StreamResolutionException("no PoToken for ${client.clientName}")
        }

        val root = innerTube.call("player", client) {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            if (signatureTimestamp != null) {
                putJsonObject("playbackContext") {
                    putJsonObject("contentPlaybackContext") {
                        put("signatureTimestamp", signatureTimestamp)
                    }
                }
            }
            if (poToken != null) {
                putJsonObject("serviceIntegrityDimensions") {
                    put("poToken", poToken.playerRequestPoToken)
                }
            }
        }
        val response = json.decodeFromJsonElement(PlayerResponse.serializer(), root)
        if (!response.isPlayable) {
            throw StreamResolutionException(response.playabilityStatus?.reason ?: "not playable")
        }

        val format = bestAudioFormat(response) ?: return null
        // Pin the chosen encoding for the disk cache (evicts stale spans if it changed).
        format.itag?.let { formatStore.record(videoId, it) }
        val url = newPipe.resolveStreamUrl(videoId, format, poToken?.streamingDataPoToken)
            ?: throw StreamResolutionException("could not descramble stream url")

        val expiresInSeconds = response.streamingData?.expiresInSeconds?.toLongOrNull() ?: 21_600L
        return ResolvedStream(
            url = url,
            bitrate = format.effectiveBitrate,
            mimeType = format.mimeType ?: "audio/mp4",
            expiresAtEpochMs = now() + expiresInSeconds * 1000L,
        )
    }

    private fun bestAudioFormat(response: PlayerResponse): PlayerResponse.StreamingData.Format? {
        val streaming = response.streamingData ?: return null
        val audio = (streaming.adaptiveFormats + streaming.formats)
            .filter { it.isAudio && it.hasSomeSource }
            .ifEmpty { streaming.formats.filter { it.hasSomeSource } }
        if (audio.isEmpty()) return null

        // Premium accounts can pull higher bitrates; anonymous playback caps at ~160 kbps.
        val ceiling = if (accountStore.isSignedIn.value) Long.MAX_VALUE else 160_000L
        val byBitrateDesc = audio.sortedByDescending { it.effectiveBitrate }
        return byBitrateDesc.firstOrNull { it.effectiveBitrate <= ceiling } ?: byBitrateDesc.last()
    }

    private fun now(): Long = System.currentTimeMillis()

    private companion object {
        const val TAG = "YtStreamResolver"
        const val CACHE_LIMIT = 60
        // WEB_REMIX + BotGuard PoToken is the reliable primary path. WEB_CREATOR (also pot)
        // covers some age-restricted tracks; ANDROID_VR is the no-pot anonymous last resort
        // for when the WebView pot engine is unavailable. (Plain IOS is omitted: no pot means
        // googlevideo `svpuc`-caps it at ~1 MB — worse than useless.)
        val CLIENT_ORDER = listOf(
            InnerTubeClientId.WEB_REMIX,
            InnerTubeClientId.WEB_CREATOR,
            InnerTubeClientId.ANDROID_VR,
        )
    }
}
