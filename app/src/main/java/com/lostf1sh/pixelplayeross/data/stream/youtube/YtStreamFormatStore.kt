package com.lostf1sh.pixelplayeross.data.stream.youtube

import android.content.Context
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers which audio format (itag) each YTM videoId last resolved to, so the disk
 * cache can key spans by (videoId, itag). Persisted, so a fully cached track replays
 * across restarts with zero network — the itag rides on the proxy URL and becomes the
 * cache key without needing a fresh `player` call.
 *
 * If a later resolve picks a different format (sign-in state changed the bitrate
 * ceiling, or a fallback client offered a different lineup), the old format's cache
 * entry is evicted immediately: spans of two different encodings under one key would
 * be a corrupt, unplayable blob.
 */
@OptIn(UnstableApi::class)
@Singleton
class YtStreamFormatStore @Inject constructor(
    @ApplicationContext context: Context,
    @YtStreamCache private val streamCache: SimpleCache,
) {
    private val prefs = context.getSharedPreferences("yt_stream_formats", Context.MODE_PRIVATE)

    private val itags = ConcurrentHashMap<String, Int>().apply {
        prefs.all.forEach { (videoId, itag) -> (itag as? Int)?.let { put(videoId, it) } }
    }

    fun itagFor(videoId: String): Int? = itags[videoId]

    fun record(videoId: String, itag: Int) {
        val previous = itags.put(videoId, itag)
        if (previous == itag) return
        prefs.edit { putInt(videoId, itag) }
        if (previous != null) {
            Timber.tag(TAG).d("format for %s changed %d -> %d; evicting stale cache", videoId, previous, itag)
            runCatching { streamCache.removeResource(cacheKey(videoId, previous)) }
        }
    }

    companion object {
        private const val TAG = "YtStreamFormatStore"

        /** The disk-cache key for one (videoId, itag) pair — one exact encoded file. */
        fun cacheKey(videoId: String, itag: Int): String = "ytm:$videoId:$itag"
    }
}
