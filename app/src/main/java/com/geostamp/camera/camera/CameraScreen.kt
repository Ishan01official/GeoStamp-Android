package com.geostamp.camera.camera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GpsNotFixed
import androidx.compose.material3.SnackbarDuration
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.geostamp.camera.location.LocationUpdate
import com.geostamp.camera.permissions.LocationAccess
import com.geostamp.camera.permissions.PermissionPolicy
import com.geostamp.camera.video.VideoJobState
import com.geostamp.camera.video.VideoLimits
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

/** Snapshot of the runtime permissions the camera screen depends on. Refreshed on every resume. */
private data class PermissionSnapshot(val camera: Boolean, val fineLocation: Boolean, val coarseLocation: Boolean) {
    val locationAccess: LocationAccess get() = PermissionPolicy.locationAccess(fineLocation, coarseLocation)
}

private fun Context.permissionSnapshot() = PermissionSnapshot(
    camera = hasPermission(Manifest.permission.CAMERA),
    fineLocation = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION),
    coarseLocation = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
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
    val activity = context as? Activity
    val settings = viewModel.settings.collectAsStateWithLifecycle().value ?: return
    val onboarding = settings.onboarding
    var permissions by remember { mutableStateOf(context.permissionSnapshot()) }
    var showLocationHelp by rememberSaveable { mutableStateOf(false) }

    // Re-read after returning from app settings, where the user may have changed access.
    LifecycleResumeEffect(Unit) {
        val latest = context.permissionSnapshot()
        if (latest != permissions) {
            permissions = latest
            viewModel.onPermissionsChanged()
        }
        onPauseOrDispose { }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissions = context.permissionSnapshot()
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissions = context.permissionSnapshot()
        viewModel.onPermissionsChanged()
    }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun rationale(permission: String) = activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, permission) } ?: false
    fun openAppSettings() = context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    )
    fun requestLocation() {
        viewModel.updateSettings { it.copy(onboarding = it.onboarding.copy(locationPromptShown = true, locationRequested = true)) }
        locationLauncher.launch(LOCATION_PERMISSIONS)
    }

    KeepPortrait()
    LifecycleStartEffect(viewModel) {
        viewModel.onScreenStarted()
        onStopOrDispose { viewModel.onScreenStopped() }
    }

    if (!permissions.camera) {
        CameraIntroScreen(
            action = PermissionPolicy.action(
                granted = false,
                everRequested = onboarding.cameraRequested,
                shouldShowRationale = rationale(Manifest.permission.CAMERA)
            ),
            onRequest = {
                viewModel.updateSettings { it.copy(onboarding = it.onboarding.copy(cameraRequested = true)) }
                cameraLauncher.launch(Manifest.permission.CAMERA)
            },
            onOpenAppSettings = ::openAppSettings
        )
        return
    }

    CameraScreen(
        viewModel = viewModel,
        onOpenGallery = onOpenGallery,
        onOpenSettings = onOpenSettings,
        onOpenStampSettings = onOpenStampSettings,
        onOpenMedia = onOpenMedia,
        onLocationChipClick = { showLocationHelp = true },
        onVideoModeSelected = {
            if (!context.hasPermission(Manifest.permission.RECORD_AUDIO)) audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    )

    if (PermissionPolicy.shouldOfferLocation(permissions.camera, permissions.locationAccess, onboarding.locationPromptShown)) {
        LocationOfferDialog(
            onAllow = ::requestLocation,
            onNotNow = { viewModel.updateSettings { it.copy(onboarding = it.onboarding.copy(locationPromptShown = true)) } }
        )
    }

    if (showLocationHelp) {
        val location by viewModel.location.collectAsStateWithLifecycle()
        LocationHelpDialog(
            access = permissions.locationAccess,
            action = PermissionPolicy.action(
                granted = permissions.fineLocation,
                everRequested = onboarding.locationRequested,
                shouldShowRationale = rationale(Manifest.permission.ACCESS_FINE_LOCATION)
            ),
            providersOff = location is LocationUpdate.ProvidersDisabled,
            onRequest = {
                showLocationHelp = false
                requestLocation()
            },
            onOpenAppSettings = {
                showLocationHelp = false
                openAppSettings()
            },
            onOpenLocationSettings = {
                showLocationHelp = false
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            },
            onDismiss = { showLocationHelp = false }
        )
    }
}

@Composable
private fun CameraScreen(
    viewModel: CameraViewModel,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenStampSettings: () -> Unit,
    onOpenMedia: (Uri) -> Unit,
    onLocationChipClick: () -> Unit,
    onVideoModeSelected: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings = viewModel.settings.collectAsStateWithLifecycle().value ?: return
    val capture by viewModel.capture.collectAsStateWithLifecycle()
    val location by viewModel.location.collectAsStateWithLifecycle()
    val heading by viewModel.heading.collectAsStateWithLifecycle()
    val liveStamp by viewModel.liveStamp.collectAsStateWithLifecycle()
    val detectedAddress by viewModel.detectedAddress.collectAsStateWithLifecycle()
    val addressOverride by viewModel.addressOverride.collectAsStateWithLifecycle()
    var showAddressEditor by rememberSaveable { mutableStateOf(false) }
    val zoomState by viewModel.session.zoomState.observeAsState()
    val iconRotation = rememberIconRotation()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val shutterFlash = remember { Animatable(0f) }

    var showSheet by rememberSaveable { mutableStateOf(false) }
    var showDualInfo by rememberSaveable { mutableStateOf(false) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusKey by remember { mutableIntStateOf(0) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            controller = viewModel.session.controller
        }
    }
    LaunchedEffect(lifecycleOwner) { viewModel.session.bind(lifecycleOwner) }

    val gpsNotReady by viewModel.gpsNotReady.collectAsStateWithLifecycle()
    val videoJob by viewModel.videoJob.collectAsStateWithLifecycle()
    val savedWithoutGps = stringResource(R.string.gps_not_ready_saved)
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
                CameraEvent.SavedWithoutGps -> scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(savedWithoutGps)
                }
                is CameraEvent.VideoSavedWithoutStamp -> scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val result = snackbar.showSnackbar(
                        context.getString(event.messageRes),
                        actionLabel = viewAction,
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) onOpenMedia(event.uri)
                }
                CameraEvent.DualUnsupported -> showDualInfo = true
                is CameraEvent.DualFailed -> scope.launch {
                    snackbar.showSnackbar(context.getString(R.string.dual_runtime_failed), duration = SnackbarDuration.Long)
                }
                is CameraEvent.Failed -> scope.launch {
                    val message = context.getString(event.messageRes)
                    snackbar.showSnackbar(listOfNotNull(message, event.detail).joinToString(": "))
                }
            }
        }
    }

    val previewRatio = when {
        capture.mode.isVideoMode() -> PhotoAspectRatio.SIXTEEN_NINE.portraitWidthOverHeight
        capture.mode == CaptureMode.DUAL_PHOTO -> PhotoAspectRatio.FOUR_THREE.portraitWidthOverHeight
        else -> settings.camera.aspectRatio.portraitWidthOverHeight
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(CameraColors.Background)) {
        // On tall phones the status chips get their own row above the picture instead of covering it,
        // which also moves the preview closer to the controls.
        val viewfinderHeight = maxWidth / previewRatio
        val stacked = maxHeight - viewfinderHeight >= TOP_BAR_HEIGHT + MIN_CONTROLS_HEIGHT
        // Viewfinder: exact capture framing, never stretched.
        Box(
            Modifier
                .statusBarsPadding()
                .padding(top = if (stacked) TOP_BAR_HEIGHT else 0.dp)
                .fillMaxWidth()
                .aspectRatio(previewRatio)
                .align(Alignment.TopCenter)
                .onSizeChanged { viewModel.onOverlaySize(it.width, it.height) }
        ) {
            if (capture.mode.isDual()) {
                DualViewfinder(viewModel, capture.mode, settings.stamp.position)
            } else {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            }
            if (settings.camera.gridEnabled) GridOverlay()
            // Shown in video mode too: the same card is burned into every frame after recording.
            liveStamp?.let { bitmap ->
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
                    .pointerInput(settings.camera.tapToFocus, capture.mode) {
                        detectTapGestures { offset ->
                            if (!settings.camera.tapToFocus || capture.mode.isDual()) return@detectTapGestures
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
                heading = heading,
                hasCompass = viewModel.hasCompass,
                maxAccuracyMeters = settings.location.maxAccuracyMeters,
                simpleMode = settings.camera.simpleMode,
                iconRotation = iconRotation,
                onLocationClick = onLocationChipClick,
                onOpenSettings = onOpenSettings
            )
        }

        SideControls(
            camera = settings.camera,
            mode = capture.mode,
            hasFlashUnit = capture.hasFlashUnit,
            simpleMode = settings.camera.simpleMode,
            iconRotation = iconRotation,
            onCameraChange = viewModel::updateCameraSettings,
            onOpenSheet = { showSheet = true },
            onMoveInset = viewModel.dual::cycleCorner,
            onResizeInset = viewModel.dual::cycleSize,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = if (stacked) TOP_BAR_HEIGHT + Dimens.SpaceM else 64.dp, end = Dimens.SpaceM)
        )

        BottomControls(
            viewModel = viewModel,
            capture = capture,
            zoomPresets = zoomState?.let { ZoomPresets.forRange(it.minZoomRatio, it.maxZoomRatio) }.orEmpty(),
            zoomRatio = zoomState?.zoomRatio ?: 1f,
            stampEnabled = settings.stamp.enabled,
            videoJob = videoJob,
            gpsNotReady = gpsNotReady,
            addressBar = if (settings.stamp.enabled && settings.stamp.fields.address && capture.recordingSeconds == null) {
                { AddressBar(detectedAddress, addressOverride, onEdit = { showAddressEditor = true }, modifier = Modifier.padding(horizontal = Dimens.SpaceL)) }
            } else {
                null
            },
            simpleMode = settings.camera.simpleMode,
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

    if (showDualInfo) {
        DualUnsupportedDialog(viewModel.dualUnsupportedReason, onDismiss = { showDualInfo = false })
    }

    if (showAddressEditor) {
        AddressEditDialog(
            detected = detectedAddress,
            override = addressOverride,
            onSave = { text, scope ->
                viewModel.setAddressOverride(text, scope)
                showAddressEditor = false
            },
            onRestoreDetected = {
                viewModel.restoreDetectedAddress()
                showAddressEditor = false
            },
            onDismiss = { showAddressEditor = false }
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
    videoJob: VideoJobState,
    gpsNotReady: Boolean,
    addressBar: (@Composable () -> Unit)?,
    simpleMode: Boolean,
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
        RecordingBadge(capture.recordingSeconds, VideoLimits.MAX_DURATION_SECONDS, capture.recordingHasAudio)
        (videoJob as? VideoJobState.Stamping)?.let { VideoStampingCard(it.progressPercent, onCancel = viewModel::cancelVideoStamping) }
        when {
            capture.mode.isPhotoMode() && !stampEnabled ->
                CameraChip(stringResource(R.string.stamp_off_badge))
            gpsNotReady && capture.recordingSeconds == null ->
                CameraChip(stringResource(R.string.gps_not_ready), icon = Icons.Outlined.GpsNotFixed, iconTint = CameraColors.Warning)
        }
        addressBar?.invoke()
        if (!simpleMode) {
            ZoomSelector(presets = zoomPresets, current = zoomRatio, onSelect = viewModel::setZoom)
        }
        ModeSelector(
            mode = capture.mode,
            enabled = capture.recordingSeconds == null,
            supportedModes = capture.supportedModes,
            onSelect = { mode ->
                if (mode.isVideoMode()) onVideoModeSelected()
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

private val TOP_BAR_HEIGHT = 56.dp
private val MIN_CONTROLS_HEIGHT = 300.dp

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

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
