package com.geostamp.camera.dual

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * Builds the dual photo: the rear picture fills the frame and the front picture is drawn into the inset,
 * mirrored like the live selfie preview the user saw, with a thin frame so it reads as a separate view.
 */
object DualPhotoComposer {
    fun compose(rear: Bitmap, front: Bitmap, inset: NormalizedRect): Bitmap {
        val output = if (rear.isMutable) rear else rear.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val target = RectF(
            inset.left * output.width,
            inset.top * output.height,
            inset.right * output.width,
            inset.bottom * output.height
        )
        val radius = min(target.width(), target.height()) * 0.08f
        val clip = Path().apply { addRoundRect(target, radius, radius, Path.Direction.CW) }

        canvas.save()
        canvas.clipPath(clip)
        canvas.drawBitmap(front, centerCropMirrored(front, target), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        canvas.restore()

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(2f, output.width * 0.004f)
            color = Color.WHITE
        }
        canvas.drawRoundRect(target, radius, radius, border)
        return output
    }

    private fun centerCropMirrored(source: Bitmap, target: RectF): Matrix {
        val scale = max(target.width() / source.width, target.height() / source.height)
        val dx = target.left + (target.width() - source.width * scale) / 2f
        val dy = target.top + (target.height() - source.height * scale) / 2f
        return Matrix().apply {
            setScale(-scale, scale, 0f, 0f)
            postTranslate(dx + source.width * scale, dy)
        }
    }
}
