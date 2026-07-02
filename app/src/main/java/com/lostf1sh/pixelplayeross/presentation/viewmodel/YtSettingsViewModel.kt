package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.SimpleCache
import com.lostf1sh.pixelplayeross.data.model.YtAudioQuality
import com.lostf1sh.pixelplayeross.data.network.youtube.auth.YtAccountStore
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import com.lostf1sh.pixelplayeross.data.stream.youtube.YtStreamCache
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/** State and actions for the YouTube Music settings section. */
@OptIn(UnstableApi::class)
@HiltViewModel
class YtSettingsViewModel @Inject constructor(
    private val accountStore: YtAccountStore,
    private val userPreferencesRepository: UserPreferencesRepository,
    @param:YtStreamCache private val streamCache: SimpleCache,
) : ViewModel() {

    val isSignedIn: StateFlow<Boolean> = accountStore.isSignedIn

    val normalizationEnabled: StateFlow<Boolean> =
        userPreferencesRepository.ytmNormalizationEnabledFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val audioQuality: StateFlow<YtAudioQuality> =
        userPreferencesRepository.ytmAudioQualityFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YtAudioQuality.AUTO)

    private val _cacheBytes = MutableStateFlow(0L)
    val cacheBytes: StateFlow<Long> = _cacheBytes.asStateFlow()

    init {
        refreshCacheSize()
    }

    fun setNormalizationEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setYtmNormalizationEnabled(enabled) }
    }

    fun setAudioQuality(quality: YtAudioQuality) {
        viewModelScope.launch { userPreferencesRepository.setYtmAudioQuality(quality) }
    }

    fun signOut() {
        accountStore.signOut()
    }

    fun clearStreamCache(onCleared: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val keys = streamCache.keys.toList()
                Timber.tag("YtSettings").d("clearing %d cached resources", keys.size)
                keys.forEach { key ->
                    runCatching { streamCache.removeResource(key) }
                        .onFailure { Timber.tag("YtSettings").w(it, "failed to remove %s", key) }
                }
            }
            refreshCacheSize()
            onCleared()
        }
    }

    private fun refreshCacheSize() {
        viewModelScope.launch {
            _cacheBytes.value = withContext(Dispatchers.IO) {
                runCatching { streamCache.cacheSpace }
                    .onFailure { Timber.w(it, "stream cache size read failed") }
                    .getOrDefault(0L)
            }
        }
    }
}
