package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.network.youtube.auth.YtAccountStore
import com.lostf1sh.pixelplayeross.data.network.youtube.auth.YtOAuthClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Runs the TV device-code sign-in: fetch a user code, show it, poll until the user
 * authorizes it at youtube.com/activate in a real browser. Google blocks account login
 * inside WebViews, so this is the only reliable in-app route.
 */
@HiltViewModel
class YtLoginViewModel @Inject constructor(
    private val oauth: YtOAuthClient,
    private val accountStore: YtAccountStore,
) : ViewModel() {

    sealed interface Step {
        data object Preparing : Step
        data class CodeReady(val userCode: String, val verificationUrl: String) : Step
        data object Success : Step
        data class Failed(val message: String) : Step
    }

    val isSignedIn: StateFlow<Boolean> = accountStore.isSignedIn

    private val _step = MutableStateFlow<Step>(Step.Preparing)
    val step: StateFlow<Step> = _step.asStateFlow()

    private var flowJob: Job? = null

    /** Kick off (or restart) the device-code flow. Safe to call repeatedly. */
    fun start() {
        if (flowJob?.isActive == true) return
        flowJob = viewModelScope.launch { runFlow() }
    }

    fun retry() {
        flowJob?.cancel()
        flowJob = viewModelScope.launch { runFlow() }
    }

    fun signOut() {
        flowJob?.cancel()
        accountStore.signOut()
        _step.value = Step.Preparing
    }

    private suspend fun runFlow() {
        _step.value = Step.Preparing
        val code = oauth.requestDeviceCode()
        if (code == null) {
            _step.value = Step.Failed("Couldn't reach Google — check your connection and retry.")
            return
        }
        _step.value = Step.CodeReady(code.userCode, code.verificationUrl)

        var intervalSeconds = code.pollIntervalSeconds.coerceAtLeast(1L)
        val deadline = System.currentTimeMillis() + code.expiresInSeconds * 1000L
        var consecutiveNetworkFailures = 0

        while (System.currentTimeMillis() < deadline) {
            delay(intervalSeconds * 1000L)
            when (val result = oauth.pollForTokens(code.deviceCode)) {
                is YtOAuthClient.PollResult.Success -> {
                    accountStore.saveTokens(
                        accessToken = result.tokens.accessToken,
                        refreshToken = result.tokens.refreshToken,
                        expiresInSeconds = result.tokens.expiresInSeconds,
                    )
                    _step.value = Step.Success
                    return
                }
                YtOAuthClient.PollResult.Pending -> consecutiveNetworkFailures = 0
                YtOAuthClient.PollResult.SlowDown -> intervalSeconds += 5
                is YtOAuthClient.PollResult.Failed -> {
                    // Transient network blips shouldn't kill an otherwise valid code.
                    if (result.error == "network" && ++consecutiveNetworkFailures < 5) continue
                    Timber.tag(TAG).w("device flow failed: %s", result.error)
                    _step.value = Step.Failed(
                        if (result.error == "network") "Lost connection while waiting — retry."
                        else "Sign-in was rejected (${result.error}) — retry."
                    )
                    return
                }
            }
        }
        _step.value = Step.Failed("The code expired — get a new one.")
    }

    private companion object {
        const val TAG = "YtLoginViewModel"
    }
}
