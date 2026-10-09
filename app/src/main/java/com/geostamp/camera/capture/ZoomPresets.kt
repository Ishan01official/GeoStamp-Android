package com.geostamp.camera.capture

/** Picks the zoom shortcuts the active camera can actually deliver. */
object ZoomPresets {
    private val CANDIDATES = listOf(1f, 2f, 3f, 5f, 10f)
    private const val MAX_PRESETS = 4
    private const val EPSILON = 0.01f

    fun forRange(minZoom: Float, maxZoom: Float): List<Float> {
        if (maxZoom <= minZoom + EPSILON) return listOf(minZoom)
        val presets = mutableListOf<Float>()
        if (minZoom < 1f - EPSILON) presets += minZoom
        presets += CANDIDATES.filter { it >= minZoom - EPSILON && it <= maxZoom + EPSILON }
        return presets.distinct().take(MAX_PRESETS)
    }

    /** The preset to highlight for the current ratio, or null when between presets. */
    fun selected(presets: List<Float>, current: Float): Float? =
        presets.minByOrNull { kotlin.math.abs(it - current) }
            ?.takeIf { kotlin.math.abs(it - current) < 0.05f * it.coerceAtLeast(1f) }

    fun label(ratio: Float): String =
        if (ratio % 1f < EPSILON || ratio % 1f > 1 - EPSILON) "${Math.round(ratio)}×"
        else String.format(java.util.Locale.US, "%.1f×", ratio)

}
