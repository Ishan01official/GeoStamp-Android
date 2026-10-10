package com.geostamp.camera.camera

import com.geostamp.camera.capture.LensFacing

/** Hardware facts read from the camera that CameraX actually bound, never from a guess. */
data class BoundCamera(val lens: LensFacing, val hasFlashUnit: Boolean)

/**
 * Flash availability for the lens the user asked for. Before binding completes, or while a lens switch is
 * still in flight, the bound camera does not match the request and flash reports unavailable until the
 * new camera arrives. The answer is re-evaluated on every bind, so it can never stay stuck at "no flash".
 */
object FlashAvailability {
    fun resolve(requestedLens: LensFacing, bound: BoundCamera?): Boolean =
        bound != null && bound.lens == requestedLens && bound.hasFlashUnit
}
