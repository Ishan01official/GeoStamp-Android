package com.geostamp.camera.gallery

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.geostamp.camera.capture.CapturedImageDecoder
import com.geostamp.camera.capture.MediaStoreWriter
import com.geostamp.camera.capture.PhotoMetadata
import com.geostamp.camera.capture.PhotoProcessor
import com.geostamp.camera.capture.StampRequest
import com.geostamp.camera.environment.EnvironmentRepository
import com.geostamp.camera.location.LocationStamp
import com.geostamp.camera.stamps.StampData
import com.geostamp.camera.stamps.StampPreferences
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BatchResult(val stamped: Int, val failed: Int)

/**
 * Stamps existing photos using only what their EXIF records (time and, if present, GPS).
 * Weather is never added because it would describe "now", not the moment the photo was taken.
 */
class BatchStamper(
    private val resolver: ContentResolver,
    private val processor: PhotoProcessor,
    private val writer: MediaStoreWriter,
    private val environment: EnvironmentRepository
) {
    suspend fun stamp(
        uris: List<Uri>,
        preferences: StampPreferences,
        logo: Bitmap?,
        jpegQuality: Int,
        onProgress: (done: Int) -> Unit
    ): BatchResult = withContext(Dispatchers.Default) {
        var stamped = 0
        var failed = 0
        uris.forEachIndexed { index, uri ->
            runCatching { stampOne(uri, preferences, logo, jpegQuality) }
                .onSuccess { stamped++ }
                .onFailure { failed++ }
            onProgress(index + 1)
        }
        BatchResult(stamped, failed)
    }

    private fun stampOne(uri: Uri, preferences: StampPreferences, logo: Bitmap?, jpegQuality: Int) {
        val exif = resolver.openInputStream(uri)?.use { ExifInterface(it) } ?: error("Unreadable photo")
        val takenAt = exif.dateTimeOriginalMillis() ?: System.currentTimeMillis()
        val location = exif.latLong?.let { (lat, lon) ->
            LocationStamp(
                latitude = lat,
                longitude = lon,
                accuracyMeters = exif.horizontalErrorMeters() ?: Float.NaN,
                measuredAtMillis = takenAt,
                altitudeMeters = exif.getAltitude(Double.NaN).takeUnless { it.isNaN() },
                speedMetersPerSecond = null,
                provider = "exif"
            )
        }
        val nearby = environment.forCapture(location)
        val bitmap = decode(uri, exif.rotationDegrees)
        val request = StampRequest(
            data = StampData(capturedAtMillis = takenAt, location = location, address = nearby.address?.value),
            preferences = preferences,
            map = nearby.map?.value,
            logo = logo
        )
        processor.stamp(bitmap, request)
        writer.saveBitmap(
            bitmap,
            MediaStoreWriter.displayName(PhotoProcessor.NAME_PREFIX, System.currentTimeMillis(), "_batch"),
            jpegQuality,
            PhotoMetadata(takenAt, location, preferences.writeExifLocation, stamped = true)
        )
        bitmap.recycle()
    }

    private fun decode(uri: Uri, rotation: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Undecodable photo")
        return CapturedImageDecoder.rotateUpright(decoded, rotation)
    }

    private fun ExifInterface.dateTimeOriginalMillis(): Long? {
        val value = getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: getAttribute(ExifInterface.TAG_DATETIME) ?: return null
        return runCatching { SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(value)?.time }.getOrNull()
    }

    private fun ExifInterface.horizontalErrorMeters(): Float? =
        getAttributeDouble(ExifInterface.TAG_GPS_H_POSITIONING_ERROR, Double.NaN).takeUnless { it.isNaN() }?.toFloat()


    private companion object {
        const val MAX_EDGE = 4096
    }
}
