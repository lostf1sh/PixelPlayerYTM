package com.lostf1sh.pixelplayeross.data.youtube.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for YouTube Music sign-in state. Supports two login paths:
 * the WebView-free TV device-code OAuth flow (preferred) and legacy web-cookie capture.
 */
@Singleton
class YouTubeAccountManager @Inject constructor(
    private val cookieStore: YouTubeCookieStore,
    private val oauth: YouTubeOAuth,
) {
    private val _isLoggedIn = MutableStateFlow(cookieStore.isLoggedIn)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    // ── TV device-code OAuth ───────────────────────────────────────────

    suspend fun requestDeviceCode(): YouTubeOAuth.DeviceCode? = oauth.requestDeviceCode()

    suspend fun pollForTokens(deviceCode: String): YouTubeOAuth.PollResult = oauth.pollForTokens(deviceCode)

    fun onOAuthSuccess(tokens: YouTubeOAuth.Tokens) {
        cookieStore.saveTokens(tokens.accessToken, tokens.refreshToken, tokens.expiresInSeconds)
        _isLoggedIn.value = cookieStore.isLoggedIn
    }

    // ── Legacy web-cookie login ────────────────────────────────────────

    fun signIn(cookie: String) {
        cookieStore.save(cookie)
        _isLoggedIn.value = cookieStore.isLoggedIn
    }

    fun signOut() {
        cookieStore.clear()
        _isLoggedIn.value = false
    }
}
