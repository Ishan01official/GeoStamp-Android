package com.geostamp.camera.capture

import android.graphics.Bitmap
import android.net.Uri
import com.geostamp.camera.stamps.StampContentBuilder
import com.geostamp.camera.stamps.StampData
import com.geostamp.camera.stamps.StampPreferences
import com.geostamp.camera.stamps.StampRenderer
import com.geostamp.camera.stamps.StampStyle

data class StampRequest(
    val data: StampData,
    val preferences: StampPreferences,
    val map: Bitmap?,
    val logo: Bitmap?
)

data class SaveOptions(
    val jpegQuality: Int,
    val saveOriginal: Boolean
)

data class ProcessedPhoto(val uri: Uri, val originalUri: Uri?, val thumbnail: Bitmap, val width: Int, val height: Int)

/**
 * The image pipeline, independent of any UI: decoded upright bitmap -> stamp card -> JPEG + EXIF -> MediaStore.
 * Must be called off the main thread.
 */
class PhotoProcessor(
    private val writer: MediaStoreWriter,
    private val contentBuilder: () -> StampContentBuilder,
    private val renderer: () -> StampRenderer
) {
    fun process(
        upright: Bitmap,
        originalJpeg: ByteArray?,
        originalRotationDegrees: Int,
        request: StampRequest,
        options: SaveOptions,
        namePrefix: String = NAME_PREFIX
    ): ProcessedPhoto {
        val takenAt = request.data.capturedAtMillis
        val originalUri = originalJpeg?.takeIf { options.saveOriginal }?.let { bytes ->
            writer.saveJpegBytes(
                bytes,
                MediaStoreWriter.displayName(namePrefix, takenAt, MediaStoreWriter.ORIGINAL_SUFFIX),
                PhotoMetadata(
                    takenAt,
                    request.data.location,
                    request.preferences.writeExifLocation,
                    stamped = false,
                    rotationDegrees = originalRotationDegrees
                )
            )
        }

        stamp(upright, request)
        val uri = writer.saveBitmap(
            upright,
            MediaStoreWriter.displayName(namePrefix, takenAt),
            options.jpegQuality,
            PhotoMetadata(takenAt, request.data.location, request.preferences.writeExifLocation, stamped = true)
        )
        val thumbnail = thumbnail(upright, THUMBNAIL_SIZE)
        val width = upright.width
        val height = upright.height
        if (thumbnail !== upright) upright.recycle()
        return ProcessedPhoto(uri, originalUri, thumbnail, width, height)
    }

    /** Draws the stamp in place. Public so the batch stamper and tests reuse the exact same path. */
    fun stamp(target: Bitmap, request: StampRequest) {
        val content = contentBuilder().build(
            data = request.data,
            preferences = request.preferences,
            mapAvailable = request.map != null,
            logoAvailable = request.logo != null
        )
        renderer().renderOnto(target, content, StampStyle.from(request.preferences), request.map, request.logo)
    }

    companion object {
        const val NAME_PREFIX = "GeoStamp"
        private const val THUMBNAIL_SIZE = 256

        fun thumbnail(source: Bitmap, maxSize: Int): Bitmap {
            val scale = maxSize.toFloat() / maxOf(source.width, source.height)
            if (scale >= 1f) return source
            return Bitmap.createScaledBitmap(
                source,
                (source.width * scale).toInt().coerceAtLeast(1),
                (source.height * scale).toInt().coerceAtLeast(1),
                true
            )
        }
    }
}
