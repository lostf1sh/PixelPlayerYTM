package com.lostf1sh.pixelplayeross.data.youtube.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted storage for the YouTube Music session cookie captured at login. The cookie
 * string authenticates every InnerTube request and is a credential, so it lives in
 * [EncryptedSharedPreferences] rather than plain prefs.
 */
@Singleton
class YouTubeCookieStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = runCatching {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrElse {
        Timber.e(it, "Failed to open encrypted cookie store; falling back to plaintext prefs")
        context.getSharedPreferences(PREFS_NAME + "_fallback", Context.MODE_PRIVATE)
    }

    @Volatile
    private var cachedCookie: String? = prefs.getString(KEY_COOKIE, null)
    @Volatile
    private var cachedAccessToken: String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    @Volatile
    private var cachedRefreshToken: String? = prefs.getString(KEY_REFRESH_TOKEN, null)
    @Volatile
    private var accessTokenExpiresAt: Long = prefs.getLong(KEY_TOKEN_EXPIRES_AT, 0L)

    val cookie: String? get() = cachedCookie

    /** Logged in either via OAuth (TV code) or a captured web cookie. */
    val isLoggedIn: Boolean
        get() = !cachedRefreshToken.isNullOrBlank() || (!cachedCookie.isNullOrBlank() && sapisid() != null)

    fun save(cookie: String) {
        cachedCookie = cookie
        prefs.edit().putString(KEY_COOKIE, cookie).apply()
    }

    // ── OAuth (device-code) tokens ─────────────────────────────────────

    val accessToken: String? get() = cachedAccessToken
    val refreshToken: String? get() = cachedRefreshToken

    /** True when we hold a non-expired access token (60s safety margin). */
    fun isAccessTokenValid(): Boolean =
        !cachedAccessToken.isNullOrBlank() && System.currentTimeMillis() < accessTokenExpiresAt - 60_000L

    fun saveTokens(accessToken: String, refreshToken: String?, expiresInSeconds: Long) {
        cachedAccessToken = accessToken
        accessTokenExpiresAt = System.currentTimeMillis() + expiresInSeconds * 1000L
        if (!refreshToken.isNullOrBlank()) cachedRefreshToken = refreshToken
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putLong(KEY_TOKEN_EXPIRES_AT, accessTokenExpiresAt)
            .apply { if (!refreshToken.isNullOrBlank()) putString(KEY_REFRESH_TOKEN, refreshToken) }
            .apply()
    }

    fun clear() {
        cachedCookie = null
        cachedAccessToken = null
        cachedRefreshToken = null
        accessTokenExpiresAt = 0L
        prefs.edit()
            .remove(KEY_COOKIE)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_EXPIRES_AT)
            .apply()
    }

    /** Extract SAPISID (preferring the __Secure-3PAPISID variant) from the stored cookie. */
    fun sapisid(): String? {
        val cookie = cachedCookie ?: return null
        val pairs = cookie.split(";").mapNotNull { part ->
            val trimmed = part.trim()
            val eq = trimmed.indexOf('=')
            if (eq <= 0) null else trimmed.substring(0, eq) to trimmed.substring(eq + 1)
        }.toMap()
        return pairs["__Secure-3PAPISID"] ?: pairs["SAPISID"]
    }

    private companion object {
        const val PREFS_NAME = "youtube_account_secure"
        const val KEY_COOKIE = "cookie"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"
    }
}
