package com.lostf1sh.pixelplayeross.presentation.youtube.auth

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.lostf1sh.pixelplayeross.data.youtube.auth.YouTubeAccountManager
import com.lostf1sh.pixelplayeross.ui.theme.PixelPlayerTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Google sign-in for YouTube Music via a WebView. Loads the Google login page and, once
 * the user is redirected to music.youtube.com with a valid session, harvests the cookie
 * (must contain `__Secure-3PAPISID`/`SAPISID`), hands it to [YouTubeAccountManager], and finishes.
 */
@AndroidEntryPoint
class YouTubeLoginActivity : ComponentActivity() {

    @Inject
    lateinit var accountManager: YouTubeAccountManager

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PixelPlayerTheme {
                var loading by remember { mutableStateOf(true) }
                Surface(color = MaterialTheme.colorScheme.background) {
                    Scaffold { padding ->
                        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { context ->
                                    WebView(context).apply {
                                        setupCookies()
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        settings.userAgentString = DESKTOP_UA
                                        webViewClient = object : WebViewClient() {
                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                loading = false
                                                if (url != null && url.contains("music.youtube.com")) {
                                                    tryCaptureCookie()
                                                }
                                            }
                                        }
                                        loadUrl(LOGIN_URL)
                                    }
                                },
                            )
                            if (loading) {
                                LinearWavyProgressIndicator(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun WebView.setupCookies() {
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(this@setupCookies, true)
        }
    }

    private fun tryCaptureCookie() {
        val cookie = CookieManager.getInstance().getCookie("https://music.youtube.com") ?: return
        if (cookie.contains("SAPISID")) {
            Timber.d("Captured YouTube Music session cookie")
            accountManager.signIn(cookie)
            setResult(RESULT_OK)
            finish()
        }
    }

    private companion object {
        const val LOGIN_URL =
            "https://accounts.google.com/ServiceLogin?service=youtube&continue=https://music.youtube.com/"
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
