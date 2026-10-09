package com.geostamp.camera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.Surface
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.geostamp.camera.capture.CaptureTimer
import com.geostamp.camera.capture.FlashMode
import com.geostamp.camera.capture.PhotoAspectRatio
import com.geostamp.camera.capture.PhotoResolution
import com.geostamp.camera.location.ForegroundLocationTracker
import com.geostamp.camera.location.LocationStamp
import com.geostamp.camera.location.LocationUpdate
import com.geostamp.camera.sensors.CompassMonitor
import com.geostamp.camera.sensors.CompassUpdate
import com.geostamp.camera.stamps.StampTextFormatter
import com.geostamp.camera.ui.GridOverlayView
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Offline CameraX MVP. No network, account, analytics, or background location access. */
class MainActivity : ComponentActivity() {
    private lateinit var preview: PreviewView
    private lateinit var status: TextView
    private lateinit var gridOverlay: GridOverlayView
    private lateinit var flashButton: Button
    private lateinit var timerButton: Button
    private lateinit var aspectButton: Button
    private lateinit var resolutionButton: Button
    private lateinit var gridButton: Button
    private lateinit var zoomSlider: SeekBar
    private lateinit var exposureSlider: SeekBar
    private lateinit var exposureLabel: TextView
    private lateinit var lastPhotoPreview: ImageView
    private lateinit var lastPhotoLabel: TextView
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var locationTracker: ForegroundLocationTracker
    private lateinit var compassMonitor: CompassMonitor

    private var imageCapture: ImageCapture? = null
    private var boundCamera: Camera? = null
    private var frontCamera = false
    private var flashMode = FlashMode.OFF
    private var captureTimer = CaptureTimer.OFF
    private var photoAspectRatio = PhotoAspectRatio.FOUR_THREE
    private var photoResolution = PhotoResolution.DEFAULT
    private var gridEnabled = true
    private var pendingTimedCapture = false
    private var cameraStatusText = "Camera idle"
    private var locationStatusText = "Location waiting"
    private var compassStatusText = "Compass waiting"
    private var runtimePermissionsResolved = false

    @Volatile
    private var currentLocation: LocationStamp? = null

    private val worker = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stampTextFormatter = StampTextFormatter()

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            runtimePermissionsResolved = true
            if (grants[Manifest.permission.CAMERA] == true) {
                startCamera()
            } else {
                setCameraStatus("Camera permission required")
            }
            startLocationTracking()
            startCompass()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationTracker = ForegroundLocationTracker(this)
        compassMonitor = CompassMonitor(this)
        setContentView(buildCameraUi())
        permissionRequest.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    override fun onStart() {
        super.onStart()
        if (!runtimePermissionsResolved) return

        if (hasPermission(Manifest.permission.CAMERA)) {
            startCamera()
        }
        startLocationTracking()
        startCompass()
    }

    override fun onStop() {
        if (::locationTracker.isInitialized) {
            locationTracker.stop()
        }
        if (::compassMonitor.isInitialized) {
            compassMonitor.stop()
        }
        super.onStop()
    }

    private fun buildCameraUi(): LinearLayout {
        preview = PreviewView(this)
        setupPreviewGestures()

        gridOverlay = GridOverlayView(this)
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            text = "GeoStamp camera"
            setPadding(20, 20, 20, 20)
        }

        lastPhotoPreview = ImageView(this).apply {
            setBackgroundColor(Color.DKGRAY)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        lastPhotoLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
            text = "Last photo: none"
            setPadding(12, 0, 12, 0)
        }

        val previewFrame = FrameLayout(this).apply {
            addView(preview, fullFrameLayoutParams())
            addView(gridOverlay, fullFrameLayoutParams())
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(status)
            addView(previewFrame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(buildMainControls())
            addView(buildModeControls())
            addView(buildZoomControls())
            addView(buildExposureControls())
            addView(buildLastPhotoRow())
        }
        applySystemBarsPadding(root)
        return root
    }

    private fun applySystemBarsPadding(root: LinearLayout) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun fullFrameLayoutParams(): FrameLayout.LayoutParams =
        FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

    private fun buildMainControls(): LinearLayout {
        val captureButton = Button(this).apply {
            text = "Capture"
            setOnClickListener { takePhoto() }
        }
        val switchButton = Button(this).apply {
            text = "Switch"
            setOnClickListener {
                frontCamera = !frontCamera
                startCamera()
            }
        }
        flashButton = Button(this).apply {
            text = flashMode.label
            setOnClickListener {
                flashMode = flashMode.next()
                applyFlashMode()
            }
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(switchButton, weightedControlParams())
            addView(flashButton, weightedControlParams())
            addView(captureButton, weightedControlParams())
        }
    }

    private fun buildModeControls(): LinearLayout {
        timerButton = Button(this).apply {
            text = captureTimer.label
            setOnClickListener {
                captureTimer = captureTimer.next()
                text = captureTimer.label
            }
        }
        aspectButton = Button(this).apply {
            text = photoAspectRatio.label
            setOnClickListener {
                photoAspectRatio = photoAspectRatio.next()
                text = photoAspectRatio.label
                startCamera()
            }
        }
        resolutionButton = Button(this).apply {
            text = photoResolution.label
            setOnClickListener {
                photoResolution = photoResolution.next()
                text = photoResolution.label
                startCamera()
            }
        }
        gridButton = Button(this).apply {
            text = "Grid on"
            setOnClickListener {
                gridEnabled = !gridEnabled
                gridOverlay.visibility = if (gridEnabled) android.view.View.VISIBLE else android.view.View.GONE
                text = if (gridEnabled) "Grid on" else "Grid off"
            }
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(timerButton, weightedControlParams())
            addView(aspectButton, weightedControlParams())
            addView(resolutionButton, weightedControlParams())
            addView(gridButton, weightedControlParams())
        }
    }

    private fun buildZoomControls(): LinearLayout {
        val zoomLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
            text = "Zoom"
            setPadding(12, 0, 12, 0)
        }
        zoomSlider = SeekBar(this).apply {
            max = 100
            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            setZoomFromProgress(progress)
                        }
                    }

                    override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(bar: SeekBar?) = Unit
                }
            )
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(zoomLabel)
            addView(zoomSlider, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun buildExposureControls(): LinearLayout {
        exposureLabel = TextView(this).apply {
            setTextColor(Color.WHITE)
            text = "Exposure"
            setPadding(12, 0, 12, 0)
        }
        exposureSlider = SeekBar(this).apply {
            max = 0
            isEnabled = false
            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            setExposureFromProgress(progress)
                        }
                    }

                    override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(bar: SeekBar?) = Unit
                }
            )
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(exposureLabel)
            addView(exposureSlider, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun buildLastPhotoRow(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(12, 8, 12, 12)
            addView(lastPhotoPreview, LinearLayout.LayoutParams(120, 90))
            addView(lastPhotoLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

    private fun weightedControlParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    private fun setupPreviewGestures() {
        scaleGestureDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val camera = boundCamera ?: return false
                    val zoomState = camera.cameraInfo.zoomState.value ?: return false
                    val targetZoom = (zoomState.zoomRatio * detector.scaleFactor)
                        .coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                    camera.cameraControl.setZoomRatio(targetZoom)
                    updateZoomSlider(targetZoom, zoomState.minZoomRatio, zoomState.maxZoomRatio)
                    setCameraStatus("Zoom %.1fx".format(Locale.US, targetZoom))
                    return true
                }
            }
        )
        preview.setOnClickListener { }
        preview.setOnTouchListener { view, event ->
            scaleGestureDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP && !scaleGestureDetector.isInProgress) {
                view.performClick()
                focusAt(event.x, event.y)
            }
            true
        }
    }

    private fun focusAt(x: Float, y: Float) {
        val camera = boundCamera ?: return
        val meteringPoint = preview.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(
            meteringPoint,
            FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
        )
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()

        camera.cameraControl.startFocusAndMetering(action)
        setCameraStatus("Focus and exposure set")
    }

    private fun startLocationTracking() {
        locationTracker.start { update ->
            when (update) {
                is LocationUpdate.Available -> {
                    currentLocation = update.location
                    compassMonitor.setReferenceLocation(update.location)
                    val stampNote = if (update.location.isAccurateEnough(MAX_STAMP_ACCURACY_METERS)) {
                        ""
                    } else {
                        " - weak for stamp"
                    }
                    setLocationStatus(update.displayText() + stampNote)
                }
                else -> {
                    currentLocation = null
                    compassMonitor.setReferenceLocation(null)
                    setLocationStatus(update.displayText())
                }
            }
        }
    }

    private fun startCompass() {
        compassMonitor.start { update ->
            when (update) {
                CompassUpdate.Unavailable -> setCompassStatus(update.displayText())
                is CompassUpdate.Available -> setCompassStatus(update.displayText())
            }
        }
    }

    private fun startCamera() {
        if (!hasPermission(Manifest.permission.CAMERA)) {
            setCameraStatus("Camera permission required")
            return
        }
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                try {
                    bindCamera(future.get(), allowResolutionFallback = true)
                } catch (e: Exception) {
                    setCameraStatus("Camera error: ${e.message}")
                }
            },
            ContextCompat.getMainExecutor(this)
        )
    }

    private fun bindCamera(provider: ProcessCameraProvider, allowResolutionFallback: Boolean) {
        val selector = requestedCameraSelector(provider)
        val rotation = currentDisplayRotation()
        val cameraPreview = Preview.Builder()
            .setTargetAspectRatio(photoAspectRatio.cameraXRatio)
            .setTargetRotation(rotation)
            .build()
            .also { it.surfaceProvider = preview.surfaceProvider }

        val capture = buildImageCapture(rotation)

        try {
            provider.unbindAll()
            boundCamera = provider.bindToLifecycle(this, selector, cameraPreview, capture)
            imageCapture = capture
            configureCameraControls()
            setCameraStatus("Camera ready: ${photoAspectRatio.label}, ${photoResolution.label}")
        } catch (e: IllegalArgumentException) {
            if (allowResolutionFallback && photoResolution != PhotoResolution.DEFAULT) {
                photoResolution = PhotoResolution.DEFAULT
                resolutionButton.text = photoResolution.label
                bindCamera(provider, allowResolutionFallback = false)
            } else {
                throw e
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun buildImageCapture(rotation: Int): ImageCapture {
        val builder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetRotation(rotation)
        val targetResolution = photoResolution.targetSize(photoAspectRatio)
        if (targetResolution == null) {
            builder.setTargetAspectRatio(photoAspectRatio.cameraXRatio)
        } else {
            builder.setTargetResolution(targetResolution)
        }
        return builder.build()
    }

    private fun requestedCameraSelector(provider: ProcessCameraProvider): CameraSelector {
        val requestedSelector = if (frontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        if (provider.hasCamera(requestedSelector)) return requestedSelector

        frontCamera = false
        return CameraSelector.DEFAULT_BACK_CAMERA
    }

    private fun configureCameraControls() {
        val camera = boundCamera ?: return
        if (!camera.cameraInfo.hasFlashUnit()) {
            flashMode = FlashMode.OFF
            flashButton.isEnabled = false
        } else {
            flashButton.isEnabled = true
        }
        applyFlashMode()
        configureZoomSlider(camera)
        configureExposureSlider(camera)
    }

    private fun applyFlashMode() {
        imageCapture?.flashMode = flashMode.imageCaptureMode
        flashButton.text = flashMode.label
    }

    private fun configureZoomSlider(camera: Camera) {
        val zoomState = camera.cameraInfo.zoomState.value ?: return
        zoomSlider.isEnabled = zoomState.maxZoomRatio > zoomState.minZoomRatio
        updateZoomSlider(zoomState.zoomRatio, zoomState.minZoomRatio, zoomState.maxZoomRatio)
    }

    private fun updateZoomSlider(current: Float, minimum: Float, maximum: Float) {
        if (maximum <= minimum) {
            zoomSlider.progress = 0
            return
        }
        val progress = (((current - minimum) / (maximum - minimum)) * 100f)
            .toInt()
            .coerceIn(0, 100)
        zoomSlider.progress = progress
    }

    private fun setZoomFromProgress(progress: Int) {
        val camera = boundCamera ?: return
        val zoomState = camera.cameraInfo.zoomState.value ?: return
        val ratio = zoomState.minZoomRatio +
            (zoomState.maxZoomRatio - zoomState.minZoomRatio) * progress / 100f
        camera.cameraControl.setZoomRatio(ratio)
        setCameraStatus("Zoom %.1fx".format(Locale.US, ratio))
    }

    private fun configureExposureSlider(camera: Camera) {
        val exposureState = camera.cameraInfo.exposureState
        val range = exposureState.exposureCompensationRange
        val supported = exposureState.isExposureCompensationSupported && range.upper > range.lower
        exposureSlider.isEnabled = supported
        exposureSlider.max = if (supported) range.upper - range.lower else 0
        exposureSlider.progress = if (supported) {
            exposureState.exposureCompensationIndex - range.lower
        } else {
            0
        }
        exposureLabel.text = if (supported) {
            "Exposure ${exposureState.exposureCompensationIndex}"
        } else {
            "Exposure n/a"
        }
    }

    private fun setExposureFromProgress(progress: Int) {
        val camera = boundCamera ?: return
        val exposureState = camera.cameraInfo.exposureState
        val range = exposureState.exposureCompensationRange
        if (!exposureState.isExposureCompensationSupported) return

        val index = (range.lower + progress).coerceIn(range.lower, range.upper)
        camera.cameraControl.setExposureCompensationIndex(index)
        exposureLabel.text = "Exposure $index"
    }

    private fun takePhoto() {
        if (pendingTimedCapture) return
        if (captureTimer.seconds == 0) {
            captureNow()
            return
        }

        pendingTimedCapture = true
        runCountdown(captureTimer.seconds)
    }

    private fun runCountdown(remainingSeconds: Int) {
        if (remainingSeconds <= 0) {
            pendingTimedCapture = false
            captureNow()
            return
        }
        setCameraStatus("Capturing in $remainingSeconds")
        mainHandler.postDelayed({ runCountdown(remainingSeconds - 1) }, ONE_SECOND_MILLIS)
    }

    private fun captureNow() {
        val capture = imageCapture ?: return
        capture.targetRotation = currentDisplayRotation()
        val location = captureLocationForStamp()

        capture.takePicture(
            worker,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val bitmap = image.toUprightBitmap()
                        val stamped = stamp(bitmap, location)
                        val uri = save(stamped)
                        runOnUiThread {
                            updateLastPhotoPreview(stamped, uri)
                            Toast.makeText(
                                this@MainActivity,
                                "Saved to Pictures/GeoStamp",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(
                                this@MainActivity,
                                "Save failed: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } finally {
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, exception.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    private fun captureLocationForStamp(): LocationStamp? {
        val location = currentLocation ?: return null
        val nowMillis = System.currentTimeMillis()
        if (!location.isFresh(nowMillis, MAX_LOCATION_AGE_MILLIS)) {
            setCameraStatus("Location stale; capturing without GPS")
            return null
        }
        if (!location.isAccurateEnough(MAX_STAMP_ACCURACY_METERS)) {
            setCameraStatus("Location weak; capturing without GPS")
            return null
        }
        return location
    }

    private fun ImageProxy.toUprightBitmap(): Bitmap {
        val bitmap = toBitmap()
        val rotationDegrees = imageInfo.rotationDegrees
        if (rotationDegrees == 0) return bitmap

        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) {
            bitmap.recycle()
        }
        return rotated
    }

    private fun stamp(source: Bitmap, location: LocationStamp?): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val size = (output.width / 34f).coerceAtLeast(20f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            color = Color.WHITE
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
        }
        val lines = stampTextFormatter.lines(location)
        val band = size * (lines.size + 1.4f)
        canvas.drawRect(
            0f,
            output.height - band,
            output.width.toFloat(),
            output.height.toFloat(),
            Paint().apply { color = 0xB9000000.toInt() }
        )
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, size * 0.7f, output.height - band + size * (index + 1.25f), paint)
        }
        return output
    }

    private fun save(bitmap: Bitmap): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "GeoStamp_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/GeoStamp")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create photo")
        try {
            contentResolver.openOutputStream(uri)?.use {
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it))
            } ?: error("Could not open photo")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            return uri
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }
    }

    private fun updateLastPhotoPreview(bitmap: Bitmap, uri: Uri) {
        val thumbnail = bitmap.thumbnail(maxWidth = 220)
        lastPhotoPreview.setImageBitmap(thumbnail)
        lastPhotoLabel.text = "Last photo saved: ${uri.lastPathSegment ?: "GeoStamp"}"
        setCameraStatus("Photo saved")
    }

    private fun Bitmap.thumbnail(maxWidth: Int): Bitmap {
        if (width <= maxWidth) return this
        val scale = maxWidth.toFloat() / width
        return Bitmap.createScaledBitmap(this, maxWidth, (height * scale).toInt().coerceAtLeast(1), true)
    }

    private fun currentDisplayRotation(): Int =
        preview.display?.rotation ?: Surface.ROTATION_0

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun setCameraStatus(text: String) {
        cameraStatusText = text
        renderStatus()
    }

    private fun setLocationStatus(text: String) {
        locationStatusText = text
        renderStatus()
    }

    private fun setCompassStatus(text: String) {
        compassStatusText = text
        renderStatus()
    }

    private fun renderStatus() {
        status.text = listOf(cameraStatusText, locationStatusText, compassStatusText)
            .joinToString(separator = "\n")
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        if (::locationTracker.isInitialized) {
            locationTracker.stop()
        }
        if (::compassMonitor.isInitialized) {
            compassMonitor.stop()
        }
        worker.shutdown()
        super.onDestroy()
    }

    private companion object {
        const val JPEG_QUALITY = 93
        const val MAX_LOCATION_AGE_MILLIS = 120_000L
        const val MAX_STAMP_ACCURACY_METERS = 100f
        const val ONE_SECOND_MILLIS = 1_000L
    }
}
