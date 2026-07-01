package com.lostf1sh.pixelplayeross.data.network.youtube.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Attaches the signed-in user's identity to InnerTube requests; a no-op while logged
 * out so anonymous browsing keeps working. Installed only on the dedicated YouTube
 * OkHttp client, never the app-wide one.
 *
 * (A cookie + SAPISIDHASH fallback for WebView-captured sessions can be added here if
 * the TV device-code flow ever breaks; Bearer is the primary and currently only path.)
 */
@Singleton
class YtAuthInterceptor @Inject constructor(
    private val accountStore: YtAccountStore,
    // Provider breaks the DI cycle: YtOAuthClient itself uses the *base* (unauthenticated)
    // YouTube client, but Dagger still needs lazy resolution here.
    private val oauthClient: Provider<YtOAuthClient>,
) : Interceptor {

    private val refreshLock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = validAccessToken() ?: return chain.proceed(request)

        // With a Bearer token the `key` API-key param MUST go. If both are present Google
        // authenticates via the key (app identity) and silently ignores the Bearer (user
        // identity) — requests come back anonymous and the library turns up empty.
        val keylessUrl = request.url.newBuilder()
            .removeAllQueryParameters("key")
            .build()

        return chain.proceed(
            request.newBuilder()
                .url(keylessUrl)
                .header("Authorization", "Bearer $token")
                .build()
        )
    }

    /** A currently-valid access token, refreshing synchronously when expired. */
    private fun validAccessToken(): String? {
        val refreshToken = accountStore.refreshToken ?: return null
        if (accountStore.isAccessTokenValid()) return accountStore.currentAccessToken()

        synchronized(refreshLock) {
            if (accountStore.isAccessTokenValid()) return accountStore.currentAccessToken()
            val fresh = runBlocking { oauthClient.get().refresh(refreshToken) } ?: return null
            accountStore.saveTokens(fresh.accessToken, fresh.refreshToken, fresh.expiresInSeconds)
            return fresh.accessToken
        }
    }
}
