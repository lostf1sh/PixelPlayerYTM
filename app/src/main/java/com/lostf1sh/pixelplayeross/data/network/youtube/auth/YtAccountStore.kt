package com.lostf1sh.pixelplayeross.data.network.youtube.auth

import android.content.Context
import androidx.core.content.edit
import com.lostf1sh.pixelplayeross.data.model.YtAccountInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the signed-in YouTube session as a raw Cookie header for music.youtube.com
 * and exposes an [isSignedIn] flow the UI observes to gate library features.
 *
 * Cookie auth (SAPISIDHASH) is the only mechanism InnerTube still accepts from third
 * parties: the TV device-code OAuth issues valid tokens, but every youtubei call made
 * with one is rejected 400 INVALID_ARGUMENT (verified 2026-07; same reason yt-dlp and
 * ytmusicapi dropped it).
 *
 * Cookies are stored in private SharedPreferences. This is personal-use software; for a
 * shipping app these belong in EncryptedSharedPreferences / the Keystore.
 */
@Singleton
class YtAccountStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @Volatile
    private var cookieValue: String? = prefs.getString(KEY_COOKIE, null)

    private val _isSignedIn = MutableStateFlow(extractSapisid(cookieValue) != null)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    /** The full Cookie header to send with InnerTube requests, or null when logged out. */
    val cookieHeader: String? get() = cookieValue

    /** The SAPISID used to compute the SAPISIDHASH Authorization, or null when logged out. */
    fun sapisid(): String? = extractSapisid(cookieValue)

    /**
     * Store a captured cookie string. Returns false (and stores nothing) if it doesn't
     * contain a usable SAPISID — i.e. the WebView login didn't actually complete.
     */
    fun saveCookie(cookie: String): Boolean {
        if (extractSapisid(cookie) == null) return false
        cookieValue = cookie
        prefs.edit { putString(KEY_COOKIE, cookie) }
        _isSignedIn.value = true
        return true
    }

    fun signOut() {
        cookieValue = null
        prefs.edit { clear() }
        _isSignedIn.value = false
        _accountInfo.value = null
    }

    // ─────────────────────── Account identity ───────────────────────

    private val _accountInfo = MutableStateFlow(readAccountInfo())

    /** Who's signed in (name/handle/avatar), cached from `account/account_menu`. */
    val accountInfo: StateFlow<YtAccountInfo?> = _accountInfo.asStateFlow()

    fun saveAccountInfo(info: YtAccountInfo) {
        prefs.edit {
            putString(KEY_ACCOUNT_NAME, info.name)
            putString(KEY_ACCOUNT_HANDLE, info.handle)
            putString(KEY_ACCOUNT_EMAIL, info.email)
            putString(KEY_ACCOUNT_AVATAR, info.avatarUrl)
        }
        _accountInfo.value = info
    }

    private fun readAccountInfo(): YtAccountInfo? {
        val name = prefs.getString(KEY_ACCOUNT_NAME, null) ?: return null
        return YtAccountInfo(
            name = name,
            handle = prefs.getString(KEY_ACCOUNT_HANDLE, null),
            email = prefs.getString(KEY_ACCOUNT_EMAIL, null),
            avatarUrl = prefs.getString(KEY_ACCOUNT_AVATAR, null),
        )
    }

    private fun extractSapisid(cookie: String?): String? {
        if (cookie.isNullOrBlank()) return null
        // Prefer the third-party variant; SAPISIDHASH accepts either value.
        for (name in listOf("__Secure-3PAPISID", "SAPISID")) {
            val match = Regex("""(?:^|;\s*)${Regex.escape(name)}=([^;]+)""").find(cookie)
            match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    private companion object {
        const val PREFS = "yt_account"
        const val KEY_COOKIE = "cookie"
        const val KEY_ACCOUNT_NAME = "account_name"
        const val KEY_ACCOUNT_HANDLE = "account_handle"
        const val KEY_ACCOUNT_EMAIL = "account_email"
        const val KEY_ACCOUNT_AVATAR = "account_avatar"
    }
}
