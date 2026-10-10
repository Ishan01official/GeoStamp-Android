package com.geostamp.camera.video

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay

/**
 * A full-frame transparent overlay holding the stamp card. Media3 draws an overlay at its own pixel size
 * relative to the frame, so a bitmap the size of the upright frame covers it exactly. The card is redrawn
 * only when the stamp second changes; Media3 re-uploads it when the bitmap's generation id changes.
 */
@UnstableApi
class VideoStampOverlay(
    width: Int,
    height: Int,
    private val draw: (canvas: Canvas, width: Int, height: Int, presentationTimeUs: Long) -> Unit
) : BitmapOverlay() {
    private val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private var drawnSecond = Long.MIN_VALUE

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val second = presentationTimeUs / 1_000_000L
        if (second != drawnSecond) {
            bitmap.eraseColor(Color.TRANSPARENT)
            draw(canvas, bitmap.width, bitmap.height, presentationTimeUs)
            drawnSecond = second
        }
        return bitmap
    }

    override fun release() {
        super.release()
        bitmap.recycle()
    }
}
