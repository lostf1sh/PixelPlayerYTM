package com.lostf1sh.pixelplayeross.di

import com.lostf1sh.pixelplayeross.data.innertube.InnerTubeClient
import com.lostf1sh.pixelplayeross.data.innertube.VisitorDataStore
import com.lostf1sh.pixelplayeross.data.innertube.YouTubeMusicApi
import com.lostf1sh.pixelplayeross.data.youtube.auth.YouTubeAuthInterceptor

import com.lostf1sh.pixelplayeross.data.repository.YouTubeMusicRepository
import com.lostf1sh.pixelplayeross.data.repository.YouTubeMusicRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Hilt wiring for the YouTube Music layer (InnerTube transport, API, repository).
 *
 * The [YouTubeStreamProxy][com.lostf1sh.pixelplayeross.data.youtube.YouTubeStreamProxy] is
 * constructor-injected, so it needs no provider here.
 */
@Module
@InstallIn(SingletonComponent::class)
object YouTubeModule {

    /**
     * A YouTube-scoped OkHttpClient. Built from the shared base client so it reuses the
     * connection pool/dispatcher, but with the base interceptors cleared: InnerTube sets a
     * per-request `User-Agent` per client identity, and the base UA interceptor would
     * otherwise clobber it. (Auth interceptor is added here in Phase 3.)
     */
    @Provides
    @Singleton
    @YouTubeOkHttpClient
    fun provideYouTubeOkHttpClient(
        baseClient: OkHttpClient,
        authInterceptor: YouTubeAuthInterceptor,
    ): OkHttpClient {
        return baseClient.newBuilder()
            .apply { interceptors().clear() }
            .addInterceptor(authInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideInnerTubeClient(
        @YouTubeOkHttpClient okHttpClient: OkHttpClient,
        json: Json,
        visitorDataStore: VisitorDataStore,
    ): InnerTubeClient = InnerTubeClient(okHttpClient, json, visitorDataStore)

    @Provides
    @Singleton
    fun provideYouTubeMusicApi(
        client: InnerTubeClient,
        json: Json,
        cipherManager: com.lostf1sh.pixelplayeross.data.youtube.stream.YouTubeCipherManager,
    ): YouTubeMusicApi = YouTubeMusicApi(client, json, cipherManager)

    @Provides
    @Singleton
    fun provideYouTubeMusicRepository(
        api: YouTubeMusicApi,
        accountManager: com.lostf1sh.pixelplayeross.data.youtube.auth.YouTubeAccountManager,
    ): YouTubeMusicRepository = YouTubeMusicRepositoryImpl(api, accountManager)
}
