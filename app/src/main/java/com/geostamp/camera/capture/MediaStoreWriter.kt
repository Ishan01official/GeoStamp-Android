package com.geostamp.camera.capture

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.geostamp.camera.address.AddressSource
import com.geostamp.camera.location.LocationStamp
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Metadata written into saved JPEGs. Location is only written when the user allows it. */
data class PhotoMetadata(
    val capturedAtMillis: Long,
    val location: LocationStamp?,
    val writeLocation: Boolean,
    val stamped: Boolean,
    /** Clockwise rotation needed to view the stored pixels upright; null leaves the tag untouched. */
    val rotationDegrees: Int? = null,
    val addressSource: AddressSource = AddressSource.DETECTED
)

/** Saves media into the shared Pictures/GeoStamp collection using scoped storage. */
class MediaStoreWriter(private val resolver: ContentResolver) {
    fun saveBitmap(bitmap: Bitmap, displayName: String, quality: Int, metadata: PhotoMetadata): Uri =
        insertPending(displayName, metadata.capturedAtMillis) { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)) { "JPEG encoding failed" }
        }.also { uri -> writeExif(uri, metadata, resetOrientation = true) }

    fun saveJpegBytes(bytes: ByteArray, displayName: String, metadata: PhotoMetadata): Uri =
        insertPending(displayName, metadata.capturedAtMillis) { it.write(bytes) }
            .also { uri -> writeExif(uri, metadata, resetOrientation = false) }

    fun createPendingVideo(displayName: String, takenAtMillis: Long): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, VIDEO_RELATIVE_PATH)
            put(MediaStore.Video.Media.DATE_TAKEN, takenAtMillis)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        return resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore refused to create $displayName")
    }

    fun publishVideo(uri: Uri) {
        resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
    }

    fun delete(uri: Uri) {
        resolver.delete(uri, null, null)
    }

    fun openFileDescriptor(uri: Uri, mode: String): ParcelFileDescriptor =
        resolver.openFileDescriptor(uri, mode) ?: error("Could not open $uri")

    private fun insertPending(displayName: String, takenAtMillis: Long, write: (OutputStream) -> Unit): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, PHOTO_RELATIVE_PATH)
            put(MediaStore.Images.Media.DATE_TAKEN, takenAtMillis)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore refused to create $displayName")
        try {
            resolver.openOutputStream(uri)?.use(write) ?: error("Could not open $displayName for writing")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            return uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun writeExif(uri: Uri, metadata: PhotoMetadata, resetOrientation: Boolean) {
        resolver.openFileDescriptor(uri, "rw")?.use { descriptor ->
            val exif = ExifInterface(descriptor.fileDescriptor)
            val dateTime = exifDate(metadata.capturedAtMillis)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateTime)
            exif.setAttribute(ExifInterface.TAG_DATETIME, dateTime)
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, exifOffset(metadata.capturedAtMillis))
            exif.setAttribute(ExifInterface.TAG_SOFTWARE, SOFTWARE)
            exif.setAttribute(ExifInterface.TAG_USER_COMMENT, userComment(metadata))
            val orientation = if (resetOrientation) ExifInterface.ORIENTATION_NORMAL else metadata.rotationDegrees?.let(::exifOrientation)
            orientation?.let { exif.setAttribute(ExifInterface.TAG_ORIENTATION, it.toString()) }
            val location = metadata.location
            if (metadata.writeLocation && location != null) {
                exif.setLatLong(location.latitude, location.longitude)
                location.altitudeMeters?.let(exif::setAltitude)
                if (location.hasAccuracy) {
                    exif.setAttribute(ExifInterface.TAG_GPS_H_POSITIONING_ERROR, "${(location.accuracyMeters * 100).toInt()}/100")
                }
            } else {
                GPS_TAGS.forEach { exif.setAttribute(it, null) }
            }
            exif.saveAttributes()
        }
    }

    companion object {
        val PHOTO_RELATIVE_PATH = "${Environment.DIRECTORY_PICTURES}/GeoStamp"
        val VIDEO_RELATIVE_PATH = "${Environment.DIRECTORY_MOVIES}/GeoStamp"
        const val SOFTWARE = "GeoStamp"
        const val STAMPED_MARKER = "GeoStamp:stamped"
        const val ORIGINAL_MARKER = "GeoStamp:original"
        const val ORIGINAL_SUFFIX = "_original"
        const val MANUAL_ADDRESS_MARKER = ";address=manual"

        fun userComment(metadata: PhotoMetadata): String =
            (if (metadata.stamped) STAMPED_MARKER else ORIGINAL_MARKER) +
                if (metadata.stamped && metadata.addressSource == AddressSource.MANUAL) MANUAL_ADDRESS_MARKER else ""

        private val GPS_TAGS = listOf(
            ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_GPS_H_POSITIONING_ERROR
        )

        fun displayName(prefix: String, takenAtMillis: Long, suffix: String = ""): String =
            "${prefix}_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(takenAtMillis))}$suffix.jpg"

        fun videoDisplayName(prefix: String, takenAtMillis: Long, suffix: String = ""): String =
            "${prefix}_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(takenAtMillis))}$suffix.mp4"

        fun exifOrientation(rotationDegrees: Int): Int =
            when (((rotationDegrees % 360) + 360) % 360) {
                90 -> ExifInterface.ORIENTATION_ROTATE_90
                180 -> ExifInterface.ORIENTATION_ROTATE_180
                270 -> ExifInterface.ORIENTATION_ROTATE_270
                else -> ExifInterface.ORIENTATION_NORMAL
            }

        private fun exifDate(millis: Long): String =
            SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(millis))

        private fun exifOffset(millis: Long): String {
            val offsetMinutes = TimeZone.getDefault().getOffset(millis) / 60_000
            val sign = if (offsetMinutes >= 0) "+" else "-"
            val abs = kotlin.math.abs(offsetMinutes)
            return String.format(Locale.US, "%s%02d:%02d", sign, abs / 60, abs % 60)
        }
    }
}
