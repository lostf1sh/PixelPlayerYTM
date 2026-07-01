package com.lostf1sh.pixelplayerytm.data.youtube.auth

import okhttp3.Interceptor
import okhttp3.Response
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches authentication to InnerTube requests.
 *
 * Prefers an OAuth `Authorization: Bearer` token. CRITICAL: when a Bearer token
 * is present the `?key=` API-key query param MUST be stripped — if both are
 * sent, Google authenticates via the key and ignores the Bearer, dropping the
 * request to anonymous (empty library). This mirrors ytmusicapi's OAuth path.
 *
 * Falls back to cookie + SAPISIDHASH auth when only a cookie session exists.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val authManager: AuthManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        // Only touch InnerTube calls.
        if (!original.url.host.contains("youtube.com")) {
            return chain.proceed(original)
        }

        val bearer = authManager.blockingAuthorizationHeader()
        if (bearer != null) {
            val url = original.url.newBuilder().removeAllQueryParameters("key").build()
            val request = original.newBuilder()
                .url(url)
                .header("Authorization", bearer)
                .header("X-Goog-Request-Time", (System.currentTimeMillis() / 1000).toString())
                .build()
            return chain.proceed(request)
        }

        val cookie = authManager.blockingCookie()
        if (!cookie.isNullOrBlank()) {
            val sapisid = extractSapisid(cookie)
            val builder = original.newBuilder()
                .header("Cookie", cookie)
                .header("Origin", "https://music.youtube.com")
            if (sapisid != null) {
                builder.header("Authorization", sapisidHash(sapisid))
            }
            return chain.proceed(builder.build())
        }

        return chain.proceed(original)
    }

    private fun extractSapisid(cookie: String): String? {
        val parts = cookie.split(";").associate {
            val kv = it.trim().split("=", limit = 2)
            (kv.getOrNull(0)?.trim().orEmpty()) to (kv.getOrNull(1)?.trim().orEmpty())
        }
        return parts["__Secure-3PAPISID"] ?: parts["SAPISID"]
    }

    private fun sapisidHash(sapisid: String): String {
        val origin = "https://music.youtube.com"
        val timestamp = System.currentTimeMillis() / 1000
        val input = "$timestamp $sapisid $origin"
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${timestamp}_$digest"
    }
}
