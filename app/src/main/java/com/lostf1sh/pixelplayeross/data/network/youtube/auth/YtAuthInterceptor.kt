package com.lostf1sh.pixelplayeross.data.network.youtube.auth

import okhttp3.Interceptor
import okhttp3.Response
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches the signed-in user's identity to InnerTube requests; a no-op while logged
 * out so anonymous browsing keeps working. Installed only on the dedicated YouTube
 * OkHttp client, never the app-wide one.
 *
 * Identity is the browser-style cookie session: the captured Cookie header plus a
 * per-request `SAPISIDHASH` Authorization derived from the SAPISID cookie — exactly
 * what music.youtube.com itself sends. (OAuth Bearer is NOT an option: InnerTube
 * rejects TV device-flow tokens with 400 INVALID_ARGUMENT across all clients.)
 */
@Singleton
class YtAuthInterceptor @Inject constructor(
    private val accountStore: YtAccountStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Only authenticate the web client (WEB_REMIX, X-YouTube-Client-Name: 67). It is
        // what browse/search/library and — crucially — the `player` stream resolution use,
        // and the one whose cookie session Google accepts. App/TV player clients reject web
        // cookies (400) or bounce through an endless consent redirect, so they stay
        // anonymous; getting that wrong also starves browse/search on the shared client.
        if (request.header("X-YouTube-Client-Name") != WEB_REMIX_CLIENT_ID) {
            return chain.proceed(request)
        }

        val cookie = accountStore.cookieHeader
        val sapisid = accountStore.sapisid()
        if (cookie.isNullOrBlank() || sapisid == null) return chain.proceed(request)

        return chain.proceed(
            request.newBuilder()
                .header("Cookie", cookie)
                .header("Authorization", sapisidHash(sapisid))
                .header("X-Origin", ORIGIN)
                .header("X-Goog-AuthUser", "0")
                .build()
        )
    }

    /** `SAPISIDHASH <ts>_<sha1("<ts> <sapisid> <origin>")>` — Google's cookie-auth proof. */
    private fun sapisidHash(sapisid: String, nowMs: Long = System.currentTimeMillis()): String {
        val timestamp = nowMs / 1000
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$timestamp $sapisid $ORIGIN".toByteArray(Charsets.UTF_8))
        val hex = buildString(digest.size * 2) {
            digest.forEach { byte -> append("%02x".format(byte)) }
        }
        return "SAPISIDHASH ${timestamp}_$hex"
    }

    private companion object {
        const val ORIGIN = "https://music.youtube.com"
        const val WEB_REMIX_CLIENT_ID = "67"
    }
}
