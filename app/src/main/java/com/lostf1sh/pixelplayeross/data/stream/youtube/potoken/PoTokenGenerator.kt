package com.lostf1sh.pixelplayeross.data.stream.youtube.potoken

import android.content.Context
import android.webkit.CookieManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mints BotGuard PoTokens for playback. Holds one [PoTokenWebView] engine per session
 * (visitorData), reusing it until it expires (~50 min). The session ("streaming") token is
 * minted once per engine; a per-video ("player") token is minted per call.
 *
 * Adapted from Metrolist. Returns null (letting playback proceed unthrottled-but-throttled,
 * or a fallback client take over) whenever the device lacks a usable WebView or the mint
 * times out.
 */
@Singleton
class PoTokenGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val webViewSupported by lazy { runCatching { CookieManager.getInstance() }.isSuccess }
    private var webViewBadImpl = false

    private val lock = Mutex()
    private var sessionId: String? = null
    private var streamingPot: String? = null
    private var generator: PoTokenWebView? = null

    /**
     * Boot the WebView engine and mint the session token ahead of time so the first real
     * [getWebClientPoToken] (i.e. the first track played) doesn't pay the ~2–5 s cold start.
     * Best-effort and silent on failure.
     */
    suspend fun preWarm(session: String) {
        if (!webViewSupported || webViewBadImpl) return
        try {
            withTimeout(COLD_START_TIMEOUT_MS) {
                lock.withLock {
                    if (generator == null || generator!!.isExpired || sessionId != session) {
                        sessionId = session
                        withContext(Dispatchers.Main) { generator?.close() }
                        generator = PoTokenWebView.getNewPoTokenGenerator(context)
                        streamingPot = generator!!.generatePoToken(session)
                    }
                }
            }
            Timber.tag(TAG).d("PoToken engine pre-warmed")
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "prewarm failed (non-fatal)")
        }
    }

    /** Release the ~50 MB WebView (e.g. on TRIM_MEMORY_UI_HIDDEN); recreated on next use. */
    suspend fun onAppBackgrounded() = lock.withLock { destroy() }

    suspend fun getWebClientPoToken(videoId: String, sessionId: String): PoTokenResult? {
        if (!webViewSupported || webViewBadImpl) {
            Timber.tag(TAG).d("WebView unavailable (supported=%s, bad=%s)", webViewSupported, webViewBadImpl)
            return null
        }
        return try {
            withTimeout(POTOKEN_TIMEOUT_MS) {
                mint(videoId, sessionId, forceRecreate = false)
            }
        } catch (e: TimeoutCancellationException) {
            Timber.tag(TAG).w("poToken generation timed out after %dms; proceeding without it", POTOKEN_TIMEOUT_MS)
            lock.withLock { destroy() }
            null
        } catch (e: BadWebViewException) {
            Timber.tag(TAG).e(e, "WebView is broken; disabling PoToken")
            webViewBadImpl = true
            null
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "poToken generation failed: %s", e.message)
            null
        }
    }

    private suspend fun mint(videoId: String, session: String, forceRecreate: Boolean): PoTokenResult {
        val (gen, streaming, recreated) = lock.withLock {
            val recreate = forceRecreate || generator == null || generator!!.isExpired || sessionId != session
            if (recreate) {
                sessionId = session
                withContext(Dispatchers.Main) { generator?.close() }
                generator = PoTokenWebView.getNewPoTokenGenerator(context)
                // The streaming (session) poToken must be minted once, before any player token.
                streamingPot = generator!!.generatePoToken(session)
            }
            Triple(generator!!, streamingPot!!, recreate)
        }

        val playerPot = try {
            gen.generatePoToken(videoId)
        } catch (t: Throwable) {
            if (recreated) throw t
            Timber.tag(TAG).e(t, "player token mint failed, retrying with a fresh engine")
            return mint(videoId, session, forceRecreate = true)
        }

        return PoTokenResult(
            playerRequestPoToken = streaming,
            streamingDataPoToken = playerPot,
        )
    }

    private suspend fun destroy() {
        withContext(Dispatchers.Main) { generator?.close() }
        generator = null
        streamingPot = null
        sessionId = null
    }

    private companion object {
        const val TAG = "PoTokenGenerator"
        const val POTOKEN_TIMEOUT_MS = 8_000L
        const val COLD_START_TIMEOUT_MS = 15_000L
    }
}
