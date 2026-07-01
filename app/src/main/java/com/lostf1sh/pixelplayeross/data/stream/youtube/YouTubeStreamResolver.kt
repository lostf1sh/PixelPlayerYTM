package com.lostf1sh.pixelplayeross.data.stream.youtube

import com.lostf1sh.pixelplayeross.data.network.youtube.InnerTubeClientId
import com.lostf1sh.pixelplayeross.data.network.youtube.InnerTubeService
import com.lostf1sh.pixelplayeross.data.network.youtube.auth.YtAccountStore
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
 * Clients are tried in reliability order: ANDROID_MUSIC first (its formats carry plain
 * URLs, so no base.js work), then WEB_REMIX and TVHTML5 (which need signature/n-param
 * descrambling via [PlayerCipher]). A short-lived cache avoids re-hitting `player` for
 * tracks played back-to-back (URLs are ~6h-lived; we expire a minute early).
 */
@Singleton
class YouTubeStreamResolver @Inject constructor(
    private val innerTube: InnerTubeService,
    private val cipher: PlayerCipher,
    private val accountStore: YtAccountStore,
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
        val needsCipher = client != InnerTubeClientId.ANDROID_MUSIC
        val signatureTimestamp = if (needsCipher) cipher.prepare() else null

        val root = innerTube.call("player", client) {
            put("videoId", videoId)
            if (signatureTimestamp != null) {
                putJsonObject("playbackContext") {
                    putJsonObject("contentPlaybackContext") {
                        put("signatureTimestamp", signatureTimestamp)
                    }
                }
            }
        }
        val response = json.decodeFromJsonElement(PlayerResponse.serializer(), root)
        if (!response.isPlayable) {
            throw StreamResolutionException(response.playabilityStatus?.reason ?: "not playable")
        }

        val format = bestAudioFormat(response) ?: return null
        val playerUrl = if (needsCipher) cipher.playerUrlOrThrow() else null
        val url = playableUrl(format, playerUrl)

        val expiresInSeconds = response.streamingData?.expiresInSeconds?.toLongOrNull() ?: 21_600L
        return ResolvedStream(
            url = url,
            bitrate = format.effectiveBitrate,
            mimeType = format.mimeType ?: "audio/mp4",
            expiresAtEpochMs = now() + expiresInSeconds * 1000L,
        )
    }

    private suspend fun playableUrl(
        format: PlayerResponse.StreamingData.Format,
        playerUrl: String?,
    ): String {
        format.url?.let { plain ->
            // Even plain (web-client) URLs can carry an un-transformed n param.
            return if (playerUrl != null && Regex("""[?&]n=""").containsMatchIn(plain)) {
                cipher.resolveNParam(plain, playerUrl)
            } else {
                plain
            }
        }
        val signatureCipher = format.signatureCipher ?: format.cipher
            ?: throw StreamResolutionException("format has neither url nor signatureCipher")
        val url = playerUrl ?: run { cipher.prepare(); cipher.playerUrlOrThrow() }
        return cipher.resolveSignatureCipher(signatureCipher, url)
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
        val CLIENT_ORDER = listOf(
            InnerTubeClientId.ANDROID_MUSIC,
            InnerTubeClientId.WEB_REMIX,
            InnerTubeClientId.TVHTML5,
        )
    }
}
