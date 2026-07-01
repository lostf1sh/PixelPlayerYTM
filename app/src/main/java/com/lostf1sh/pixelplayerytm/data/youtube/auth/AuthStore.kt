package com.lostf1sh.pixelplayerytm.data.youtube.auth

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.authDataStore by preferencesDataStore("youtube_auth")

/** Persisted OAuth tokens and/or cookies for the signed-in Google account. */
@Singleton
class AuthStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val TOKEN_TYPE = stringPreferencesKey("token_type")
        val EXPIRES_AT = longPreferencesKey("expires_at")
        val COOKIE = stringPreferencesKey("cookie")
        val HAS_SESSION = booleanPreferencesKey("has_session")
    }

    data class Tokens(
        val accessToken: String,
        val refreshToken: String,
        val tokenType: String,
        val expiresAtMillis: Long,
    )

    suspend fun tokens(): Tokens? {
        val prefs = context.authDataStore.data.first()
        val access = prefs[Keys.ACCESS_TOKEN] ?: return null
        val refresh = prefs[Keys.REFRESH_TOKEN] ?: return null
        return Tokens(
            accessToken = access,
            refreshToken = refresh,
            tokenType = prefs[Keys.TOKEN_TYPE] ?: "Bearer",
            expiresAtMillis = prefs[Keys.EXPIRES_AT] ?: 0L,
        )
    }

    suspend fun saveTokens(tokens: Tokens) {
        context.authDataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = tokens.accessToken
            prefs[Keys.REFRESH_TOKEN] = tokens.refreshToken
            prefs[Keys.TOKEN_TYPE] = tokens.tokenType
            prefs[Keys.EXPIRES_AT] = tokens.expiresAtMillis
            prefs[Keys.HAS_SESSION] = true
        }
    }

    suspend fun updateAccessToken(accessToken: String, expiresAtMillis: Long, tokenType: String) {
        context.authDataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.EXPIRES_AT] = expiresAtMillis
            prefs[Keys.TOKEN_TYPE] = tokenType
        }
    }

    suspend fun cookie(): String? = context.authDataStore.data.first()[Keys.COOKIE]

    suspend fun saveCookie(cookie: String) {
        context.authDataStore.edit { prefs ->
            prefs[Keys.COOKIE] = cookie
            prefs[Keys.HAS_SESSION] = true
        }
    }

    suspend fun hasSession(): Boolean = context.authDataStore.data.first()[Keys.HAS_SESSION] ?: false

    suspend fun clear() {
        context.authDataStore.edit { it.clear() }
    }
}
