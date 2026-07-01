package com.lostf1sh.pixelplayeross.data.network.youtube.auth

import com.lostf1sh.pixelplayeross.di.YouTubeBaseHttp
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
 * YouTube "TV device code" OAuth — the WebView-free sign-in used by InnerTune / ViMusic.
 *
 * Google refuses account login inside embedded WebViews ("this browser or app may not be
 * secure"), so we use the TV app's OAuth device flow instead: ask for a short user code,
 * the user types it at youtube.com/activate in any real browser, then we poll for tokens.
 * The access token authenticates InnerTube via `Authorization: Bearer …`.
 *
 * The TV client credentials are the public, well-known "YouTube on TV" app credentials
 * (the same ones yt-dlp ships) — not secret user data.
 */
@Singleton
class YtOAuthClient @Inject constructor(
    @YouTubeBaseHttp private val httpClient: OkHttpClient,
) {
    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUrl: String,
        val pollIntervalSeconds: Long,
        val expiresInSeconds: Long,
    )

    data class Tokens(
        val accessToken: String,
        val refreshToken: String?,
        val expiresInSeconds: Long,
    )

    sealed interface PollResult {
        data class Success(val tokens: Tokens) : PollResult
        data object Pending : PollResult
        data object SlowDown : PollResult
        data class Failed(val error: String) : PollResult
    }

    /** Step 1 — request a user code to display and a device code to poll with. */
    suspend fun requestDeviceCode(): DeviceCode? = withContext(Dispatchers.IO) {
        val json = post(
            "$OAUTH_BASE/device/code",
            FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("scope", SCOPE)
                .build(),
        ) ?: return@withContext null
        DeviceCode(
            deviceCode = json.optString("device_code"),
            userCode = json.optString("user_code"),
            verificationUrl = json.optString("verification_url").ifBlank { ACTIVATE_URL },
            pollIntervalSeconds = json.optLong("interval", 5L),
            expiresInSeconds = json.optLong("expires_in", 1800L),
        ).takeIf { it.deviceCode.isNotBlank() && it.userCode.isNotBlank() }
    }

    /** Step 2 — poll until the user authorizes the code (or it fails / expires). */
    suspend fun pollForTokens(deviceCode: String): PollResult = withContext(Dispatchers.IO) {
        val json = post(
            "$OAUTH_BASE/token",
            FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("code", deviceCode)
                .add("grant_type", DEVICE_GRANT)
                .build(),
        ) ?: return@withContext PollResult.Failed("network")

        json.optString("access_token").takeIf { it.isNotBlank() }?.let { access ->
            return@withContext PollResult.Success(
                Tokens(
                    accessToken = access,
                    refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() },
                    expiresInSeconds = json.optLong("expires_in", 3600L),
                )
            )
        }
        when (val error = json.optString("error")) {
            "authorization_pending" -> PollResult.Pending
            "slow_down" -> PollResult.SlowDown
            else -> PollResult.Failed(error.ifBlank { "unknown" })
        }
    }

    /** Exchange a refresh token for a fresh access token; null on failure. */
    suspend fun refresh(refreshToken: String): Tokens? = withContext(Dispatchers.IO) {
        val json = post(
            "$OAUTH_BASE/token",
            FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("refresh_token", refreshToken)
                .add("grant_type", "refresh_token")
                .build(),
        ) ?: return@withContext null
        json.optString("access_token").takeIf { it.isNotBlank() }?.let { access ->
            Tokens(
                accessToken = access,
                // A refresh response usually omits the refresh token — keep the existing one.
                refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() } ?: refreshToken,
                expiresInSeconds = json.optLong("expires_in", 3600L),
            )
        }
    }

    private fun post(url: String, body: FormBody): JSONObject? = try {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", TV_USER_AGENT)
            .post(body)
            .build()
        httpClient.newCall(request).execute().use { response ->
            response.body?.string()?.takeIf { it.isNotBlank() }?.let(::JSONObject)
        }
    } catch (e: Exception) {
        Timber.tag(TAG).w(e, "OAuth POST failed: %s", url)
        null
    }

    private companion object {
        const val TAG = "YtOAuthClient"
        const val OAUTH_BASE = "https://www.youtube.com/o/oauth2"
        const val ACTIVATE_URL = "https://www.youtube.com/activate"
        const val DEVICE_GRANT = "http://oauth.net/grant_type/device/1.0"
        // Public "YouTube on TV" OAuth client — not secret user data.
        const val CLIENT_ID = "861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com"
        const val CLIENT_SECRET = "SboVhoG9s0rNafixCSGGKXAT"
        const val SCOPE = "http://gdata.youtube.com " +
            "https://www.googleapis.com/auth/youtube-paid-content " +
            "https://www.googleapis.com/auth/youtube"
        const val TV_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Safari/537.36"
    }
}
