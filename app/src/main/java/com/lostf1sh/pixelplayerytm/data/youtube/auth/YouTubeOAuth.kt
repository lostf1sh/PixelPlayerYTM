package com.lostf1sh.pixelplayerytm.data.youtube.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube TV device-code OAuth. Google blocks account sign-in inside embedded
 * WebViews, so the primary login flow is the TV "limited input device" flow:
 * the user visits youtube.com/activate, enters a code, and we poll for tokens.
 *
 * Uses the public YouTube-on-TV client id, the same one ytmusicapi relies on.
 */
@Singleton
class YouTubeOAuth @Inject constructor(
    private val json: Json,
) {
    // Public YouTube TV OAuth client credentials (not secret — shipped in the TV app).
    private val clientId = "861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com"
    private val clientSecret = "SboVhoG9s0rNafixCSGGKXAT"
    private val scope = "https://www.googleapis.com/auth/youtube"

    private val client = OkHttpClient()

    @Serializable
    data class DeviceCode(
        @SerialName("device_code") val deviceCode: String,
        @SerialName("user_code") val userCode: String,
        @SerialName("verification_url") val verificationUrl: String = "https://www.google.com/device",
        @SerialName("expires_in") val expiresIn: Int,
        val interval: Int = 5,
    )

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Int? = null,
        @SerialName("token_type") val tokenType: String? = null,
        val error: String? = null,
    )

    sealed interface PollResult {
        data class Success(val tokens: AuthStore.Tokens) : PollResult
        data object Pending : PollResult
        data object SlowDown : PollResult
        data class Failed(val reason: String) : PollResult
    }

    suspend fun requestDeviceCode(): DeviceCode = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("scope", scope)
            .add("device_id", java.util.UUID.randomUUID().toString().replace("-", ""))
            .add("device_model", "ytlr::")
            .build()
        val request = Request.Builder()
            .url("https://oauth2.googleapis.com/device/code")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) error("Device code request failed: HTTP ${response.code} $text")
            json.decodeFromString(DeviceCode.serializer(), text)
        }
    }

    suspend fun poll(deviceCode: String): PollResult = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("code", deviceCode)
            .add("grant_type", "http://oauth.net/grant_type/device/1.0")
            .build()
        val request = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val parsed = json.decodeFromString(TokenResponse.serializer(), response.body.string())
            when {
                parsed.accessToken != null && parsed.refreshToken != null -> PollResult.Success(
                    AuthStore.Tokens(
                        accessToken = parsed.accessToken,
                        refreshToken = parsed.refreshToken,
                        tokenType = parsed.tokenType ?: "Bearer",
                        expiresAtMillis = System.currentTimeMillis() +
                            ((parsed.expiresIn ?: 3600) * 1000L),
                    ),
                )
                parsed.error == "authorization_pending" -> PollResult.Pending
                parsed.error == "slow_down" -> PollResult.SlowDown
                parsed.error == "access_denied" -> PollResult.Failed("Access denied")
                parsed.error == "expired_token" -> PollResult.Failed("Code expired")
                else -> PollResult.Failed(parsed.error ?: "Unknown error")
            }
        }
    }

    /** Returns a fresh access token (accessToken, expiresAtMillis, tokenType). */
    suspend fun refresh(refreshToken: String): Triple<String, Long, String> = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .build()
        val request = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) error("Token refresh failed: HTTP ${response.code} $text")
            val parsed = json.decodeFromString(TokenResponse.serializer(), text)
            val access = parsed.accessToken ?: error("Refresh response missing access_token")
            Triple(
                access,
                System.currentTimeMillis() + ((parsed.expiresIn ?: 3600) * 1000L),
                parsed.tokenType ?: "Bearer",
            )
        }
    }
}
