package com.geostamp.camera.dual

import com.geostamp.camera.stamps.StampPosition

enum class InsetCorner { TOP_START, TOP_END, BOTTOM_START, BOTTOM_END;
    fun next(): InsetCorner = entries[(ordinal + 1) % entries.size]
}

enum class InsetSize(val widthFraction: Float) { SMALL(0.26f), MEDIUM(0.34f), LARGE(0.42f);
    fun next(): InsetSize = entries[(ordinal + 1) % entries.size]
}

/** A rectangle in normalized frame coordinates: (0,0) top-left, (1,1) bottom-right of the upright frame. */
data class NormalizedRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

/**
 * Where the front-camera picture-in-picture sits. Moving and resizing use simple taps (next corner, next
 * size) instead of precise drags. The inset is always kept out of the band the stamp card occupies.
 */
data class InsetLayout(val corner: InsetCorner = InsetCorner.TOP_END, val size: InsetSize = InsetSize.MEDIUM) {
    /**
     * @param frameAspect upright frame width / height, e.g. 3/4 or 9/16.
     * @param insetAspect inset width / height; the front camera is shown in portrait 3:4.
     */
    fun rect(frameAspect: Float, stampPosition: StampPosition, insetAspect: Float = 3f / 4f): NormalizedRect {
        val width = size.widthFraction
        val height = width * frameAspect / insetAspect
        val start = corner == InsetCorner.TOP_START || corner == InsetCorner.BOTTOM_START
        val top = corner == InsetCorner.TOP_START || corner == InsetCorner.TOP_END
        val left = if (start) MARGIN else 1f - MARGIN - width
        val stampTop = if (stampPosition == StampPosition.TOP) STAMP_BAND else 0f
        val stampBottom = if (stampPosition == StampPosition.BOTTOM) STAMP_BAND else 0f
        val y = if (top) MARGIN + stampTop else 1f - MARGIN - stampBottom - height
        return NormalizedRect(left, y, left + width, y + height)
    }

    companion object {
        const val MARGIN = 0.03f

        /** Conservative share of the frame height the stamp card can take, including its margin. */
        const val STAMP_BAND = 0.30f
    }
}
