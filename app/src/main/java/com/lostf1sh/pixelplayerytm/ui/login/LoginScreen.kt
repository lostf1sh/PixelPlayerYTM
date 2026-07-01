package com.lostf1sh.pixelplayerytm.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayerytm.data.youtube.auth.AuthManager
import com.lostf1sh.pixelplayerytm.data.youtube.auth.YouTubeOAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val userCode: String? = null,
    val verificationUrl: String = "https://www.google.com/device",
    val isLoading: Boolean = true,
    val isDone: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val oauth: YouTubeOAuth,
    private val authManager: AuthManager,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        start()
    }

    fun start() {
        _state.value = LoginUiState(isLoading = true)
        viewModelScope.launch {
            runCatching { oauth.requestDeviceCode() }
                .onSuccess { code ->
                    _state.value = LoginUiState(
                        userCode = code.userCode,
                        verificationUrl = code.verificationUrl,
                        isLoading = false,
                    )
                    pollForTokens(code)
                }
                .onFailure { e ->
                    _state.value = LoginUiState(isLoading = false, error = e.message)
                }
        }
    }

    private suspend fun pollForTokens(code: YouTubeOAuth.DeviceCode) {
        var interval = code.interval.coerceAtLeast(2)
        val deadline = System.currentTimeMillis() + code.expiresIn * 1000L
        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000L)
            when (val result = oauth.poll(code.deviceCode)) {
                is YouTubeOAuth.PollResult.Success -> {
                    authManager.onLoggedIn(result.tokens)
                    _state.value = _state.value.copy(isDone = true)
                    return
                }

                is YouTubeOAuth.PollResult.SlowDown -> interval += 5
                is YouTubeOAuth.PollResult.Pending -> Unit
                is YouTubeOAuth.PollResult.Failed -> {
                    _state.value = _state.value.copy(error = result.reason)
                    return
                }
            }
        }
        _state.value = _state.value.copy(error = "Code expired — try again")
    }
}

@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onLoggedIn: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(state.isDone) {
        if (state.isDone) onLoggedIn()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Sign in with Google",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when {
                state.isLoading -> CircularProgressIndicator()

                state.error != null -> {
                    Text(
                        text = state.error ?: "Something went wrong",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = viewModel::start) { Text("Try again") }
                }

                else -> {
                    Text(
                        text = "On any device, open",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = state.verificationUrl
                            .removePrefix("https://")
                            .removePrefix("http://"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Text(
                        text = "and enter this code:",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = {
                            state.userCode?.let { clipboard.setText(AnnotatedString(it)) }
                        },
                    ) {
                        Text(
                            text = state.userCode.orEmpty(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 6.sp,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Tap the code to copy it. Waiting for you to finish signing in…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    CircularProgressIndicator()
                }
            }
        }
    }
}
