package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayeross.data.model.YtAccountInfo
import com.lostf1sh.pixelplayeross.data.network.youtube.auth.YtAccountStore
import com.lostf1sh.pixelplayeross.data.youtube.YouTubeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Backs the WebView Google sign-in: the screen feeds every cookie snapshot it sees for
 * music.youtube.com through [onCookiesCaptured]; once one contains a SAPISID the session
 * is persisted and [isSignedIn] flips. (TV device-code OAuth is dead on InnerTube —
 * cookies are the only auth Google still accepts from third-party clients.)
 */
@HiltViewModel
class YtLoginViewModel @Inject constructor(
    private val accountStore: YtAccountStore,
    private val youTubeRepository: YouTubeRepository,
) : ViewModel() {

    val isSignedIn: StateFlow<Boolean> = accountStore.isSignedIn

    val accountInfo: StateFlow<YtAccountInfo?> = accountStore.accountInfo

    /** Returns true when the cookie string held a usable session and was stored. */
    fun onCookiesCaptured(cookie: String?): Boolean {
        if (cookie.isNullOrBlank()) return false
        val saved = accountStore.saveCookie(cookie)
        if (saved) {
            Timber.tag(TAG).d("captured signed-in cookie session")
            viewModelScope.launch {
                runCatching { youTubeRepository.refreshAccountInfo() }
                    .onFailure { Timber.tag(TAG).w(it, "account info fetch failed") }
            }
        }
        return saved
    }

    fun signOut() {
        accountStore.signOut()
    }

    private companion object {
        const val TAG = "YtLoginViewModel"
    }
}
