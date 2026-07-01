package com.lostf1sh.pixelplayeross.di

import com.lostf1sh.pixelplayeross.data.network.youtube.auth.YtAuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Wiring for the YouTube Music data layer. Both clients derive from the app's shared
 * OkHttpClient so they reuse its connection pool and dispatcher; only the interceptor
 * differs (see [YouTubeHttp] vs [YouTubeBaseHttp]).
 */
@Module
@InstallIn(SingletonComponent::class)
object YouTubeModule {

    @Provides
    @Singleton
    @YouTubeBaseHttp
    fun provideYouTubeBaseHttpClient(okHttpClient: OkHttpClient): OkHttpClient =
        okHttpClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @YouTubeHttp
    fun provideYouTubeHttpClient(
        @YouTubeBaseHttp baseClient: OkHttpClient,
        authInterceptor: YtAuthInterceptor,
    ): OkHttpClient =
        baseClient.newBuilder()
            .addInterceptor(authInterceptor)
            .build()
}
