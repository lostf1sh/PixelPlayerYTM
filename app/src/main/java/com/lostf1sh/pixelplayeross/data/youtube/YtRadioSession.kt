package com.lostf1sh.pixelplayeross.data.youtube

import com.lostf1sh.pixelplayeross.data.model.YtTrack
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The live continuation token of the radio queue that's currently playing, so the player
 * can keep appending tracks (endless autoplay) as the listener nears the queue's end.
 *
 * Armed by [com.lostf1sh.pixelplayeross.presentation.viewmodel.YtRadioViewModel] when a
 * radio is seeded, keyed by the queue name the seeding screen passes to the player
 * ("<seed title> Radio"). A queue with any other name simply never matches, so starting
 * an album/playlist naturally puts the session dormant without explicit teardown.
 */
@Singleton
class YtRadioSession @Inject constructor(
    private val repository: YouTubeRepository,
) {
    @Volatile
    private var queueName: String? = null

    @Volatile
    private var continuation: String? = null

    private val fetchMutex = Mutex()

    fun start(queueName: String, continuation: String?) {
        this.queueName = queueName
        this.continuation = continuation
    }

    fun isActiveFor(playingQueueName: String?): Boolean =
        playingQueueName != null && playingQueueName == queueName && continuation != null

    /**
     * The next page of radio tracks for [playingQueueName], advancing the continuation.
     * Empty when the session doesn't match, is exhausted, or the fetch fails (the token
     * is kept in that case so a later attempt can retry).
     */
    suspend fun nextTracks(playingQueueName: String?): List<YtTrack> = fetchMutex.withLock {
        if (!isActiveFor(playingQueueName)) return emptyList()
        val token = continuation ?: return emptyList()
        val page = try {
            repository.radioMore(token)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "radio continuation fetch failed")
            return emptyList()
        }
        continuation = page.continuation
        page.tracks
    }

    private companion object {
        const val TAG = "YtRadioSession"
    }
}
