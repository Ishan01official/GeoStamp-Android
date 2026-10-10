package com.geostamp.camera.dual

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * The rounded white frame around the front-camera picture-in-picture, shared by the Dual Video viewfinder
 * and the recorded video so both look like the Dual Photo window.
 *
 * CameraX composes the front camera into the video as a plain rectangle and cannot round it. The frame is
 * therefore a ring: its inner edge is rounded inside the rectangle and its outer edge is rounded outside it,
 * extending far enough to cover the square corners. A corner is covered once the outward extension reaches
 * (1 - 1/sqrt 2) of the outer radius, about 0.29.
 */
object PipFrame {
    /** Outer corner radius as a fraction of the frame width; about 12 dp on a typical phone, like Dual Photo. */
    const val CORNER_FRACTION = 0.03f

    /** How far the frame extends outside the inset, relative to the outer radius. */
    const val OUTSET_RATIO = 0.32f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    /** Corner radius and outward extension in pixels for a frame [frameWidth] pixels wide. */
    fun metrics(frameWidth: Float): Pair<Float, Float> {
        val radius = frameWidth * CORNER_FRACTION
        return radius to radius * OUTSET_RATIO
    }

    /** The ring for an inset occupying [inset] (pixels), as an even-odd path. */
    fun ring(inset: RectF, frameWidth: Float): Path {
        val (radius, outset) = metrics(frameWidth)
        val outer = RectF(inset.left - outset, inset.top - outset, inset.right + outset, inset.bottom + outset)
        return Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRoundRect(outer, radius, radius, Path.Direction.CW)
            addRoundRect(inset, radius - outset, radius - outset, Path.Direction.CW)
        }
    }

    /** Draws the frame for a normalized inset onto a frame-sized canvas. */
    fun draw(canvas: Canvas, frameWidth: Float, frameHeight: Float, inset: NormalizedRect) {
        val rect = RectF(inset.left * frameWidth, inset.top * frameHeight, inset.right * frameWidth, inset.bottom * frameHeight)
        canvas.drawPath(ring(rect, frameWidth), paint)
    }
}
