package com.geostamp.camera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.SeekBar
import androidx.camera.core.Camera
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.geostamp.camera.location.LocationStamp
import com.geostamp.camera.stamps.StampTextFormatter
import java.util.Locale
import java.util.concurrent.Executors

/** Initial offline prototype. No network or background location access. */
class MainActivity : ComponentActivity() {
    private lateinit var preview: PreviewView
    private lateinit var status: TextView
    private var imageCapture: ImageCapture? = null
    private var boundCamera: Camera? = null
    private var frontCamera = false
    private var flashEnabled = false
    @Volatile private var lastLocation: Location? = null
    private val worker = Executors.newSingleThreadExecutor()
    private val stampTextFormatter = StampTextFormatter()

    private val permissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            startCamera()
            updateLocation()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            text = "GeoStamp • Location optional"
            setPadding(20, 20, 20, 20)
        }
        preview = PreviewView(this)
        val shutter = Button(this).apply {
            text = "CAPTURE GPS PHOTO"
            setOnClickListener { takePhoto() }
        }
        root.addView(status)
        root.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val switch = Button(this).apply {
            text = "Switch camera"
            setOnClickListener { frontCamera = !frontCamera; startCamera() }
        }
        val flash = Button(this).apply {
            text = "Flash off"
            setOnClickListener {
                flashEnabled = !flashEnabled
                imageCapture?.flashMode = if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                text = if (flashEnabled) "Flash on" else "Flash off"
            }
        }
        controls.addView(switch, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(flash, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(controls)
        val zoom = SeekBar(this).apply {
            max = 100
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val state = boundCamera?.cameraInfo?.zoomState?.value ?: return
                        val ratio = state.minZoomRatio + (state.maxZoomRatio - state.minZoomRatio) * progress / 100f
                        boundCamera?.cameraControl?.setZoomRatio(ratio)
                    }
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        root.addView(zoom)
        root.addView(shutter, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
        permissionRequest.launch(arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    private fun updateLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            status.text = "Location denied • camera remains available"
            return
        }
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        val provider = when {
            fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> { status.text = "Location provider disabled"; return }
        }
        try {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    updateLocationStatus(location)
                }
            }
            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            Handler(Looper.getMainLooper()).postDelayed({
                if (lastLocation == null) {
                    status.text = "Location unavailable"
                }
            }, 10_000L)
        } catch (_: SecurityException) {
            status.text = "Location permission unavailable"
        } catch (_: IllegalArgumentException) {
            status.text = "Location provider unavailable"
        }
    }

    private fun updateLocationStatus(location: Location) {
        lastLocation = location
        status.text = "GPS ±${location.accuracy.toInt()}m • ${location.latitude}, ${location.longitude}"
    }

    private fun startCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Camera permission required"
            return
        }
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val cameraPreview = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                provider.unbindAll()
                boundCamera = provider.bindToLifecycle(this, if (frontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA, cameraPreview, imageCapture)
                imageCapture?.flashMode = if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
            } catch (e: Exception) {
                status.text = "Camera error: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        // Snapshot the last known location; a fresh location request is asynchronous.
        val location = lastLocation
            ?.takeIf { System.currentTimeMillis() - it.time < 120_000L }
            ?.let(LocationStamp::from)
        capture.takePicture(worker, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val bitmap = image.toBitmap()
                    val stamped = stamp(bitmap, location)
                    save(stamped)
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Saved to Pictures/GeoStamp", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                } finally {
                    image.close()
                }
            }
            override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, exception.message, Toast.LENGTH_LONG).show()
                }
            }
        })
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
        canvas.drawRect(0f, output.height - band, output.width.toFloat(), output.height.toFloat(),
            Paint().apply { color = 0xB9000000.toInt() })
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, size * 0.7f, output.height - band + size * (index + 1.25f), paint)
        }
        return output
    }

    private fun save(bitmap: Bitmap) {
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
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 93, it))
            } ?: error("Could not open photo")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }
}
