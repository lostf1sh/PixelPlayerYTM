package com.lostf1sh.pixelplayerytm.data.stream

import com.lostf1sh.pixelplayerytm.data.innertube.InnerTube
import com.lostf1sh.pixelplayerytm.data.innertube.model.YouTubeClient
import com.lostf1sh.pixelplayerytm.data.innertube.model.response.PlayerResponse
import com.lostf1sh.pixelplayerytm.data.youtube.auth.AuthState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

data class ResolvedStream(
    val url: String,
    val bitrate: Long,
    val mimeType: String,
    val loudnessDb: Double?,
    val expiresAtMillis: Long,
)

class StreamResolutionException(message: String) : Exception(message)

/**
 * Resolves a videoId to a playable audio stream URL. Tries clients in order
 * of reliability; ANDROID_MUSIC typically returns plain URLs, WEB_REMIX/TVHTML5
 * need cipher descrambling via [YouTubeCipherManager].
 */
@Singleton
class StreamResolver @Inject constructor(
    private val innerTube: InnerTube,
    private val cipherManager: YouTubeCipherManager,
    private val authState: AuthState,
) {
    private data class CacheEntry(val stream: ResolvedStream)

    private val cache = LinkedHashMap<String, CacheEntry>()
    private val cacheMutex = Mutex()

    private val clientOrder = listOf(
        YouTubeClient.ANDROID_MUSIC,
        YouTubeClient.WEB_REMIX,
        YouTubeClient.TVHTML5,
    )

    suspend fun resolve(videoId: String): ResolvedStream {
        cacheMutex.withLock {
            cache[videoId]?.let { entry ->
                if (entry.stream.expiresAtMillis > nowMillis() + 60_000) return entry.stream
                cache.remove(videoId)
            }
        }

        var lastError: String? = null
        for (client in clientOrder) {
            try {
                val stream = resolveWithClient(videoId, client) ?: continue
                cacheMutex.withLock {
                    cache[videoId] = CacheEntry(stream)
                    if (cache.size > 60) cache.remove(cache.keys.first())
                }
                return stream
            } catch (e: Exception) {
                lastError = "${client.clientName}: ${e.message}"
            }
        }
        throw StreamResolutionException("Could not resolve stream for $videoId ($lastError)")
    }

    private suspend fun resolveWithClient(
        videoId: String,
        client: YouTubeClient,
    ): ResolvedStream? {
        val signatureTimestamp = if (client.useSignatureTimestamp) {
            cipherManager.prepare()
        } else {
            null
        }

        val response = innerTube.player(
            videoId = videoId,
            client = client,
            signatureTimestamp = signatureTimestamp,
        )
        if (!response.isPlayable) {
            throw StreamResolutionException(
                response.playabilityStatus?.reason ?: "Not playable",
            )
        }

        val format = selectBestAudioFormat(response) ?: return null
        val playerUrl = if (client.useSignatureTimestamp) currentPlayerUrl() else null
        val url = resolveFormatUrl(format, playerUrl)

        return ResolvedStream(
            url = url,
            bitrate = format.bitrate ?: format.averageBitrate ?: 0,
            mimeType = format.mimeType ?: "audio/mp4",
            loudnessDb = format.loudnessDb,
            expiresAtMillis = nowMillis() +
                ((response.streamingData?.expiresInSeconds?.toLongOrNull() ?: 21600L) * 1000L),
        )
    }

    private suspend fun resolveFormatUrl(
        format: PlayerResponse.StreamingData.Format,
        playerUrl: String?,
    ): String {
        format.url?.let { plain ->
            // Even plain URLs may carry an untransformed n param on web clients.
            return if (playerUrl != null && plain.contains("&n=")) {
                cipherManager.resolveNParam(plain, playerUrl)
            } else {
                plain
            }
        }
        val cipher = format.signatureCipher ?: format.cipher
            ?: throw StreamResolutionException("Format has neither url nor signatureCipher")
        val url = playerUrl ?: currentPlayerUrl()
        return cipherManager.resolveSignatureCipher(cipher, url)
    }

    private suspend fun currentPlayerUrl(): String {
        cipherManager.prepare()
        return cipherManager.playerUrlOrThrow()
    }

    private fun selectBestAudioFormat(response: PlayerResponse): PlayerResponse.StreamingData.Format? {
        val streamingData = response.streamingData ?: return null
        val audioFormats = (streamingData.adaptiveFormats.orEmpty() + streamingData.formats.orEmpty())
            .filter { it.isAudio && (it.url != null || it.signatureCipher != null || it.cipher != null) }
            .ifEmpty {
                // Muxed formats as last resort.
                streamingData.formats.orEmpty()
                    .filter { it.url != null || it.signatureCipher != null }
            }

        val ceiling = if (authState.isLoggedIn()) Long.MAX_VALUE else 160_000L
        return audioFormats
            .sortedByDescending { it.bitrate ?: it.averageBitrate ?: 0 }
            .let { sorted ->
                sorted.firstOrNull { (it.bitrate ?: 0) <= ceiling } ?: sorted.lastOrNull()
            }
    }

    private fun nowMillis(): Long = System.currentTimeMillis()
}
