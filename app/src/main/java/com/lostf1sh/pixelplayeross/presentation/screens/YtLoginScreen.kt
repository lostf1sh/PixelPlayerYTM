package com.lostf1sh.pixelplayeross.presentation.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
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
 * Google sign-in via an in-app WebView, on the music-templated ServiceLogin flow that
 * surfaces the device's already-signed-in Google accounts for one-tap sign-in. Once the
 * user lands on music.youtube.com signed in, the cookie jar carries a SAPISID session;
 * we capture it and persist. If already signed in, offers sign-out instead.
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
                // Deliberately keep the STOCK WebView UA (with "; wv" and Build/…) and the
                // X-Requested-With header: on this music ServiceLogin flow Google treats a
                // recognized Android WebView as a device sign-in and offers the phone's
                // already-signed-in Google accounts as one-tap choices — no password, no
                // 2FA re-entry. Spoofing a browser UA here (the old approach) suppressed
                // that account picker and forced a full manual login.
                webViewClient = WebViewClient()
                loadUrl(LOGIN_URL)
            }
        },
        onRelease = { view ->
            view.stopLoading()
            view.destroy()
        },
    )
}

/**
 * The music-templated Google sign-in used by every working third-party YTM client
 * (InnerTune, Metrolist, PixelMusic): unlike the generic ServiceLogin, this flow is not
 * hit by Google's "this browser may not be secure" WebView block and surfaces the
 * device-account chooser.
 */
private const val LOGIN_URL =
    "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&uilel=3&passive=true&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Ddesktop%26hl%3Den%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F&hl=en"

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
