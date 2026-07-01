package com.lostf1sh.pixelplayeross.data.network.youtube.auth

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the signed-in YouTube account's OAuth tokens and exposes a [isSignedIn] flow
 * the UI observes to gate library features.
 *
 * Tokens are stored in private SharedPreferences. This is personal-use software; for a
 * shipping app these belong in EncryptedSharedPreferences / the Keystore, which is left
 * as a follow-up (see M7).
 */
@Singleton
class YtAccountStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Volatile
    private var accessToken: String? = prefs.getString(KEY_ACCESS, null)

    @Volatile
    private var refreshTokenValue: String? = prefs.getString(KEY_REFRESH, null)

    /** Absolute epoch-millis at which [accessToken] expires (with a safety margin applied). */
    @Volatile
    private var accessExpiryEpochMs: Long = prefs.getLong(KEY_EXPIRY, 0L)

    private val _isSignedIn = MutableStateFlow(refreshTokenValue != null)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    val refreshToken: String? get() = refreshTokenValue

    fun currentAccessToken(): String? = accessToken

    fun isAccessTokenValid(): Boolean =
        accessToken != null && System.currentTimeMillis() < accessExpiryEpochMs

    /**
     * Store a freshly obtained access token. [refreshToken] may be null on a plain refresh
     * (Google omits it), in which case the existing refresh token is kept.
     */
    fun saveTokens(accessToken: String, refreshToken: String?, expiresInSeconds: Long) {
        this.accessToken = accessToken
        if (refreshToken != null) this.refreshTokenValue = refreshToken
        // Renew a minute early so a request never fires with an about-to-expire token.
        this.accessExpiryEpochMs = System.currentTimeMillis() + (expiresInSeconds - 60).coerceAtLeast(0) * 1000L
        prefs.edit {
            putString(KEY_ACCESS, this@YtAccountStore.accessToken)
            putString(KEY_REFRESH, this@YtAccountStore.refreshTokenValue)
            putLong(KEY_EXPIRY, this@YtAccountStore.accessExpiryEpochMs)
        }
        _isSignedIn.value = refreshTokenValue != null
    }

    fun signOut() {
        accessToken = null
        refreshTokenValue = null
        accessExpiryEpochMs = 0L
        prefs.edit { clear() }
        _isSignedIn.value = false
    }

    private companion object {
        const val PREFS = "yt_account"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRY = "access_expiry_ms"
    }
}
