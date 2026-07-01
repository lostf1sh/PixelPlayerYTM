package com.lostf1sh.pixelplayerytm.playback

import androidx.core.net.toUri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.lostf1sh.pixelplayerytm.data.stream.StreamResolver
import kotlinx.coroutines.runBlocking

/**
 * Resolves ytm://<videoId> URIs into real googlevideo stream URLs at open
 * time, so queue items stay lightweight and URLs never go stale in the queue.
 */
@androidx.media3.common.util.UnstableApi
class StreamResolvingDataSource(
    private val streamResolver: StreamResolver,
) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme != YTM_SCHEME) return dataSpec
        val videoId = uri.authority ?: uri.schemeSpecificPart
            ?: throw IllegalArgumentException("ytm uri without videoId: $uri")
        val stream = runBlocking { streamResolver.resolve(videoId) }
        return dataSpec.withUri(stream.url.toUri())
    }
}
