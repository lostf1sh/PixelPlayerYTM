package com.lostf1sh.pixelplayeross.presentation.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import timber.log.Timber
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.lostf1sh.pixelplayeross.data.model.YtAccountInfo
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
            val accountInfo by viewModel.accountInfo.collectAsStateWithLifecycle()
            SignedInContent(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                accountInfo = accountInfo,
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
                    val saved = viewModel.onCookiesCaptured(cookie)
                    if (saved) {
                        navController.popBackStack()
                    }
                    saved
                },
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginWebView(
    modifier: Modifier,
    onCookieCaptured: (String?) -> Boolean,
) {
    // The cookie jar commits the SAPISID cookies asynchronously relative to page
    // navigation — after a 2FA challenge the final music.youtube.com land often fires
    // onPageFinished *before* the session cookies are visible, and no further page
    // loads happen. So capture by polling the jar, not by page events: flush + read
    // every half second until a usable session shows up.
    LaunchedEffect(Unit) {
        val cookieManager = CookieManager.getInstance()
        while (true) {
            cookieManager.flush()
            val cookie = cookieManager.getCookie("https://music.youtube.com")
            if (onCookieCaptured(cookie)) break
            delay(500)
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)

            WebView(context).apply {
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // Turn the WebView's UA into a genuine mobile-Chrome one consistent with the
                // device: strip the three embedded-browser tells Google keys on — "; wv",
                // the "Version/4.0" token (real Chrome has no Version/), and the "Build/…"
                // fragment. A desktop UA would be worse (Windows string on an Android device).
                settings.userAgentString = settings.userAgentString
                    .replace("; wv", "")
                    .replace(Regex("""Version/[0-9.]+ """), "")
                    .replace(Regex(""" Build/[^)]+"""), "")
                // Android auto-injects `X-Requested-With: <package>` on every request, which
                // is Google's primary "embedded WebView" tell behind the "browser may not be
                // secure" block. An empty allow-list sends it to no origin, suppressing it.
                val canSuppress = WebViewFeature.isFeatureSupported(
                    WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST
                )
                Timber.tag("YtLogin").d(
                    "requested-with suppression supported=%s; ua=%s",
                    canSuppress, settings.userAgentString,
                )
                if (canSuppress) {
                    WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
                }

                // Keep navigation in-view (the default handler punts to the browser);
                // cookie capture happens in the polling loop above, not on page events.
                webViewClient = WebViewClient()
                loadUrl("https://accounts.google.com/ServiceLogin?continue=https://music.youtube.com/")
            }
        },
    )
}

@Composable
private fun SignedInContent(
    modifier: Modifier,
    accountInfo: YtAccountInfo?,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (accountInfo?.avatarUrl != null) {
            AsyncImage(
                model = accountInfo.avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape),
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = accountInfo?.name ?: "You're signed in",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = accountInfo?.handle
                ?: accountInfo?.email
                ?: "Your library, mixes, and likes come from this account.",
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
