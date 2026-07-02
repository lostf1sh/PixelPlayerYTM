package com.lostf1sh.pixelplayeross.data.stream.youtube

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

/** The Media3 disk cache holding YTM audio bytes (not album art or other caches). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class YtStreamCache

@Module
@InstallIn(SingletonComponent::class)
object YtStreamCacheModule {

    /**
     * Disk cache for YTM audio served through the loopback proxy. Bytes are keyed by
     * videoId (see the CacheKeyFactory in DualPlayerEngine), so a replayed track is
     * served from disk instead of re-hitting googlevideo — which also survives the
     * ~6 h stream-URL expiry for fully cached tracks.
     *
     * SimpleCache allows exactly one instance per directory, hence the @Singleton.
     */
    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    @YtStreamCache
    fun provideYtStreamCache(@ApplicationContext context: Context): SimpleCache =
        SimpleCache(
            File(context.cacheDir, "ytm_stream_cache"),
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(context),
        )

    private const val MAX_CACHE_BYTES = 512L * 1024 * 1024
}
