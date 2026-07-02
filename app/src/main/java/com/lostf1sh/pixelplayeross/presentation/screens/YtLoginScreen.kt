package com.lostf1sh.pixelplayeross.presentation.screens

import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * Google sign-in via an in-app WebView, opening on the account chooser: every account
 * that ever signed in here stays remembered in the WebView cookie jar (sign-out keeps
 * the jar), so all sign-ins after the first are a one-tap pick. Once the user lands on
 * music.youtube.com signed in, the cookie jar carries a SAPISID session; we capture it
 * and persist. If already signed in, offers sign-out instead.
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
                    // Deliberately KEEP the WebView cookie jar: Google's account chooser
                    // remembers the signed-in accounts that live there, which is what makes
                    // the next sign-in a one-tap pick instead of a full password + 2FA run.
                    // The capture loop only reads cookies once the user lands on
                    // music.youtube.com, so the kept session is never re-captured silently.
                    viewModel.signOut()
                },
            )
        } else {
            // The web account chooser only knows accounts that signed in HERE before, but
            // the DEVICE knows all of the user's Google accounts. Always open the native
            // system account picker (no permission needed) and prefill the picked e-mail
            // into Google's sign-in page — the user never types their address; Google then
            // asks only for that account's verification (password/2FA once per account,
            // or just a phone prompt for passwordless accounts; nothing at all when the
            // jar still holds that account's session). Cancelling the picker falls back
            // to the plain web chooser.
            var startUrl by remember { mutableStateOf<String?>(null) }
            val accountPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                val email = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                startUrl = if (email != null) {
                    "$ACCOUNT_CHOOSER_URL&Email=${Uri.encode(email)}"
                } else {
                    ACCOUNT_CHOOSER_URL
                }
            }
            LaunchedEffect(Unit) {
                if (startUrl != null) return@LaunchedEffect
                runCatching {
                    accountPicker.launch(
                        AccountManager.newChooseAccountIntent(
                            null, null, arrayOf("com.google"), null, null, null, null,
                        )
                    )
                }.onFailure { startUrl = ACCOUNT_CHOOSER_URL }
            }
            startUrl?.let { url ->
                LoginWebView(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    startUrl = url,
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
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LoginWebView(
    modifier: Modifier,
    startUrl: String,
    onCookieCaptured: (String?) -> Boolean,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }

    // The cookie jar commits the SAPISID cookies asynchronously relative to page
    // navigation — after a 2FA challenge the final music.youtube.com land often fires
    // onPageFinished *before* the session cookies are visible, and no further page
    // loads happen. So capture by polling the jar, not by page events: flush + read
    // every half second until a usable session shows up.
    //
    // The poll is gated on the WebView actually being on music.youtube.com: the jar may
    // still hold a valid session from before a sign-out (kept on purpose, it feeds the
    // account chooser), and capturing it from the chooser page would sign the user back
    // in before they had a chance to pick a different account.
    LaunchedEffect(Unit) {
        val cookieManager = CookieManager.getInstance()
        while (true) {
            if (webView?.url?.contains("music.youtube.com") == true) {
                cookieManager.flush()
                val cookie = cookieManager.getCookie("https://music.youtube.com")
                if (onCookieCaptured(cookie)) break
            }
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
                // X-Requested-With header — this music sign-in flow accepts recognized
                // Android WebViews (no "browser may not be secure" block), unlike the
                // generic one the old spoofed-UA approach was working around.
                webViewClient = WebViewClient()
                loadUrl(startUrl)
                webView = this
            }
        },
        onRelease = { view ->
            webView = null
            view.stopLoading()
            view.destroy()
        },
    )
}

/**
 * Google's account chooser, continuing into the music-templated youtube sign-in used by
 * every working third-party YTM client (InnerTune, Metrolist, PixelMusic).
 *
 * With accounts remembered in the WebView cookie jar (any previous in-app sign-in —
 * sign-out keeps the jar for exactly this reason) this shows a one-tap "Choose an
 * account" list; with an empty jar it falls through to the normal sign-in form, which
 * the user only ever has to complete once per account.
 */
private const val ACCOUNT_CHOOSER_URL =
    "https://accounts.google.com/AccountChooser?service=youtube&ltmpl=music&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Ddesktop%26hl%3Den%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F&hl=en"

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
