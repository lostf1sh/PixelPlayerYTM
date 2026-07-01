package com.lostf1sh.pixelplayeross.di

import javax.inject.Qualifier

/**
 * Qualifier for Deezer Retrofit instance.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DeezerRetrofit

/**
 * Qualifier for Fast OkHttpClient (Short timeouts).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FastOkHttpClient

/**
 * Qualifier for Gson instance configured for backup serialization.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BackupGson

/**
 * Qualifier for application-lifetime coroutine scope.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

/**
 * Qualifier for the YouTube-scoped OkHttpClient. Shares the base client's connection pool
 * but carries its own interceptor stack (per-request InnerTube headers; auth in Phase 3).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class YouTubeOkHttpClient
