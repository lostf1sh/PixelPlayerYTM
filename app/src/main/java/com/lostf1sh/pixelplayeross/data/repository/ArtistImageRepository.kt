package com.lostf1sh.pixelplayeross.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import com.lostf1sh.pixelplayeross.data.database.MusicDao
import com.lostf1sh.pixelplayeross.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber
import androidx.core.graphics.scale

/**
 * Repository for resolving and caching artist images.
 *
 * After the YouTube Music pivot there is no external lookup service: image URLs are
 * whatever has been persisted on the artist row (populated from YTM data during
 * sync/browse) plus user-set custom images stored on internal storage. Uses an
 * in-memory LRU cache in front of the Room database.
 */
@Singleton
class ArtistImageRepository @Inject constructor(
    private val musicDao: MusicDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    companion object {
        private const val TAG = "ArtistImageRepository"
        private const val CACHE_SIZE = 100 // Number of artist images to cache in memory
        private const val MAX_CUSTOM_IMAGE_SOURCE_BYTES = 24L * 1024L * 1024L
        private const val MAX_CUSTOM_IMAGE_DIMENSION_PX = 16_384
        private const val MAX_CUSTOM_IMAGE_TOTAL_PIXELS = 80_000_000L
        private const val TARGET_CUSTOM_IMAGE_MAX_DIMENSION_PX = 2_048
        private const val TARGET_CUSTOM_IMAGE_MAX_PIXELS = 4_194_304L // 2048x2048

        internal fun calculateCustomImageSampleSize(width: Int, height: Int): Int {
            var sampleSize = 1
            while (
                width / sampleSize > TARGET_CUSTOM_IMAGE_MAX_DIMENSION_PX ||
                height / sampleSize > TARGET_CUSTOM_IMAGE_MAX_DIMENSION_PX ||
                (width.toLong() / sampleSize) * (height.toLong() / sampleSize) > TARGET_CUSTOM_IMAGE_MAX_PIXELS
            ) {
                sampleSize = sampleSize shl 1
            }
            return sampleSize.coerceAtLeast(1)
        }
    }

    // In-memory LRU cache for quick access
    private val memoryCache = LruCache<String, String>(CACHE_SIZE)

    /**
     * Get the persisted artist image URL, if any.
     * @param artistName Name of the artist
     * @param artistId Room database ID of the artist (for caching)
     * @return Image URL or null if none is stored
     */
    suspend fun getArtistImageUrl(artistName: String, artistId: Long): String? {
        if (artistName.isBlank()) return null
        if (!userPreferencesRepository.externalArtistImagesEnabledFlow.first()) return null

        val normalizedName = artistName.trim().lowercase()

        // Check memory cache first
        memoryCache.get(normalizedName)?.let { cachedUrl ->
            return cachedUrl
        }

        // Resolve canonical DB artist row by name to avoid MediaStore-ID/DB-ID mismatches.
        val dbCachedUrl = withContext(Dispatchers.IO) {
            val canonicalArtistId = musicDao.getArtistIdByNormalizedName(artistName) ?: artistId
            musicDao.getArtistImageUrl(canonicalArtistId)
                ?: musicDao.getArtistImageUrlByNormalizedName(artistName)
        }
        if (!dbCachedUrl.isNullOrEmpty()) {
            memoryCache.put(normalizedName, dbCachedUrl)
            return dbCachedUrl
        }
        return null
    }

    /**
     * Warm the in-memory cache for a list of artists from the database.
     * (No network involved — kept for call-site compatibility.)
     */
    suspend fun prefetchArtistImages(artists: List<Pair<Long, String>>) = withContext(Dispatchers.IO) {
        if (!userPreferencesRepository.externalArtistImagesEnabledFlow.first()) return@withContext
        artists.forEach { (artistId, artistName) ->
            runCatching { getArtistImageUrl(artistName, artistId) }
        }
    }

    /**
     * Persist an artist image URL (e.g. a YTM thumbnail discovered during browse/sync).
     */
    suspend fun cacheArtistImageUrl(artistId: Long, artistName: String, imageUrl: String) {
        if (imageUrl.isBlank()) return
        withContext(Dispatchers.IO) {
            musicDao.updateArtistImageUrl(artistId, imageUrl)
        }
        memoryCache.put(artistName.trim().lowercase(), imageUrl)
    }

    /**
     * Clear all cached images. Useful for debugging or forced refresh.
     */
    fun clearCache() {
        memoryCache.evictAll()
    }

    /**
     * Returns the effective image URL for an artist:
     * - If a custom (user-set) image exists in DB → returns that path
     * - Otherwise falls back to the persisted URL, if any
     */
    suspend fun getEffectiveArtistImageUrl(artistId: Long, artistName: String): String? {
        val customUri = withContext(Dispatchers.IO) { musicDao.getArtistCustomImage(artistId) }
        if (!customUri.isNullOrBlank()) return customUri
        return getArtistImageUrl(artistName, artistId)
    }

    /**
     * Saves a user-selected image as the artist's custom image.
     *
     * The content URI is resolved immediately and the bitmap is written to
     * internal storage (filesDir/artist_art_<id>.jpg). This avoids depending
     * on a content URI that may expire once the photo-picker dismisses.
     *
     * @param context Application context (used for contentResolver and filesDir)
     * @param artistId The artist's database row ID
     * @param sourceUri URI returned by the system photo-picker
     * @return The internal file path on success, null on failure
     */
    suspend fun setCustomArtistImage(context: Context, artistId: Long, sourceUri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val bitmap = decodeCustomArtistBitmap(context, sourceUri) ?: return@withContext null
                val scaledBitmap = scaleBitmapIfNeeded(bitmap)
                try {
                    // 2. Write to internal storage as JPEG (compact and predictable for caching)
                    val destFile = File(context.filesDir, "artist_art_${artistId}.jpg")
                    FileOutputStream(destFile).use { out ->
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }

                    val internalPath = destFile.absolutePath

                    // 3. Persist to DB
                    musicDao.updateArtistCustomImage(artistId, internalPath)

                    // 4. The ViewModel reloads the effective image URL on success, so we only need
                    // to persist the internal file path here.
                    Timber.tag(TAG).d("Custom artist image saved: $internalPath")
                    internalPath
                } finally {
                    if (scaledBitmap !== bitmap) {
                        bitmap.recycle()
                    }
                    scaledBitmap.recycle()
                }
            } catch (e: Exception) {
                Timber.tag(TAG)
                    .e("Failed to save custom artist image for id=$artistId: ${e.message}")
                null
            }
        }
    }

    private fun decodeCustomArtistBitmap(context: Context, sourceUri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(sourceUri)?.lowercase()
        if (mimeType != null && !mimeType.startsWith("image/")) {
            Timber.tag(TAG).w("Rejected custom artist image with unsupported MIME type: $mimeType")
            return null
        }

        runCatching { resolver.openAssetFileDescriptor(sourceUri, "r") }.getOrNull()?.use { descriptor ->
            val declaredLength = descriptor.length
            if (declaredLength > MAX_CUSTOM_IMAGE_SOURCE_BYTES) {
                Timber.tag(TAG)
                    .w("Rejected custom artist image larger than allowed source size: $declaredLength")
                return null
            }
        }

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        // Fetch dimensions without loading the full bitmap into memory.
        // decodeStream returns null when inJustDecodeBounds is true.
        resolver.openInputStream(sourceUri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, bounds)
        }

        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) {
            Timber.tag(TAG)
                .w("Rejected custom artist image with invalid bounds: ${width}x${height}")
            return null
        }
        if (width > MAX_CUSTOM_IMAGE_DIMENSION_PX || height > MAX_CUSTOM_IMAGE_DIMENSION_PX) {
            Timber.tag(TAG)
                .w("Rejected custom artist image with oversized bounds: ${width}x${height}")
            return null
        }
        if (width.toLong() * height.toLong() > MAX_CUSTOM_IMAGE_TOTAL_PIXELS) {
            Timber.tag(TAG)
                .w("Rejected custom artist image with excessive pixel count: ${width}x${height}")
            return null
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateCustomImageSampleSize(width, height)
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        return try {
            resolver.openInputStream(sourceUri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            }
        } catch (oom: OutOfMemoryError) {
            Timber.tag(TAG).e(oom, "Failed to decode custom artist image due to OOM")
            null
        }
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        val longestEdge = max(bitmap.width, bitmap.height)
        if (longestEdge <= TARGET_CUSTOM_IMAGE_MAX_DIMENSION_PX) {
            return bitmap
        }

        val scale = TARGET_CUSTOM_IMAGE_MAX_DIMENSION_PX.toFloat() / longestEdge.toFloat()
        val scaledWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return bitmap.scale(scaledWidth, scaledHeight)
    }

    /**
     * Removes the user's custom artist image, reverting to the persisted URL (if any).
     *
     * @param context Application context
     * @param artistId The artist's database row ID
     */
    suspend fun clearCustomArtistImage(context: Context, artistId: Long) {
        withContext(Dispatchers.IO) {
            try {
                // Delete the internal file if it exists
                val destFile = File(context.filesDir, "artist_art_${artistId}.jpg")
                if (destFile.exists()) {
                    destFile.delete()
                    Timber.tag(TAG).d("Deleted custom artist image file: ${destFile.absolutePath}")
                }
                // Clear from DB
                musicDao.updateArtistCustomImage(artistId, null)
            } catch (e: Exception) {
                Timber.tag(TAG)
                    .e("Failed to clear custom artist image for id=$artistId: ${e.message}")
            }
        }
    }
}
