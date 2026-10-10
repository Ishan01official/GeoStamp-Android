package com.geostamp.camera.qr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.floor

/**
 * Draws QR codes pixel-exact: every module is a whole number of pixels with no anti-aliasing, so the code
 * stays sharp at any output size instead of being scaled from a small bitmap.
 */
object QrDrawing {
    private val darkPaint = Paint().apply {
        color = Color.BLACK
        isAntiAlias = false
        style = Paint.Style.FILL
    }

    /**
     * Fills [bounds] with a white square containing the code and its quiet zone, black on white for the
     * highest contrast. Returns the square actually used, which can be slightly smaller than [bounds]
     * because modules are snapped to whole pixels.
     */
    fun draw(canvas: Canvas, matrix: QrMatrix, bounds: RectF): RectF {
        val modules = matrix.sizeWithQuietZone
        val available = minOf(bounds.width(), bounds.height())
        val modulePx = floor(available / modules).coerceAtLeast(1f)
        val side = modulePx * modules
        val left = floor(bounds.left + (bounds.width() - side) / 2f)
        val top = floor(bounds.top + (bounds.height() - side) / 2f)
        canvas.drawRect(left, top, left + side, top + side, Paint().apply { color = Color.WHITE })
        val origin = QrLocationEncoder.QUIET_ZONE_MODULES * modulePx
        for (y in 0 until matrix.size) {
            for (x in 0 until matrix.size) {
                if (!matrix.isDark(x, y)) continue
                val l = left + origin + x * modulePx
                val t = top + origin + y * modulePx
                canvas.drawRect(l, t, l + modulePx, t + modulePx, darkPaint)
            }
        }
        return RectF(left, top, left + side, top + side)
    }

    /** A standalone image of the code, about [targetPx] wide, for showing on screen and sharing. */
    fun bitmap(matrix: QrMatrix, targetPx: Int): Bitmap {
        val modulePx = (targetPx / matrix.sizeWithQuietZone).coerceAtLeast(1)
        val side = modulePx * matrix.sizeWithQuietZone
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap), matrix, RectF(0f, 0f, side.toFloat(), side.toFloat()))
        return bitmap
    }
}
