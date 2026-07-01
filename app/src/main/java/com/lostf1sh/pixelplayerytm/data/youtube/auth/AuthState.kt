package com.lostf1sh.pixelplayerytm.data.youtube.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory view of the current authentication state. Backed by [AuthStore];
 * [AuthManager] keeps this in sync so hot paths (stream quality selection,
 * interceptor) can read it without suspending.
 */
@Singleton
class AuthState @Inject constructor() {
    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    fun isLoggedIn(): Boolean = _loggedIn.value

    fun setLoggedIn(value: Boolean) {
        _loggedIn.value = value
    }
}
