package com.geostamp.camera.camera

import androidx.camera.view.PreviewView
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geostamp.camera.capture.CaptureMode
import com.geostamp.camera.dual.DualCaptureController
import com.geostamp.camera.stamps.StampPosition

/**
 * Live view for the dual modes. Dual photo shows two real previews (rear full frame, front inset); dual
 * video shows the single stream CameraX already composes, exactly as it will be recorded.
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
    val inset = dualState.inset
    val videoInsetKey = if (mode == CaptureMode.DUAL_VIDEO) inset else null

    LaunchedEffect(mode, videoInsetKey, stampPosition) {
        viewModel.bindDual(owner, mainView.surfaceProvider, if (mode == CaptureMode.DUAL_PHOTO) frontView.surfaceProvider else null)
    }
    DisposableEffect(Unit) { onDispose { viewModel.leaveDual(owner) } }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        AndroidView(factory = { mainView }, modifier = Modifier.fillMaxSize())
        if (mode == CaptureMode.DUAL_PHOTO) {
            val rect = inset.rect(DualCaptureController.PHOTO_FRAME_ASPECT, stampPosition)
            val shape = RoundedCornerShape(12.dp)
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
