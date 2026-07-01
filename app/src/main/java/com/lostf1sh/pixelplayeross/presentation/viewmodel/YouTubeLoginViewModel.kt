package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.youtube.auth.YouTubeAccountManager
import com.lostf1sh.pixelplayeross.data.youtube.auth.YouTubeOAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the TV device-code sign-in: fetches a user code, then polls until the user enters
 * it at youtube.com/activate. Exposes a small state machine for [YouTubeLoginScreen].
 */
@HiltViewModel
class YouTubeLoginViewModel @Inject constructor(
    private val accountManager: YouTubeAccountManager,
) : ViewModel() {

    sealed interface LoginState {
        object Loading : LoginState
        data class AwaitingCode(val userCode: String, val verificationUrl: String) : LoginState
        object Success : LoginState
        data class Error(val message: String) : LoginState
    }

    private val _state = MutableStateFlow<LoginState>(LoginState.Loading)
    val state = _state.asStateFlow()

    private var started = false

    fun start() {
        if (started) return
        started = true
        beginFlow()
    }

    fun retry() {
        _state.value = LoginState.Loading
        beginFlow()
    }

    private fun beginFlow() {
        viewModelScope.launch {
            val code = accountManager.requestDeviceCode()
            if (code == null) {
                _state.value = LoginState.Error("Couldn't start sign-in. Check your connection.")
                return@launch
            }
            _state.value = LoginState.AwaitingCode(code.userCode, code.verificationUrl)

            var intervalMs = code.intervalSeconds.coerceAtLeast(2) * 1000L
            val deadline = System.currentTimeMillis() + code.expiresInSeconds * 1000L
            while (System.currentTimeMillis() < deadline) {
                delay(intervalMs)
                when (val result = accountManager.pollForTokens(code.deviceCode)) {
                    is YouTubeOAuth.PollResult.Success -> {
                        accountManager.onOAuthSuccess(result.tokens)
                        _state.value = LoginState.Success
                        return@launch
                    }
                    YouTubeOAuth.PollResult.Pending -> Unit
                    YouTubeOAuth.PollResult.SlowDown -> intervalMs += 5_000L
                    is YouTubeOAuth.PollResult.Failed -> {
                        if (result.error == "access_denied") {
                            _state.value = LoginState.Error("Sign-in was declined.")
                            return@launch
                        }
                        // Transient error — keep polling until the code expires.
                    }
                }
            }
            _state.value = LoginState.Error("The code expired. Please try again.")
        }
    }
}
