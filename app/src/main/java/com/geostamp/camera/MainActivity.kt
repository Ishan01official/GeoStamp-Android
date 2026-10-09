package com.geostamp.camera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Initial offline prototype. No network or background location access. */
class MainActivity : ComponentActivity() {
    private lateinit var preview: PreviewView
    private lateinit var status: TextView
    private var imageCapture: ImageCapture? = null
    @Volatile private var lastLocation: Location? = null
    private val worker = Executors.newSingleThreadExecutor()

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
            manager.getCurrentLocation(provider, null, mainExecutor) { location ->
                lastLocation = location
                status.text = if (location == null) "Location unavailable" else
                    "GPS ±${location.accuracy.toInt()}m • ${location.latitude}, ${location.longitude}"
            }
        } catch (_: SecurityException) {
            status.text = "Location permission unavailable"
        } catch (_: IllegalArgumentException) {
            status.text = "Location provider unavailable"
        }
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
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, cameraPreview, imageCapture)
            } catch (e: Exception) {
                status.text = "Camera error: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        // Snapshot the last known location; a fresh location request is asynchronous.
        val location = lastLocation?.takeIf { System.currentTimeMillis() - it.time < 120_000L }
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

    private fun stamp(source: Bitmap, location: Location?): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val size = (output.width / 34f).coerceAtLeast(20f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            color = Color.WHITE
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
        }
        val lines = listOf(
            SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault()).format(Date()),
            location?.let { "Lat: %.6f  Lon: %.6f".format(Locale.US, it.latitude, it.longitude) }
                ?: "Location unavailable",
            location?.let { "Accuracy: ±%.0f m".format(Locale.US, it.accuracy) }
                ?: "GeoStamp • offline"
        )
        val band = size * 4.4f
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
