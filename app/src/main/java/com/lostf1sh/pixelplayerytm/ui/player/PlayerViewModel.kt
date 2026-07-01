package com.lostf1sh.pixelplayerytm.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lostf1sh.pixelplayerytm.data.repository.YouTubeRepository
import com.lostf1sh.pixelplayerytm.data.youtube.auth.AuthState
import com.lostf1sh.pixelplayerytm.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val player: PlayerController,
    private val repository: YouTubeRepository,
    private val authState: AuthState,
) : ViewModel() {

    /** Session-local liked state per videoId (YTM doesn't return it inline). */
    private val _likedVideoIds = MutableStateFlow<Set<String>>(emptySet())
    val likedVideoIds: StateFlow<Set<String>> = _likedVideoIds.asStateFlow()

    val canLike: Boolean get() = authState.isLoggedIn()

    fun toggleLike() {
        val videoId = player.currentSong.value?.videoId ?: return
        if (!authState.isLoggedIn()) return
        val liked = videoId in _likedVideoIds.value
        // Optimistic flip; revert on failure.
        _likedVideoIds.value = if (liked) {
            _likedVideoIds.value - videoId
        } else {
            _likedVideoIds.value + videoId
        }
        viewModelScope.launch {
            val result = if (liked) repository.unlikeSong(videoId) else repository.likeSong(videoId)
            if (result.isFailure || result.getOrNull() == false) {
                _likedVideoIds.value = if (liked) {
                    _likedVideoIds.value + videoId
                } else {
                    _likedVideoIds.value - videoId
                }
            }
        }
    }
}
