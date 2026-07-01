package com.lostf1sh.pixelplayeross.data.innertube.parser

import com.lostf1sh.pixelplayeross.data.innertube.model.YouTubeSong
import org.json.JSONArray
import org.json.JSONObject

/** A page of parsed search results plus the token to fetch the next page (null if none). */
data class SearchPage(
    val songs: List<YouTubeSong>,
    val continuation: String?,
)

/**
 * Parses `search` and search-continuation responses into playable [YouTubeSong]s.
 *
 * Rather than depend on the exact shelf a track lands in (which varies with filters and
 * A/B experiments), this walks every `musicResponsiveListItemRenderer` in the tree and
 * keeps the ones that carry a `videoId` — those are the playable items.
 */
internal object SearchParser {

    fun parse(root: JSONObject): SearchPage {
        // Initial search vs. continuation response have different roots.
        val shelfContainers: JSONArray = collectShelfContainers(root)

        val songs = mutableListOf<YouTubeSong>()
        var continuation: String? = null
        val seen = HashSet<String>()

        for (i in 0 until shelfContainers.length()) {
            val container = shelfContainers.optJSONObject(i) ?: continue
            val shelf = container.optJSONObject("musicShelfRenderer")
                ?: container.optJSONObject("musicShelfContinuation")
                ?: continue

            continuation = continuation ?: extractContinuation(shelf)

            val items = shelf.optJSONArray("contents") ?: continue
            for (j in 0 until items.length()) {
                val renderer = items.optJSONObject(j)
                    ?.optJSONObject("musicResponsiveListItemRenderer") ?: continue
                val song = YouTubeParsingUtils.parseSongRow(renderer) ?: continue
                if (seen.add(song.videoId)) songs.add(song)
            }
        }

        return SearchPage(songs, continuation)
    }

    private fun collectShelfContainers(root: JSONObject): JSONArray {
        // Continuation response.
        root.optJSONObject("continuationContents")?.let { cc ->
            return JSONArray().put(JSONObject().put("musicShelfContinuation", cc.optJSONObject("musicShelfContinuation")))
        }

        val sectionContents = root.optJSONObject("contents")
            ?.optJSONObject("tabbedSearchResultsRenderer")
            ?.optJSONArray("tabs")
            ?.optJSONObject(0)
            ?.optJSONObject("tabRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents")
        return sectionContents ?: JSONArray()
    }

    private fun extractContinuation(shelf: JSONObject): String? {
        // Legacy shape.
        shelf.optJSONArray("continuations")
            ?.optJSONObject(0)
            ?.optJSONObject("nextContinuationData")
            ?.optString("continuation")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return null
    }
}
