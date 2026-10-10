package com.geostamp.camera.dual

/** CameraX composition values: scale and offset in normalized device coordinates (-1..1, y up). */
data class NdcPlacement(val scaleX: Float, val scaleY: Float, val offsetX: Float, val offsetY: Float)

/**
 * CameraX composes the two camera streams in the primary sensor's buffer orientation, and only then
 * rotates the result for display and recording. An inset placed on the upright portrait screen must
 * therefore be mapped back into buffer coordinates.
 */
object CompositionMapping {
    /**
     * @param rect inset in upright frame coordinates (0..1, y down).
     * @param bufferRotationDegrees clockwise rotation from the composed buffer to the upright frame
     *   (the primary camera's sensor orientation relative to the portrait display: 0, 90, 180 or 270).
     */
    fun toNdc(rect: NormalizedRect, bufferRotationDegrees: Int): NdcPlacement {
        // Map the upright centre (u, v) back into buffer coordinates (s, t), both y-down.
        val (s, t, scaleS, scaleT) = when (((bufferRotationDegrees % 360) + 360) % 360) {
            90 -> Quad(rect.centerY, 1f - rect.centerX, rect.height, rect.width)
            180 -> Quad(1f - rect.centerX, 1f - rect.centerY, rect.width, rect.height)
            270 -> Quad(1f - rect.centerY, rect.centerX, rect.height, rect.width)
            else -> Quad(rect.centerX, rect.centerY, rect.width, rect.height)
        }
        return NdcPlacement(scaleX = scaleS, scaleY = scaleT, offsetX = 2f * s - 1f, offsetY = 1f - 2f * t)
    }

    private data class Quad(val a: Float, val b: Float, val c: Float, val d: Float)
}
