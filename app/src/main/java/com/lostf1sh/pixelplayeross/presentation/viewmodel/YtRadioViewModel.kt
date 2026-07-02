package com.lostf1sh.pixelplayeross.presentation.viewmodel

import androidx.lifecycle.ViewModel
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.model.YtTrack
import com.lostf1sh.pixelplayeross.data.model.toSong
import com.lostf1sh.pixelplayeross.data.youtube.YouTubeRepository
import com.lostf1sh.pixelplayeross.data.youtube.YtRadioSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject

/**
 * Shared radio-seeding helper for every YTM screen: turns one track into an endless
 * radio queue (seed track first). Falls back to just the seed if the call fails, so
 * a tap always plays something.
 *
 * Also arms [YtRadioSession] with the page's continuation so the player keeps the
 * radio going past the first page. The session key must match the queue name the
 * screens pass to the player — all of them use `"<track title> Radio"`.
 */
@HiltViewModel
class YtRadioViewModel @Inject constructor(
    private val repository: YouTubeRepository,
    private val radioSession: YtRadioSession,
) : ViewModel() {

    suspend fun radioSongsFor(track: YtTrack): List<Song> = try {
        val page = repository.radioFor(track.videoId)
        radioSession.start(queueName = "${track.title} Radio", continuation = page.continuation)
        val radio = page.tracks.map { it.toSong() }
        when {
            radio.isEmpty() -> listOf(track.toSong())
            radio.first().id == track.videoId -> radio
            else -> listOf(track.toSong()) + radio.filter { it.id != track.videoId }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.tag(TAG).w(e, "radio for %s failed", track.videoId)
        listOf(track.toSong())
    }

    private companion object {
        const val TAG = "YtRadioViewModel"
    }
}
