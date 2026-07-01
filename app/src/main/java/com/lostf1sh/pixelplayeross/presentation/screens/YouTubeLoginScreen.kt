package com.lostf1sh.pixelplayeross.presentation.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lostf1sh.pixelplayeross.presentation.viewmodel.YouTubeLoginViewModel
import com.lostf1sh.pixelplayeross.presentation.youtube.auth.YouTubeLoginActivity

/**
 * WebView-free YouTube Music sign-in. Shows a short code the user enters at
 * youtube.com/activate in any real browser (Google blocks account login inside embedded
 * WebViews). Polls in the background and pops back on success.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YouTubeLoginScreen(
    navController: NavHostController,
    paddingValues: PaddingValues,
    viewModel: YouTubeLoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.start() }
    LaunchedEffect(state) {
        if (state is YouTubeLoginViewModel.LoginState.Success) {
            navController.popBackStack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .padding(top = paddingValues.calculateTopPadding()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Sign in to YouTube Music",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        when (val s = state) {
            YouTubeLoginViewModel.LoginState.Loading -> {
                LoadingIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Preparing sign-in…", style = MaterialTheme.typography.bodyMedium)
            }

            is YouTubeLoginViewModel.LoginState.AwaitingCode -> {
                Text(
                    text = "1.  Open the page below\n2.  Enter this code\n3.  Approve on your Google account",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Card(shape = RoundedCornerShape(16.dp)) {
                    Text(
                        text = s.userCode,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 6.sp,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Button(onClick = { context.openUrl(s.verificationUrl) }) {
                    Icon(Icons.Rounded.OpenInBrowser, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open ${s.verificationUrl.removePrefix("https://")}")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { context.copyToClipboard(s.userCode) }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Copy code")
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Waiting for you to approve…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is YouTubeLoginViewModel.LoginState.Error -> {
                Text(s.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { viewModel.retry() }) { Text("Try again") }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    context.startActivity(Intent(context, YouTubeLoginActivity::class.java))
                }) { Text("Use in-app browser instead") }
            }

            YouTubeLoginViewModel.LoginState.Success -> {
                LoadingIndicator()
            }
        }
    }
}

private fun Context.openUrl(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("YouTube code", text))
}
