package com.lostf1sh.pixelplayeross.data.youtube.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube TV "device code" OAuth — the WebView-free sign-in used by InnerTune/ViMusic.
 *
 * Google blocks Google-account login inside embedded WebViews ("this browser or app may not
 * be secure"), so instead we use the TV app's OAuth device flow: request a short user code,
 * the user enters it at youtube.com/activate in any real browser, and we poll for tokens.
 * The resulting access token authenticates InnerTube requests via `Authorization: Bearer`.
 *
 * The TV client credentials below are the public, well-known "YouTube on TV" app credentials
 * (same ones yt-dlp uses); they are not secret user data.
 */
@Singleton
class YouTubeOAuth @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUrl: String,
        val intervalSeconds: Long,
        val expiresInSeconds: Long,
    )

    data class Tokens(
        val accessToken: String,
        val refreshToken: String?,
        val expiresInSeconds: Long,
    )

    sealed interface PollResult {
        data class Success(val tokens: Tokens) : PollResult
        object Pending : PollResult
        object SlowDown : PollResult
        data class Failed(val error: String) : PollResult
    }

    /** Step 1: request a user code to show, and a device code to poll with. */
    suspend fun requestDeviceCode(): DeviceCode? = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("scope", SCOPE)
            .build()
        val json = post("$OAUTH_BASE/device/code", body) ?: return@withContext null
        DeviceCode(
            deviceCode = json.optString("device_code"),
            userCode = json.optString("user_code"),
            verificationUrl = json.optString("verification_url").ifBlank { "https://www.youtube.com/activate" },
            intervalSeconds = json.optLong("interval", 5L),
            expiresInSeconds = json.optLong("expires_in", 1800L),
        ).takeIf { it.deviceCode.isNotBlank() && it.userCode.isNotBlank() }
    }

    /** Step 2: poll until the user has entered the code (or it fails/expires). */
    suspend fun pollForTokens(deviceCode: String): PollResult = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .add("code", deviceCode)
            .add("grant_type", "http://oauth.net/grant_type/device/1.0")
            .build()
        val json = post("$OAUTH_BASE/token", body) ?: return@withContext PollResult.Failed("network")

        json.optString("access_token").takeIf { it.isNotBlank() }?.let { access ->
            return@withContext PollResult.Success(
                Tokens(
                    accessToken = access,
                    refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() },
                    expiresInSeconds = json.optLong("expires_in", 3600L),
                )
            )
        }
        when (json.optString("error")) {
            "authorization_pending" -> PollResult.Pending
            "slow_down" -> PollResult.SlowDown
            else -> PollResult.Failed(json.optString("error").ifBlank { "unknown" })
        }
    }

    /** Refresh an expired access token. Returns null on failure. */
    suspend fun refresh(refreshToken: String): Tokens? = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .add("refresh_token", refreshToken)
            .add("grant_type", "refresh_token")
            .build()
        val json = post("$OAUTH_BASE/token", body) ?: return@withContext null
        json.optString("access_token").takeIf { it.isNotBlank() }?.let { access ->
            Tokens(
                accessToken = access,
                refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() } ?: refreshToken,
                expiresInSeconds = json.optLong("expires_in", 3600L),
            )
        }
    }

    private fun post(url: String, body: FormBody): JSONObject? {
        return try {
            val request = Request.Builder().url(url)
                .header("User-Agent", TV_USER_AGENT)
                .post(body)
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (text.isBlank()) null else JSONObject(text)
            }
        } catch (e: Exception) {
            Timber.w(e, "OAuth POST failed: %s", url)
            null
        }
    }

    private companion object {
        const val OAUTH_BASE = "https://www.youtube.com/o/oauth2"
        // Public "YouTube on TV" OAuth client (not secret user data).
        const val CLIENT_ID = "861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com"
        const val CLIENT_SECRET = "SboVhoG9s0rNafixCSGGKXAT"
        const val SCOPE = "http://gdata.youtube.com https://www.googleapis.com/auth/youtube-paid-content https://www.googleapis.com/auth/youtube"
        const val TV_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
