package com.geostamp.camera.camera

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geostamp.camera.capture.CaptureMode
import com.geostamp.camera.dual.PipFrame
import com.geostamp.camera.stamps.StampPosition
import kotlinx.coroutines.delay

/**
 * Live view for the dual modes. Dual photo shows two real previews (rear full frame, front inset); dual
 * video shows the single stream CameraX already composes, exactly as it will be recorded, with the same
 * rounded frame the recording receives.
 */
@Composable
fun DualViewfinder(viewModel: CameraViewModel, mode: CaptureMode, stampPosition: StampPosition) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val dualState by viewModel.dual.state.collectAsStateWithLifecycle()
    val mainView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val frontView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // A TextureView keeps the inset clipped to its rounded frame above the main SurfaceView.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val isVideo = mode == CaptureMode.DUAL_VIDEO
    // The video inset is part of the composed stream, so moving it means rebinding; the photo inset is only a view.
    val videoInsetKey = if (isVideo) dualState.inset to dualState.safeArea else null

    LaunchedEffect(mode, videoInsetKey, stampPosition) {
        // Layout settles over a frame or two after a mode switch; wait so the camera binds once, not twice.
        if (isVideo) delay(BIND_SETTLE_MILLIS)
        viewModel.bindDual(owner, mainView.surfaceProvider, if (isVideo) null else frontView.surfaceProvider)
    }
    DisposableEffect(Unit) { onDispose { viewModel.leaveDual(owner) } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        AndroidView(factory = { mainView }, modifier = Modifier.fillMaxSize())
        val rect = remember(mode, dualState, stampPosition) { viewModel.dual.previewInset(mode, stampPosition) }
        if (isVideo) {
            Canvas(Modifier.fillMaxSize()) {
                drawIntoCanvas { PipFrame.draw(it.nativeCanvas, size.width, size.height, rect) }
            }
        } else {
            // Same radius as the Dual Video frame and the saved photo: a fixed share of the frame width.
            val shape = RoundedCornerShape(maxWidth * PipFrame.CORNER_FRACTION)
            AndroidView(
                factory = { frontView },
                modifier = Modifier
                    .offset(x = maxWidth * rect.left, y = maxHeight * rect.top)
                    .size(width = maxWidth * rect.width, height = maxHeight * rect.height)
                    .clip(shape)
                    .border(2.dp, Color.White, shape)
            )
        }
    }
}

private const val BIND_SETTLE_MILLIS = 250L
