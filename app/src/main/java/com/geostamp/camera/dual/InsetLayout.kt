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

    fun intersects(other: NormalizedRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom
}

/**
 * Parts of the viewfinder that on-screen camera controls cover, as fractions of the frame. [top] and [bottom]
 * are full-width bands (status chips, mode selector and shutter); [rail] is the side control rail. The live
 * stamp and the picture-in-picture stay out of them so nothing on screen overlaps.
 */
data class FrameSafeArea(val top: Float = 0f, val bottom: Float = 0f, val rail: NormalizedRect? = null) {
    init {
        require(top >= 0f && bottom >= 0f && top + bottom < 1f)
    }

    /** The band a full-width element such as a top stamp card must stay below. */
    val topIncludingRail: Float get() = maxOf(top, rail?.bottom ?: 0f)

    companion object {
        val NONE = FrameSafeArea()

        /**
         * Builds a safe area from pixel positions relative to the frame's top-left corner. Values are rounded
         * outward to whole percent so layout jitter never triggers work.
         */
        fun fromPixels(
            frameWidth: Float,
            frameHeight: Float,
            coveredTop: Float,
            coveredBottom: Float,
            rail: NormalizedRect? = null
        ): FrameSafeArea {
            if (frameWidth <= 0f || frameHeight <= 0f) return NONE
            fun up(value: Float) = kotlin.math.ceil(value * 100f - 0.01f) / 100f
            fun down(value: Float) = kotlin.math.floor(value * 100f + 0.01f) / 100f
            val railFraction = rail?.let {
                NormalizedRect(down(it.left / frameWidth), down(it.top / frameHeight), up(it.right / frameWidth), up(it.bottom / frameHeight))
            }?.takeIf { it.bottom > 0f && it.top < 1f && it.width > 0f }
            return FrameSafeArea(
                top = up(coveredTop.coerceAtLeast(0f) / frameHeight).coerceIn(0f, MAX_BAND),
                bottom = up(coveredBottom.coerceAtLeast(0f) / frameHeight).coerceIn(0f, MAX_BAND),
                rail = railFraction
            )
        }

        private const val MAX_BAND = 0.45f
    }
}

/**
 * Where the front-camera picture-in-picture sits. Moving and resizing use simple taps (next corner, next
 * size) instead of precise drags. The inset is always kept out of the band the stamp card occupies and out
 * of the parts of the frame that camera controls cover.
 */
/** Defaults to the top-left corner, away from the side control rail on the right. */
data class InsetLayout(val corner: InsetCorner = InsetCorner.TOP_START, val size: InsetSize = InsetSize.MEDIUM) {
    /**
     * @param frameAspect upright frame width / height, e.g. 3/4 or 9/16.
     * @param insetAspect inset width / height; the front camera is shown in portrait 3:4.
     * @param sizeScale shrinks the inset for tall frames, where a full-size 9:16 window would dominate.
     * @param safeArea frame bands covered by on-screen controls.
     */
    fun rect(
        frameAspect: Float,
        stampPosition: StampPosition,
        insetAspect: Float = 3f / 4f,
        sizeScale: Float = 1f,
        safeArea: FrameSafeArea = FrameSafeArea.NONE
    ): NormalizedRect {
        val baseWidth = size.widthFraction * sizeScale
        // Same distance from the side and the top/bottom edge as the 3:4 Dual Photo layout, in pixels.
        val marginX = MARGIN
        val marginY = MARGIN * frameAspect / REFERENCE_ASPECT
        val start = corner == InsetCorner.TOP_START || corner == InsetCorner.BOTTOM_START
        val top = corner == InsetCorner.TOP_START || corner == InsetCorner.TOP_END
        val reservedTop = safeArea.top + if (stampPosition == StampPosition.TOP) STAMP_BAND else 0f
        val reservedBottom = safeArea.bottom + if (stampPosition == StampPosition.BOTTOM) STAMP_BAND else 0f
        val minTop = marginY + reservedTop
        val maxBottom = 1f - marginY - reservedBottom
        // Never taller than the free band between the controls and the stamp; the shape is kept when shrinking.
        val scale = ((maxBottom - minTop) / (baseWidth * frameAspect / insetAspect)).coerceAtMost(1f).coerceAtLeast(0.3f)
        val width = baseWidth * scale
        val height = width * frameAspect / insetAspect

        var left = if (start) marginX else 1f - marginX - width
        var y = if (top) minTop else maxBottom - height
        val rail = safeArea.rail
        if (rail != null && NormalizedRect(left, y, left + width, y + height).intersects(rail)) {
            // First move sideways next to the rail, which keeps the chosen corner; otherwise move below it.
            val besideLeft = rail.left - marginX - width
            val besideRight = rail.right + marginX
            when {
                rail.centerX >= 0.5f && besideLeft >= marginX -> left = besideLeft
                rail.centerX < 0.5f && besideRight + width <= 1f - marginX -> left = besideRight
                rail.bottom + marginY + height <= maxBottom -> y = rail.bottom + marginY
            }
        }
        return NormalizedRect(left, y, left + width, y + height)
    }

    companion object {
        const val MARGIN = 0.03f

        /** Conservative share of the frame height the stamp card can take, including its margin. */
        const val STAMP_BAND = 0.30f

        /** Dual Photo's frame aspect; margins elsewhere are scaled to match it in pixels. */
        private const val REFERENCE_ASPECT = 3f / 4f
    }
}
