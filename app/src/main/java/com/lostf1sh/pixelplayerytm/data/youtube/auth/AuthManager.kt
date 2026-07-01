package com.lostf1sh.pixelplayerytm.data.youtube.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the signed-in session: persists tokens/cookies, refreshes expired
 * access tokens, and exposes the current credentials to the interceptor.
 */
@Singleton
class AuthManager @Inject constructor(
    private val store: AuthStore,
    private val oauth: YouTubeOAuth,
    private val authState: AuthState,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()

    init {
        scope.launch {
            authState.setLoggedIn(store.hasSession())
        }
    }

    suspend fun onLoggedIn(tokens: AuthStore.Tokens) {
        store.saveTokens(tokens)
        authState.setLoggedIn(true)
    }

    suspend fun onLoggedInWithCookie(cookie: String) {
        store.saveCookie(cookie)
        authState.setLoggedIn(true)
    }

    suspend fun logout() {
        store.clear()
        authState.setLoggedIn(false)
    }

    suspend fun refreshState() {
        authState.setLoggedIn(store.hasSession())
    }

    /**
     * Returns a valid `Authorization` header value, refreshing the access token
     * if it is within 60s of expiry. Called from the interceptor on a
     * background thread, so blocking here is acceptable.
     */
    fun blockingAuthorizationHeader(): String? = runBlocking {
        val tokens = store.tokens() ?: return@runBlocking null
        if (tokens.expiresAtMillis > System.currentTimeMillis() + 60_000) {
            return@runBlocking "${tokens.tokenType} ${tokens.accessToken}"
        }
        refreshMutex.withLock {
            val current = store.tokens() ?: return@runBlocking null
            if (current.expiresAtMillis > System.currentTimeMillis() + 60_000) {
                return@runBlocking "${current.tokenType} ${current.accessToken}"
            }
            runCatching {
                val (access, expiresAt, type) = oauth.refresh(current.refreshToken)
                store.updateAccessToken(access, expiresAt, type)
                "$type $access"
            }.getOrElse {
                // Refresh failed — treat as logged out for token auth; cookie may still work.
                null
            }
        }
    }

    fun blockingCookie(): String? = runBlocking { store.cookie() }
}
