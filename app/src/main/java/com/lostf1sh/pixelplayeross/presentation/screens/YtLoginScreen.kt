package com.lostf1sh.pixelplayeross.presentation.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lostf1sh.pixelplayeross.presentation.viewmodel.YtLoginViewModel

/**
 * Google sign-in via an in-app WebView. Google blocks account login inside default
 * WebViews ("this browser may not be secure"), so we spoof a desktop Chrome UA. Once
 * the user lands on music.youtube.com signed in, the cookie jar carries a SAPISID
 * session; we capture it and persist. If already signed in, offers sign-out instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YtLoginScreen(
    navController: NavHostController,
    viewModel: YtLoginViewModel = hiltViewModel(),
) {
    val isSignedIn by viewModel.isSignedIn.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("YouTube Music account") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (isSignedIn) {
            SignedInContent(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                onSignOut = {
                    // Drop the WebView's cookie jar too — otherwise reopening this screen
                    // silently re-captures the just-signed-out session.
                    CookieManager.getInstance().removeAllCookies(null)
                    viewModel.signOut()
                },
            )
        } else {
            LoginWebView(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                onCookieCaptured = { cookie ->
                    if (viewModel.onCookiesCaptured(cookie)) {
                        navController.popBackStack()
                    }
                },
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginWebView(
    modifier: Modifier,
    onCookieCaptured: (String?) -> Unit,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)

            WebView(context).apply {
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Desktop Chrome UA: Google refuses login to the stock Android WebView UA.
                settings.userAgentString =
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                // Android auto-injects `X-Requested-With: <package>` on every request, which
                // is Google's primary "embedded WebView" tell behind the "browser may not be
                // secure" block. An empty allow-list sends it to no origin, suppressing it.
                if (WebViewFeature.isFeatureSupported(
                        WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST
                    )
                ) {
                    WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Only music.youtube.com carries the SAPISID we need; the login
                        // hops through accounts.google.com first, so check on every land.
                        if (url != null && url.contains("music.youtube.com")) {
                            val cookie = CookieManager.getInstance().getCookie("https://music.youtube.com")
                            onCookieCaptured(cookie)
                        }
                    }
                }
                loadUrl("https://accounts.google.com/ServiceLogin?continue=https://music.youtube.com/")
            }
        },
    )
}

@Composable
private fun SignedInContent(
    modifier: Modifier,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "You're signed in",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Your library, mixes, and likes come from this account.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        FilledTonalButton(onClick = onSignOut) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Logout,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Sign out")
        }
    }
}
