package com.lostf1sh.pixelplayeross.data.innertube.parser

import com.lostf1sh.pixelplayeross.data.innertube.InnerTubeMapper
import com.lostf1sh.pixelplayeross.data.innertube.model.YouTubeArtistRef
import com.lostf1sh.pixelplayeross.data.innertube.model.YouTubeSong
import com.lostf1sh.pixelplayeross.data.model.Song
import org.json.JSONObject

/** A page of a radio / up-next queue. */
data class NextPage(
    val songs: List<Song>,
    val continuation: String?,
)

/**
 * Parses the `next` (watch) response — the source of radio, autoplay and the up-next queue.
 * Tracks live under a `playlistPanelRenderer`; the continuation token drives the endless queue.
 */
internal object NextParser {

    fun parse(root: JSONObject): NextPage {
        val panel = playlistPanel(root) ?: return NextPage(emptyList(), null)
        val contents = panel.optJSONArray("contents")
        val songs = mutableListOf<Song>()
        val seen = HashSet<String>()

        if (contents != null) {
            for (i in 0 until contents.length()) {
                val renderer = contents.optJSONObject(i)?.optJSONObject("playlistPanelVideoRenderer")
                    ?: continue
                val song = parsePanelVideo(renderer) ?: continue
                if (seen.add(song.videoId)) songs.add(InnerTubeMapper.toSong(song))
            }
        }

        val continuation = panel.optJSONArray("continuations")
            ?.optJSONObject(0)
            ?.let { it.optJSONObject("nextRadioContinuationData") ?: it.optJSONObject("nextContinuationData") }
            ?.optString("continuation")
            ?.takeIf { it.isNotBlank() }

        return NextPage(songs, continuation)
    }

    private fun playlistPanel(root: JSONObject): JSONObject? {
        // Continuation response.
        root.optJSONObject("continuationContents")
            ?.optJSONObject("playlistPanelContinuation")
            ?.let { return it }

        return root.optJSONObject("contents")
            ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
            ?.optJSONObject("tabbedRenderer")
            ?.optJSONObject("watchNextTabbedResultsRenderer")
            ?.optJSONArray("tabs")
            ?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicQueueRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("playlistPanelRenderer")
    }

    private fun parsePanelVideo(renderer: JSONObject): YouTubeSong? {
        val videoId = renderer.optString("videoId").takeIf { it.isNotBlank() } ?: return null
        val title = YouTubeParsingUtils.joinRuns(renderer.optJSONObject("title")).takeIf { it.isNotBlank() }
            ?: return null

        val byline = renderer.optJSONObject("longBylineText") ?: renderer.optJSONObject("shortBylineText")
        val artists = YouTubeParsingUtils.runsWithBrowseId(byline, prefix = "UC")
            .map { (name, id) -> YouTubeArtistRef(name, id) }
            .ifEmpty {
                val text = YouTubeParsingUtils.joinRuns(byline).substringBefore(" • ").trim()
                if (text.isNotEmpty()) listOf(YouTubeArtistRef(text, null)) else emptyList()
            }

        val durationText = renderer.optJSONObject("lengthText")?.let { YouTubeParsingUtils.joinRuns(it) }
        val duration = YouTubeParsingUtils.parseDurationText(durationText)

        val thumbnail = YouTubeParsingUtils.bestThumbnail(renderer.optJSONObject("thumbnail"))

        return YouTubeSong(
            videoId = videoId,
            title = title,
            artists = artists,
            album = null,
            durationSeconds = duration,
            thumbnailUrl = thumbnail,
        )
    }
}
