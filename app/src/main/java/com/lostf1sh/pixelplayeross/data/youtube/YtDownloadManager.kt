package com.lostf1sh.pixelplayeross.data.youtube

import android.content.Context
import android.net.Uri
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.model.YtArtistLink
import com.lostf1sh.pixelplayeross.data.model.YtTrack
import com.lostf1sh.pixelplayeross.data.model.toSong
import com.lostf1sh.pixelplayeross.data.network.youtube.InnerTubeClientId
import com.lostf1sh.pixelplayeross.data.stream.youtube.YouTubeStreamResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A completed offline download, as persisted in its `<videoId>.json` sidecar. */
@Serializable
data class YtDownloadEntry(
    val videoId: String,
    val title: String,
    val artists: List<Artist> = emptyList(),
    val album: String? = null,
    val albumBrowseId: String? = null,
    val durationMs: Long = 0,
    val mimeType: String? = null,
    val sizeBytes: Long = 0,
    val audioFileName: String,
    val artFileName: String? = null,
    val downloadedAtMs: Long = 0,
) {
    @Serializable
    data class Artist(val name: String, val channelId: String? = null)
}

/**
 * Offline downloads for YTM tracks: resolves the stream like playback does, saves the
 * full audio (plus thumbnail) under `filesDir/ytm_downloads/`, and keeps a JSON sidecar
 * per track as the metadata store — no DB migration, and a track is exactly the files
 * on disk. Playback stays on the normal `ytm://` pipeline: the player engine asks
 * [localUriFor] first and plays the file with no network when it's downloaded.
 */
@Singleton
class YtDownloadManager @Inject constructor(
    @ApplicationContext context: Context,
    private val resolver: YouTubeStreamResolver,
    private val httpClient: OkHttpClient,
    private val json: Json,
) {
    private val dir = File(context.filesDir, "ytm_downloads")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloads = MutableStateFlow<Map<String, YtDownloadEntry>>(emptyMap())

    /** Completed downloads by videoId; loaded from disk at startup. */
    val downloads: StateFlow<Map<String, YtDownloadEntry>> = _downloads.asStateFlow()

    /** In-flight downloads: videoId → progress in 0..1 (0 while the size is unknown). */
    private val _inProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val inProgress: StateFlow<Map<String, Float>> = _inProgress.asStateFlow()

    init {
        scope.launch { loadFromDisk() }
    }

    fun isDownloaded(videoId: String): Boolean = _downloads.value.containsKey(videoId)

    /** The downloaded audio as a file URI, or null when [videoId] isn't downloaded. */
    fun localUriFor(videoId: String): Uri? {
        val entry = _downloads.value[videoId] ?: return null
        val file = File(dir, entry.audioFileName)
        return if (file.exists()) Uri.fromFile(file) else null
    }

    /** The wire-model track a download was made from (thumbnail left remote-less). */
    fun trackFor(entry: YtDownloadEntry): YtTrack = YtTrack(
        videoId = entry.videoId,
        title = entry.title,
        artists = entry.artists.map { YtArtistLink(it.name, it.channelId) },
        album = entry.album,
        albumBrowseId = entry.albumBrowseId,
        durationMs = entry.durationMs,
        thumbnailUrl = null,
    )

    /** The app-wide [Song] for a downloaded track (artwork points at the local file). */
    fun songFor(entry: YtDownloadEntry): Song {
        val artPath = entry.artFileName
            ?.let { File(dir, it) }
            ?.takeIf(File::exists)
            ?.absolutePath
        return trackFor(entry).toSong().copy(albumArtUriString = artPath)
    }

    /** Start downloading [track]; no-op if it's already downloaded or in flight. */
    fun download(track: YtTrack) {
        val videoId = track.videoId
        if (isDownloaded(videoId)) return
        val started = synchronized(this) {
            if (_inProgress.value.containsKey(videoId)) return@synchronized false
            _inProgress.update { it + (videoId to 0f) }
            true
        }
        if (!started) return

        scope.launch {
            runCatching { doDownload(track) }
                .onFailure { Timber.tag(TAG).w(it, "download failed for %s", videoId) }
            _inProgress.update { it - videoId }
        }
    }

    fun delete(videoId: String) {
        val entry = _downloads.value[videoId] ?: return
        _downloads.update { it - videoId }
        scope.launch {
            File(dir, entry.audioFileName).delete()
            entry.artFileName?.let { File(dir, it).delete() }
            File(dir, "$videoId.json").delete()
        }
    }

    private fun loadFromDisk() {
        val files = dir.listFiles { f -> f.extension == "json" } ?: return
        val loaded = files.mapNotNull { file ->
            runCatching {
                json.decodeFromString(YtDownloadEntry.serializer(), file.readText())
            }.getOrNull()?.takeIf { File(dir, it.audioFileName).exists() }
        }
        _downloads.update { current -> loaded.associateBy { it.videoId } + current }
    }

    private suspend fun doDownload(track: YtTrack) {
        dir.mkdirs()
        val videoId = track.videoId

        // Resolve like playback would; a 403 means the cached URL went stale — invalidate
        // and re-resolve once.
        var stream = resolver.resolve(videoId)
        val tmp = File(dir, "$videoId.part")
        var written = fetchAudio(stream.url, tmp, videoId)
        if (written < 0) {
            resolver.invalidate(videoId)
            stream = resolver.resolve(videoId)
            written = fetchAudio(stream.url, tmp, videoId)
        }
        if (written < 0) {
            tmp.delete()
            throw IllegalStateException("audio fetch failed for $videoId")
        }

        val extension = when {
            stream.mimeType.contains("webm") -> "webm"
            stream.mimeType.contains("mp4") -> "m4a"
            else -> "audio"
        }
        val audioFile = File(dir, "$videoId.$extension")
        if (!tmp.renameTo(audioFile)) {
            tmp.copyTo(audioFile, overwrite = true)
            tmp.delete()
        }

        val artFileName = fetchThumbnail(track.thumbnailUrl, videoId)

        val entry = YtDownloadEntry(
            videoId = videoId,
            title = track.title,
            artists = track.artists.map { YtDownloadEntry.Artist(it.name, it.channelId) },
            album = track.album,
            albumBrowseId = track.albumBrowseId,
            durationMs = track.durationMs,
            mimeType = stream.mimeType,
            sizeBytes = written,
            audioFileName = audioFile.name,
            artFileName = artFileName,
            downloadedAtMs = System.currentTimeMillis(),
        )
        File(dir, "$videoId.json")
            .writeText(json.encodeToString(YtDownloadEntry.serializer(), entry))
        _downloads.update { it + (videoId to entry) }
        Timber.tag(TAG).d("downloaded %s (%d bytes)", videoId, written)
    }

    /** Streams [url] into [target]; returns bytes written, or -1 on a retryable failure. */
    private fun fetchAudio(url: String, target: File, videoId: String): Long {
        val request = Request.Builder()
            .url(url)
            .apply { upstreamHeaders(url).forEach { (k, v) -> header(k, v) } }
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Timber.tag(TAG).w("audio fetch HTTP %d for %s", response.code, videoId)
                return -1
            }
            val body = response.body ?: return -1
            val total = body.contentLength()
            var written = 0L
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            val progress = (written.toFloat() / total).coerceIn(0f, 1f)
                            _inProgress.update { it + (videoId to progress) }
                        }
                    }
                }
            }
            return written
        }
    }

    /**
     * googlevideo URLs are minted for a specific InnerTube client (`c` param); the byte
     * request must look like that client or the CDN answers 403.
     */
    private fun upstreamHeaders(streamUrl: String): Map<String, String> {
        val clientName = Uri.parse(streamUrl).getQueryParameter("c").orEmpty().uppercase()
        val client = InnerTubeClientId.entries.firstOrNull { it.clientName == clientName }
            ?: InnerTubeClientId.WEB_REMIX
        return buildMap {
            put("User-Agent", client.userAgent)
            client.referer?.let { referer ->
                put("Referer", referer)
                put("Origin", referer.trimEnd('/'))
            }
        }
    }

    private fun fetchThumbnail(thumbnailUrl: String?, videoId: String): String? {
        if (thumbnailUrl.isNullOrBlank()) return null
        return runCatching {
            httpClient.newCall(Request.Builder().url(thumbnailUrl).build()).execute()
                .use { response ->
                    if (!response.isSuccessful) return null
                    val file = File(dir, "$videoId.jpg")
                    response.body?.byteStream()?.use { input ->
                        file.outputStream().use { input.copyTo(it) }
                    } ?: return null
                    file.name
                }
        }.getOrNull()
    }

    private companion object {
        const val TAG = "YtDownloadManager"
    }
}
