package com.geostamp.camera.camera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.geostamp.camera.R
import com.geostamp.camera.capture.CaptureMode
import com.geostamp.camera.capture.ZoomPresets
import com.geostamp.camera.capture.PhotoAspectRatio
import com.geostamp.camera.capture.LensFacing
import com.geostamp.camera.ui.components.CameraChip
import com.geostamp.camera.ui.labelRes
import com.geostamp.camera.ui.theme.CameraColors
import com.geostamp.camera.ui.theme.Dimens
import kotlinx.coroutines.launch

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

@Composable
fun CameraRoute(
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStampSettings: () -> Unit,
    onOpenMedia: (Uri) -> Unit,
    viewModel: CameraViewModel = viewModel()
) {
    val context = LocalContext.current
    var cameraGranted by remember { mutableStateOf(context.hasPermission(Manifest.permission.CAMERA)) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        cameraGranted = context.hasPermission(Manifest.permission.CAMERA)
        viewModel.onPermissionsChanged()
    }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        val missing = (arrayOf(Manifest.permission.CAMERA) + LOCATION_PERMISSIONS).filterNot { context.hasPermission(it) }
        if (missing.isNotEmpty() && !askedOnce) {
            askedOnce = true
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    KeepPortrait()
    LifecycleStartEffect(viewModel) {
        viewModel.onScreenStarted()
        onStopOrDispose { viewModel.onScreenStopped() }
    }

    if (!cameraGranted) {
        CameraPermissionScreen(
            onRequest = { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA) + LOCATION_PERMISSIONS) },
            onOpenAppSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                )
            }
        )
        return
    }

    CameraScreen(
        viewModel = viewModel,
        onOpenGallery = onOpenGallery,
        onOpenSettings = onOpenSettings,
        onOpenStampSettings = onOpenStampSettings,
        onOpenMedia = onOpenMedia,
        onVideoModeSelected = {
            if (!context.hasPermission(Manifest.permission.RECORD_AUDIO)) audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    )
}

@Composable
private fun CameraScreen(
    viewModel: CameraViewModel,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStampSettings: () -> Unit,
    onOpenMedia: (Uri) -> Unit,
    onVideoModeSelected: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings = viewModel.settings.collectAsStateWithLifecycle().value ?: return
    val capture by viewModel.capture.collectAsStateWithLifecycle()
    val location by viewModel.location.collectAsStateWithLifecycle()
    val compass by viewModel.compass.collectAsStateWithLifecycle()
    val liveStamp by viewModel.liveStamp.collectAsStateWithLifecycle()
    val zoomState by viewModel.session.zoomState.observeAsState()
    val iconRotation = rememberIconRotation()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val shutterFlash = remember { Animatable(0f) }

    var showSheet by rememberSaveable { mutableStateOf(false) }
    var showDiagnostics by rememberSaveable { mutableStateOf(false) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusKey by remember { mutableIntStateOf(0) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            controller = viewModel.session.controller
        }
    }
    LaunchedEffect(lifecycleOwner) { viewModel.session.bind(lifecycleOwner) }

    val savedPhoto = stringResource(R.string.saved_photo)
    val savedVideo = stringResource(R.string.saved_video)
    val viewAction = stringResource(R.string.action_view)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CameraEvent.ShutterFeedback -> scope.launch {
                    shutterFlash.snapTo(0.85f)
                    shutterFlash.animateTo(0f, tween(220))
                }
                is CameraEvent.Saved -> scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val result = snackbar.showSnackbar(if (event.isVideo) savedVideo else savedPhoto, actionLabel = viewAction)
                    if (result == SnackbarResult.ActionPerformed) onOpenMedia(event.uri)
                }
                is CameraEvent.Failed -> scope.launch {
                    val message = context.getString(event.messageRes)
                    snackbar.showSnackbar(listOfNotNull(message, event.detail).joinToString(": "))
                }
            }
        }
    }

    val previewRatio = if (capture.mode == CaptureMode.VIDEO) {
        PhotoAspectRatio.SIXTEEN_NINE.portraitWidthOverHeight
    } else {
        settings.camera.aspectRatio.portraitWidthOverHeight
    }

    Box(Modifier.fillMaxSize().background(CameraColors.Background)) {
        // Viewfinder: exact capture framing, never stretched.
        Box(
            Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .aspectRatio(previewRatio)
                .align(Alignment.TopCenter)
                .onSizeChanged { viewModel.onOverlaySize(it.width, it.height) }
        ) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            if (settings.camera.gridEnabled) GridOverlay()
            liveStamp?.takeIf { capture.mode == CaptureMode.PHOTO }?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )
            }
            focusPoint?.let { FocusIndicator(it, focusKey) }
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoom, _ ->
                            val current = viewModel.session.zoomState.value?.zoomRatio ?: return@detectTransformGestures
                            viewModel.setZoom(current * zoom)
                        }
                    }
                    .pointerInput(settings.camera.tapToFocus) {
                        detectTapGestures { offset ->
                            if (!settings.camera.tapToFocus) return@detectTapGestures
                            focusPoint = offset
                            focusKey++
                            val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                            viewModel.focus(point) { }
                        }
                    }
            )
            CountdownOverlay(capture.countdown)
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = shutterFlash.value }.background(Color.Black))
        }

        Column(Modifier.statusBarsPadding().fillMaxWidth()) {
            TopOverlay(
                location = location,
                compass = compass,
                maxAccuracyMeters = settings.location.maxAccuracyMeters,
                iconRotation = iconRotation,
                diagnosticsOpen = showDiagnostics,
                onToggleDiagnostics = { showDiagnostics = !showDiagnostics },
                onOpenSettings = onOpenSettings
            )
            DiagnosticsPanel(
                visible = showDiagnostics,
                rows = listOf(
                    stringResource(R.string.diag_camera) to listOf(
                        stringResource(if (capture.lens == LensFacing.BACK) R.string.diag_lens_back else R.string.diag_lens_front),
                        stringResource(settings.camera.aspectRatio.labelRes),
                        stringResource(settings.camera.resolution.labelRes),
                        zoomState?.let { ZoomPresets.label(it.zoomRatio) }
                    ).filterNotNull().joinToString(" · "),
                    stringResource(R.string.diag_location) to (location?.displayText() ?: stringResource(R.string.diag_none)),
                    stringResource(R.string.diag_compass) to (compass?.displayText() ?: stringResource(R.string.diag_none)),
                    stringResource(R.string.diag_last_capture) to (capture.lastCaptureInfo ?: stringResource(R.string.diag_none))
                )
            )
        }

        SideControls(
            camera = settings.camera,
            mode = capture.mode,
            hasFlashUnit = capture.hasFlashUnit,
            iconRotation = iconRotation,
            onCameraChange = viewModel::updateCameraSettings,
            onOpenSheet = { showSheet = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 64.dp, end = Dimens.SpaceM)
        )

        BottomControls(
            viewModel = viewModel,
            capture = capture,
            zoomPresets = zoomState?.let { ZoomPresets.forRange(it.minZoomRatio, it.maxZoomRatio) }.orEmpty(),
            zoomRatio = zoomState?.zoomRatio ?: 1f,
            stampEnabled = settings.stamp.enabled,
            iconRotation = iconRotation,
            onOpenGallery = onOpenGallery,
            onVideoModeSelected = onVideoModeSelected,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        SnackbarHost(
            snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 180.dp)
        )
    }

    if (showSheet) {
        val exposure = viewModel.session.exposureState()?.let { state ->
            ExposureUi(
                index = state.exposureCompensationIndex,
                min = if (state.isExposureCompensationSupported) state.exposureCompensationRange.lower else 0,
                max = if (state.isExposureCompensationSupported) state.exposureCompensationRange.upper else 0,
                stepEv = state.exposureCompensationStep.toFloat()
            )
        }
        CameraControlsSheet(
            settings = settings,
            mode = capture.mode,
            hasFlashUnit = capture.hasFlashUnit,
            exposure = exposure,
            zoom = zoomState?.let { ZoomUi(it.zoomRatio, it.minZoomRatio, it.maxZoomRatio) },
            onDismiss = { showSheet = false },
            onCameraChange = viewModel::updateCameraSettings,
            onSettingsChange = viewModel::updateSettings,
            onExposure = viewModel::setExposure,
            onZoom = viewModel::setZoom,
            onCustomizeStamp = {
                showSheet = false
                onOpenStampSettings()
            }
        )
    }
}

@Composable
private fun BottomControls(
    viewModel: CameraViewModel,
    capture: CaptureUiState,
    zoomPresets: List<Float>,
    zoomRatio: Float,
    stampEnabled: Boolean,
    iconRotation: Float,
    onOpenGallery: () -> Unit,
    onVideoModeSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, CameraColors.ScrimStrong)))
            .navigationBarsPadding()
            .padding(top = Dimens.SpaceXl, bottom = Dimens.SpaceL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)
    ) {
        RecordingBadge(capture.recordingSeconds)
        when {
            capture.mode == CaptureMode.VIDEO && capture.recordingSeconds == null ->
                CameraChip(stringResource(R.string.video_unstamped_note))
            capture.mode == CaptureMode.PHOTO && !stampEnabled ->
                CameraChip(stringResource(R.string.stamp_off_badge))
        }
        ZoomSelector(presets = zoomPresets, current = zoomRatio, onSelect = viewModel::setZoom)
        ModeSelector(
            mode = capture.mode,
            enabled = capture.recordingSeconds == null,
            onSelect = { mode ->
                if (mode == CaptureMode.VIDEO) onVideoModeSelected()
                viewModel.setMode(mode)
            }
        )
        CaptureRow(
            mode = capture.mode,
            isRecording = capture.recordingSeconds != null,
            countdownActive = capture.countdown != null,
            isProcessing = capture.isProcessing,
            thumbnail = capture.lastCapture?.thumbnail,
            canSwitchCamera = capture.hasFrontCamera,
            iconRotation = iconRotation,
            onShutter = viewModel::onShutter,
            onOpenGallery = onOpenGallery,
            onSwitchCamera = viewModel::switchLens
        )
    }
}

@Composable
private fun CameraPermissionScreen(onRequest: () -> Unit, onOpenAppSettings: () -> Unit) {
    Box(Modifier.fillMaxSize().background(CameraColors.Background).padding(Dimens.SpaceXl), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 420.dp)) {
            Text(
                stringResource(R.string.permission_camera_title),
                color = CameraColors.Content,
                style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Dimens.SpaceM))
            Text(
                stringResource(R.string.permission_camera_body),
                color = CameraColors.ContentMuted,
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Dimens.SpaceXl))
            Button(onClick = onRequest) { Text(stringResource(R.string.permission_grant)) }
            TextButton(onClick = onOpenAppSettings) { Text(stringResource(R.string.permission_open_settings), color = CameraColors.Content) }
        }
    }
}

/** The camera UI stays portrait like native camera apps; icons rotate instead and CameraX orients the photo. */
@SuppressLint("SourceLockedOrientationActivity")
@Composable
private fun KeepPortrait() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(activity) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
}

private fun android.content.Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED


