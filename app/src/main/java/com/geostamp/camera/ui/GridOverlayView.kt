package com.geostamp.camera.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class GridOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 255, 255)
        strokeWidth = 1.5f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val thirdWidth = width / 3f
        val thirdHeight = height / 3f

        canvas.drawLine(thirdWidth, 0f, thirdWidth, height.toFloat(), gridPaint)
        canvas.drawLine(thirdWidth * 2f, 0f, thirdWidth * 2f, height.toFloat(), gridPaint)
        canvas.drawLine(0f, thirdHeight, width.toFloat(), thirdHeight, gridPaint)
        canvas.drawLine(0f, thirdHeight * 2f, width.toFloat(), thirdHeight * 2f, gridPaint)
    }
}
