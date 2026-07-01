package com.lostf1sh.pixelplayeross.data.youtube.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Authenticates InnerTube requests when the user is signed in. Prefers the OAuth (TV device
 * code) Bearer token; falls back to the captured web cookie + SAPISIDHASH. A no-op when
 * logged out, so anonymous browsing keeps working.
 */
@Singleton
class YouTubeAuthInterceptor @Inject constructor(
    private val cookieStore: YouTubeCookieStore,
    // Provider avoids a DI cycle: YouTubeOAuth uses the base OkHttpClient, not this one.
    private val oauthProvider: Provider<YouTubeOAuth>,
) : Interceptor {

    private val refreshLock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = currentAccessToken()
        if (token != null) {
            val request = chain.request()
            // Critical: with OAuth we must NOT send the `key` API-key param. If both an API
            // key and a Bearer token are present, Google authenticates via the key (app
            // identity) and ignores the Bearer (user identity) — so the request comes back as
            // anonymous and personalised library data is empty. Dropping the key is exactly
            // what ytmusicapi does for OAuth requests.
            val url = request.url.newBuilder().removeAllQueryParameters("key").build()
            val authorized = request.newBuilder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("X-Goog-Request-Time", (System.currentTimeMillis() / 1000).toString())
                .build()
            return chain.proceed(authorized)
        }

        // Cookie fallback (WebView login).
        val cookie = cookieStore.cookie
        val sapisid = cookieStore.sapisid()
        if (cookie.isNullOrBlank() || sapisid.isNullOrBlank()) {
            return chain.proceed(chain.request())
        }
        val epochSeconds = System.currentTimeMillis() / 1000
        val authorized = chain.request().newBuilder()
            .header("Cookie", cookie)
            .header("Authorization", SapisidHashGenerator.generate(sapisid, epochSeconds))
            .header("X-Goog-AuthUser", "0")
            .build()
        return chain.proceed(authorized)
    }

    /** Returns a valid OAuth access token, refreshing it synchronously if needed. */
    private fun currentAccessToken(): String? {
        val refreshToken = cookieStore.refreshToken ?: return null
        if (cookieStore.isAccessTokenValid()) return cookieStore.accessToken

        synchronized(refreshLock) {
            if (cookieStore.isAccessTokenValid()) return cookieStore.accessToken
            val refreshed = runBlocking { oauthProvider.get().refresh(refreshToken) } ?: return null
            cookieStore.saveTokens(refreshed.accessToken, refreshed.refreshToken, refreshed.expiresInSeconds)
            return refreshed.accessToken
        }
    }
}
