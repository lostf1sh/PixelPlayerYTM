package com.lostf1sh.pixelplayeross.data.innertube.parser

import com.lostf1sh.pixelplayeross.data.innertube.model.YouTubeArtistRef
import com.lostf1sh.pixelplayeross.data.innertube.model.YouTubeSong
import org.json.JSONArray
import org.json.JSONObject

/**
 * Shared helpers for walking InnerTube's deeply-nested, renderer-based JSON. Everything
 * here is null-tolerant on purpose: InnerTube shapes drift and vary per result type, so
 * parsers must degrade gracefully rather than throw.
 */
internal object YouTubeParsingUtils {

    /**
     * Parse a `musicResponsiveListItemRenderer` (used by search results, playlist/album
     * tracks, and library rows) into a playable [YouTubeSong]. Null if it has no videoId.
     */
    fun parseSongRow(renderer: JSONObject): YouTubeSong? {
        val videoId = findVideoId(renderer) ?: return null
        val title = joinRuns(flexColumnText(renderer, 0)).takeIf { it.isNotBlank() } ?: return null

        val subtitle = flexColumnText(renderer, 1)
        val artists = runsWithBrowseId(subtitle, prefix = "UC")
            .map { (name, id) -> YouTubeArtistRef(name, id) }
            .ifEmpty {
                val text = joinRuns(subtitle).substringBefore(" • ").trim()
                if (text.isNotEmpty()) listOf(YouTubeArtistRef(text, null)) else emptyList()
            }

        val duration = subtitle?.optJSONArray("runs")?.let { runs ->
            (runs.length() - 1 downTo 0)
                .asSequence()
                .mapNotNull { runs.optJSONObject(it)?.optString("text") }
                .firstOrNull { looksLikeDuration(it) }
        }?.let { parseDurationText(it) } ?: 0L

        val album = flexColumnText(renderer, 2)?.let { joinRuns(it) }?.takeIf { it.isNotBlank() }
        val thumbnail = bestThumbnail(renderer.optJSONObject("thumbnail"))

        return YouTubeSong(
            videoId = videoId,
            title = title,
            artists = artists,
            album = album,
            durationSeconds = duration,
            thumbnailUrl = thumbnail,
        )
    }

    /** Concatenate all `runs[].text` in a `{ "runs": [...] }` text object. */
    fun joinRuns(textObj: JSONObject?): String {
        val runs = textObj?.optJSONArray("runs") ?: return textObj?.optString("simpleText").orEmpty()
        return buildString {
            for (i in 0 until runs.length()) {
                append(runs.optJSONObject(i)?.optString("text").orEmpty())
            }
        }
    }

    /** First `run` whose navigation endpoint browseId starts with [prefix] (e.g. "UC" for artists). */
    fun runsWithBrowseId(textObj: JSONObject?, prefix: String): List<Pair<String, String>> {
        val runs = textObj?.optJSONArray("runs") ?: return emptyList()
        val out = mutableListOf<Pair<String, String>>()
        for (i in 0 until runs.length()) {
            val run = runs.optJSONObject(i) ?: continue
            val name = run.optString("text")
            val browseId = run.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")
                ?.optString("browseId")
                ?.takeIf { it.startsWith(prefix) }
            if (browseId != null) out.add(name to browseId)
        }
        return out
    }

    /**
     * Pick the highest-resolution thumbnail URL from a renderer and upscale it. YTM serves
     * square art via a size suffix (`=w120-h120-...` or `wNNN-hNNN` path segment); we bump
     * it to a crisp size for the player.
     */
    fun bestThumbnail(thumbnailRenderer: JSONObject?, targetSize: Int = 544): String? {
        val thumbnails = thumbnailRenderer
            ?.optJSONObject("musicThumbnailRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?: thumbnailRenderer
                ?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")
            ?: thumbnailRenderer?.optJSONArray("thumbnails")
            ?: return null
        val last = thumbnails.optJSONObject(thumbnails.length() - 1) ?: return null
        val url = last.optString("url").takeIf { it.isNotBlank() } ?: return null
        return upscaleThumbnail(url, targetSize)
    }

    private val sizeRegex = Regex("w\\d+-h\\d+")
    private val querySizeRegex = Regex("=w\\d+-h\\d+")

    private fun upscaleThumbnail(url: String, size: Int): String = when {
        querySizeRegex.containsMatchIn(url) -> url.replace(querySizeRegex, "=w$size-h$size")
        sizeRegex.containsMatchIn(url) -> url.replace(sizeRegex, "w$size-h$size")
        else -> url
    }

    /** Parse "3:45" / "1:02:33" into seconds; 0 if unparseable. */
    fun parseDurationText(text: String?): Long {
        if (text.isNullOrBlank()) return 0
        val parts = text.split(":").mapNotNull { it.trim().toLongOrNull() }
        if (parts.isEmpty()) return 0
        return parts.fold(0L) { acc, part -> acc * 60 + part }
    }

    private val durationRegex = Regex("^\\d{1,2}(:\\d{2})+$")
    fun looksLikeDuration(text: String): Boolean = durationRegex.matches(text.trim())

    /** Find a videoId anywhere in the common locations of a list-item renderer. */
    fun findVideoId(item: JSONObject): String? {
        item.optJSONObject("playlistItemData")?.optString("videoId")
            ?.takeIf { it.isNotBlank() }?.let { return it }

        // Overlay play button → watchEndpoint
        item.optJSONObject("overlay")
            ?.optJSONObject("musicItemThumbnailOverlayRenderer")
            ?.optJSONObject("content")
            ?.optJSONObject("musicPlayButtonRenderer")
            ?.optJSONObject("playNavigationEndpoint")
            ?.optJSONObject("watchEndpoint")
            ?.optString("videoId")
            ?.takeIf { it.isNotBlank() }?.let { return it }

        // Any flex-column run carrying a watchEndpoint
        item.optJSONArray("flexColumns")?.let { cols ->
            for (i in 0 until cols.length()) {
                val runs = cols.optJSONObject(i)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
                    ?.optJSONArray("runs") ?: continue
                for (j in 0 until runs.length()) {
                    runs.optJSONObject(j)
                        ?.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("watchEndpoint")
                        ?.optString("videoId")
                        ?.takeIf { it.isNotBlank() }?.let { return it }
                }
            }
        }
        return null
    }

    /** The text object of flex column [index] in a musicResponsiveListItemRenderer. */
    fun flexColumnText(item: JSONObject, index: Int): JSONObject? =
        item.optJSONArray("flexColumns")
            ?.optJSONObject(index)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")

    /** Walk a JSONArray of objects. */
    inline fun JSONArray.forEachObject(action: (JSONObject) -> Unit) {
        for (i in 0 until length()) {
            optJSONObject(i)?.let(action)
        }
    }
}
