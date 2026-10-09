package com.geostamp.camera.camera

import android.view.OrientationEventListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Rotation (degrees) that keeps icons upright while the camera UI stays in portrait,
 * like native camera apps. Accumulates so animations take the short way round.
 */
@Composable
fun rememberIconRotation(): Float {
    val context = LocalContext.current
    var rotation by remember { mutableFloatStateOf(0f) }
    DisposableEffect(context) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val snapped = when (orientation) {
                    in 45 until 135 -> -90f
                    in 135 until 225 -> 180f
                    in 225 until 315 -> 90f
                    else -> 0f
                }
                val current = ((rotation % 360f) + 360f) % 360f
                val target = ((snapped % 360f) + 360f) % 360f
                var delta = target - current
                if (delta > 180f) delta -= 360f
                if (delta < -180f) delta += 360f
                if (delta != 0f) rotation += delta
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }
    return rotation
}
