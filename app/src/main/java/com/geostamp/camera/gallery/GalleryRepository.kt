package com.geostamp.camera.gallery

import android.content.ContentResolver
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import com.geostamp.camera.capture.MediaStoreWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val takenAtMillis: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val isVideo: Boolean,
    val durationMillis: Long
) {
    /** Media with a burned-in stamp: photos other than originals, and videos saved as `_stamped`. */
    val isStamped: Boolean get() = isStampedName(displayName, isVideo)

    /** Unstamped pixels to re-stamp from: the item itself, or the original saved alongside a stamped photo. */
    fun unstampedSource(all: List<MediaItem>): MediaItem? {
        val name = unstampedSourceName(displayName, isVideo) ?: return null
        return if (name == displayName) this else all.firstOrNull { !it.isVideo && it.displayName == name }
    }

    companion object {
        /** File name holding unstamped pixels for [displayName]: itself if unstamped, else its saved original. */
        fun unstampedSourceName(displayName: String, isVideo: Boolean): String? = when {
            isVideo -> null
            !isStampedName(displayName, isVideo = false) -> displayName
            else -> displayName.substringBeforeLast('.') + MediaStoreWriter.ORIGINAL_SUFFIX + ".jpg"
        }

        fun isStampedName(displayName: String, isVideo: Boolean): Boolean {
            val base = displayName.substringBeforeLast('.')
            return if (isVideo) base.endsWith(MediaStoreWriter.STAMPED_VIDEO_SUFFIX) else !base.endsWith(MediaStoreWriter.ORIGINAL_SUFFIX)
        }
    }
}

data class LatestCapture(val uri: Uri, val thumbnail: Bitmap?, val isVideo: Boolean)

data class PhotoMetadataInfo(
    val latitude: Double?,
    val longitude: Double?,
    val altitudeMeters: Double?,
    val dateTimeOriginal: String?,
    val cameraModel: String?,
    /** True when GeoStamp recorded that the stamped address was typed by the user; null if unknown. */
    val addressEnteredManually: Boolean? = null
)

/** Reads the app's own photos and videos from shared storage. */
class GalleryRepository(private val resolver: ContentResolver) {
    private val thumbnails = object : LruCache<Uri, Bitmap>(THUMBNAIL_CACHE_BYTES) {
        override fun sizeOf(key: Uri, value: Bitmap): Int = value.allocationByteCount
    }

    suspend fun loadMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        (queryImages() + queryVideos()).sortedByDescending { it.takenAtMillis }
    }

    suspend fun latestCapture(): LatestCapture? {
        val latest = loadMedia().firstOrNull() ?: return null
        return LatestCapture(latest.uri, thumbnail(latest.uri), latest.isVideo)
    }

    suspend fun thumbnail(uri: Uri, size: Int = THUMBNAIL_SIZE): Bitmap? {
        thumbnails.get(uri)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching { resolver.loadThumbnail(uri, Size(size, size), null) }.getOrNull()
                ?.also { thumbnails.put(uri, it) }
        }
    }

    /** Decodes a photo for full-screen display, downsampled to [maxEdge] and EXIF-oriented. */
    suspend fun displayBitmap(uri: Uri, maxEdge: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > maxEdge) {
                    val scale = maxEdge.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
            }
        }.getOrNull()
    }

    suspend fun metadata(uri: Uri): PhotoMetadataInfo? = withContext(Dispatchers.IO) {
        runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val latLong = exif.latLong
                PhotoMetadataInfo(
                    latitude = latLong?.getOrNull(0),
                    longitude = latLong?.getOrNull(1),
                    altitudeMeters = exif.getAltitude(Double.NaN).takeUnless { it.isNaN() },
                    dateTimeOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
                    cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL),
                    addressEnteredManually = exif.getAttribute(ExifInterface.TAG_USER_COMMENT)
                        ?.takeIf { it.startsWith(MediaStoreWriter.STAMPED_MARKER) }
                        ?.contains(MediaStoreWriter.MANUAL_ADDRESS_MARKER)
                )
            }
        }.getOrNull()
    }

    /** Deletes items the app owns. Returns the URIs that need user confirmation instead. */
    suspend fun delete(uris: List<Uri>): List<Uri> = withContext(Dispatchers.IO) {
        uris.filter { uri ->
            try {
                resolver.delete(uri, null, null)
                thumbnails.remove(uri)
                false
            } catch (_: SecurityException) {
                true
            }
        }
    }

    fun evict(uris: List<Uri>) = uris.forEach { thumbnails.remove(it) }

    private fun queryImages(): List<MediaItem> = query(
        collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        relativePath = MediaStoreWriter.PHOTO_RELATIVE_PATH,
        isVideo = false
    )

    private fun queryVideos(): List<MediaItem> = query(
        collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        relativePath = MediaStoreWriter.VIDEO_RELATIVE_PATH,
        isVideo = true
    )

    private fun query(collection: Uri, relativePath: String, isVideo: Boolean): List<MediaItem> {
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.DATE_TAKEN)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(MediaStore.MediaColumns.SIZE)
            if (isVideo) add(MediaStore.Video.VideoColumns.DURATION)
        }.toTypedArray()
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.IS_PENDING} = 0"
        val args = arrayOf("$relativePath%")
        val items = mutableListOf<MediaItem>()
        resolver.query(collection, projection, selection, args, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val name = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val taken = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val added = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val width = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val height = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val size = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val duration = if (isVideo) cursor.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION) else -1
            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(id)
                val takenAt = cursor.getLong(taken).takeIf { it > 0 } ?: (cursor.getLong(added) * 1000L)
                items += MediaItem(
                    id = mediaId,
                    uri = ContentUris.withAppendedId(collection, mediaId),
                    displayName = cursor.getString(name) ?: "",
                    takenAtMillis = takenAt,
                    width = cursor.getInt(width),
                    height = cursor.getInt(height),
                    sizeBytes = cursor.getLong(size),
                    isVideo = isVideo,
                    durationMillis = if (duration >= 0) cursor.getLong(duration) else 0L
                )
            }
        }
        return items
    }

    private companion object {
        const val THUMBNAIL_SIZE = 320
        const val THUMBNAIL_CACHE_BYTES = 24 * 1024 * 1024
    }
}
