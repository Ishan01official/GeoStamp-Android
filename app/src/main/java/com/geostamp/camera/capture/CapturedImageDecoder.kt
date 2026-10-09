package com.geostamp.camera.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Paint
import androidx.camera.core.ImageProxy

/**
 * Converts CameraX output into an upright, mutable ARGB bitmap.
 *
 * JPEG buffers are decoded directly (BitmapFactory ignores EXIF orientation, so the CameraX
 * rotation is applied explicitly). Other formats fall back to [ImageProxy.toBitmap].
 */
object CapturedImageDecoder {
    fun decodeUpright(image: ImageProxy): Bitmap {
        val decoded = if (image.format == ImageFormat.JPEG) {
            decodeJpeg(jpegBytes(image))
        } else {
            image.toBitmap()
        }
        return rotateUpright(decoded, image.imageInfo.rotationDegrees)
    }

    /** The encoded JPEG exactly as the camera produced it, for saving an unmodified original. */
    fun jpegBytes(image: ImageProxy): ByteArray {
        check(image.format == ImageFormat.JPEG) { "Expected JPEG, got format ${image.format}" }
        val buffer = image.planes[0].buffer.duplicate()
        buffer.rewind()
        return ByteArray(buffer.remaining()).also { buffer.get(it) }
    }

    fun decodeJpeg(bytes: ByteArray): Bitmap {
        val options = BitmapFactory.Options().apply {
            inMutable = true
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?: error("Camera returned an undecodable JPEG (${bytes.size} bytes)")
    }

    /** Returns a mutable bitmap rotated clockwise by [rotationDegrees]. Recycles [source] when a copy is made. */
    fun rotateUpright(source: Bitmap, rotationDegrees: Int): Bitmap {
        val normalized = ((rotationDegrees % 360) + 360) % 360
        if (normalized == 0) {
            if (source.isMutable && source.config == Bitmap.Config.ARGB_8888) return source
            return source.copy(Bitmap.Config.ARGB_8888, true).also { source.recycle() }
        }
        val (width, height) = uprightSize(source.width, source.height, normalized)
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val matrix = Matrix().apply {
            postRotate(normalized.toFloat())
            when (normalized) {
                90 -> postTranslate(source.height.toFloat(), 0f)
                180 -> postTranslate(source.width.toFloat(), source.height.toFloat())
                270 -> postTranslate(0f, source.width.toFloat())
            }
        }
        Canvas(output).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        source.recycle()
        return output
    }

    fun uprightSize(width: Int, height: Int, rotationDegrees: Int): Pair<Int, Int> =
        if (rotationDegrees % 180 == 0) width to height else height to width
}
