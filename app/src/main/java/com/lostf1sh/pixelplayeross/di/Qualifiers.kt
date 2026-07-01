package com.lostf1sh.pixelplayeross.di

import javax.inject.Qualifier

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
 * OkHttpClient for YouTube/InnerTube calls, with the auth interceptor installed.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class YouTubeHttp

/**
 * Unauthenticated OkHttpClient for the YouTube OAuth token endpoints — must NOT carry
 * the auth interceptor (it is what the interceptor itself uses to refresh tokens).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class YouTubeBaseHttp
