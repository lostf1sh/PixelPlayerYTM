package com.lostf1sh.pixelplayeross.data.repository

import com.lostf1sh.pixelplayeross.data.innertube.InnerTubeMapper
import com.lostf1sh.pixelplayeross.data.innertube.YouTubeMusicApi
import com.lostf1sh.pixelplayeross.data.innertube.parser.BrowsePage
import com.lostf1sh.pixelplayeross.data.innertube.parser.HomeFeedPage
import com.lostf1sh.pixelplayeross.data.innertube.parser.NextPage
import com.lostf1sh.pixelplayeross.data.model.HomeShelf
import com.lostf1sh.pixelplayeross.data.model.ShelfItem
import com.lostf1sh.pixelplayeross.data.model.Song
import com.lostf1sh.pixelplayeross.data.youtube.stream.AudioQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class YouTubeMusicRepositoryImpl(
    private val api: YouTubeMusicApi,
    private val accountManager: com.lostf1sh.pixelplayeross.data.youtube.auth.YouTubeAccountManager,
) : YouTubeMusicRepository {

    override suspend fun search(query: String, continuation: String?): YouTubeSearchResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val page = api.search(query, continuation)
                YouTubeSearchResult(
                    songs = page.songs.map(InnerTubeMapper::toSong),
                    continuation = page.continuation,
                )
            }.getOrElse {
                Timber.w(it, "YouTube search failed for '%s'", query)
                YouTubeSearchResult(emptyList(), null)
            }
        }

    override suspend fun getSearchSuggestions(input: String): List<String> =
        withContext(Dispatchers.IO) {
            runCatching { api.searchSuggestions(input) }.getOrDefault(emptyList())
        }

    override suspend fun resolveStreamUrl(videoId: String, quality: AudioQuality): String? =
        withContext(Dispatchers.IO) {
            // Signed-in users can stream higher-bitrate formats.
            val effectiveQuality = if (accountManager.isLoggedIn.value) AudioQuality.HIGH else quality
            api.resolveStreamUrl(videoId, effectiveQuality)
        }

    override suspend fun getHomeFeed(continuation: String?): HomeFeedPage =
        withContext(Dispatchers.IO) {
            runCatching { api.getHomeFeed(continuation) }.getOrElse {
                Timber.w(it, "YouTube home feed failed")
                HomeFeedPage(emptyList(), null)
            }
        }

    override suspend fun getMoodsAndGenres(): List<HomeShelf> =
        withContext(Dispatchers.IO) {
            runCatching { api.getMoodsAndGenres() }.getOrElse {
                Timber.w(it, "YouTube moods failed")
                emptyList()
            }
        }

    override suspend fun getMoodCategory(browseId: String, params: String?): HomeFeedPage =
        withContext(Dispatchers.IO) {
            runCatching { api.getMoodCategory(browseId, params) }.getOrElse {
                Timber.w(it, "YouTube mood category failed for %s", browseId)
                HomeFeedPage(emptyList(), null)
            }
        }

    override suspend fun getBrowsePage(browseId: String): BrowsePage? =
        withContext(Dispatchers.IO) {
            runCatching { api.getBrowsePage(browseId) }.getOrElse {
                Timber.w(it, "YouTube browse page failed for %s", browseId)
                null
            }
        }

    override suspend fun getWatchQueue(
        videoId: String,
        playlistId: String?,
        continuation: String?,
    ): NextPage =
        withContext(Dispatchers.IO) {
            runCatching { api.getWatchQueue(videoId, playlistId, continuation = continuation) }.getOrElse {
                Timber.w(it, "YouTube watch queue failed for %s", videoId)
                NextPage(emptyList(), null)
            }
        }

    override suspend fun getLikedSongs(): List<Song> =
        withContext(Dispatchers.IO) {
            runCatching { api.getLikedSongs() }.getOrElse {
                Timber.w(it, "YouTube liked songs failed")
                emptyList()
            }
        }

    override suspend fun getLibraryPlaylists(): List<ShelfItem.BrowseItem> =
        withContext(Dispatchers.IO) {
            runCatching { api.getLibraryPlaylists() }.getOrElse {
                Timber.w(it, "YouTube library playlists failed")
                emptyList()
            }
        }

    override suspend fun getLibraryAlbums(): List<ShelfItem.BrowseItem> =
        withContext(Dispatchers.IO) {
            runCatching { api.getLibraryAlbums() }.getOrElse {
                Timber.w(it, "YouTube library albums failed")
                emptyList()
            }
        }

    override suspend fun getLibraryArtists(): List<ShelfItem.BrowseItem> =
        withContext(Dispatchers.IO) {
            runCatching { api.getLibraryArtists() }.getOrElse {
                Timber.w(it, "YouTube library artists failed")
                emptyList()
            }
        }

    override suspend fun setLike(videoId: String, liked: Boolean) {
        withContext(Dispatchers.IO) {
            runCatching { api.setLike(videoId, liked) }
                .onFailure { Timber.w(it, "YouTube setLike failed for %s", videoId) }
        }
    }
}
