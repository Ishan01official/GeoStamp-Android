package com.geostamp.camera

import android.app.Application
import android.content.Context
import com.geostamp.camera.address.AddressOverrideRepository
import com.geostamp.camera.capture.MediaStoreWriter
import com.geostamp.camera.capture.PhotoProcessor
import com.geostamp.camera.camera.CameraCapabilityRepository
import com.geostamp.camera.environment.AddressResolver
import com.geostamp.camera.environment.EnvironmentRepository
import com.geostamp.camera.environment.MapTileRenderer
import com.geostamp.camera.environment.WeatherClient
import com.geostamp.camera.gallery.BatchStamper
import com.geostamp.camera.gallery.GalleryRepository
import com.geostamp.camera.location.ForegroundLocationTracker
import com.geostamp.camera.location.LocationRepository
import com.geostamp.camera.sensors.CompassMonitor
import com.geostamp.camera.sensors.CompassRepository
import com.geostamp.camera.settings.SettingsRepository
import com.geostamp.camera.stamps.StampResources
import com.geostamp.camera.video.VideoCaptureCoordinator
import com.geostamp.camera.video.VideoStampProcessor
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob

class GeoStampApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Publish recordings orphaned by a process death before the camera can start a new one.
        container.applicationScope.launch(Dispatchers.IO) { container.videoCapture.recoverInterrupted() }
    }
}

/** Manual dependency container; small enough that a DI framework would be overkill. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val applicationScope = CoroutineScope(SupervisorJob())

    val settingsRepository = SettingsRepository(appContext)
    val addressOverrides = AddressOverrideRepository()
    val cameraCapabilityRepository = CameraCapabilityRepository(appContext)
    val locationRepository = LocationRepository(ForegroundLocationTracker(appContext))
    val compassRepository = CompassRepository(CompassMonitor(appContext))
    val stampResources = StampResources(appContext)
    val mediaStoreWriter = MediaStoreWriter(appContext.contentResolver)
    val galleryRepository = GalleryRepository(appContext.contentResolver)
    val environmentRepository = EnvironmentRepository(
        scope = applicationScope,
        addressResolver = AddressResolver(appContext),
        weatherClient = WeatherClient(),
        mapTileRenderer = MapTileRenderer(appContext.cacheDir)
    )
    val photoProcessor = PhotoProcessor(
        writer = mediaStoreWriter,
        contentBuilder = { stampResources.contentBuilder() },
        renderer = { stampResources.renderer() }
    )
    val videoCapture = VideoCaptureCoordinator(
        workDir = File(appContext.noBackupFilesDir, "recordings"),
        writer = mediaStoreWriter,
        processor = VideoStampProcessor(
            context = appContext,
            contentBuilder = { stampResources.contentBuilder() },
            renderer = { stampResources.renderer() }
        ),
        scope = applicationScope
    )
    val batchStamper = BatchStamper(appContext.contentResolver, photoProcessor, mediaStoreWriter, environmentRepository)
}

val Context.appContainer: AppContainer
    get() = (applicationContext as GeoStampApplication).container
