package com.geostamp.camera

import android.app.Application
import android.content.Context
import com.geostamp.camera.capture.MediaStoreWriter
import com.geostamp.camera.capture.PhotoProcessor
import com.geostamp.camera.capture.VideoStampProcessor
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class GeoStampApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual dependency container; small enough that a DI framework would be overkill. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val applicationScope = CoroutineScope(SupervisorJob())

    val settingsRepository = SettingsRepository(appContext)
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
    val videoStampProcessor = VideoStampProcessor(
        context = appContext,
        writer = mediaStoreWriter,
        contentBuilder = { stampResources.contentBuilder() },
        renderer = { stampResources.renderer() }
    )
    val batchStamper = BatchStamper(appContext.contentResolver, photoProcessor, mediaStoreWriter, environmentRepository)
}

val Context.appContainer: AppContainer
    get() = (applicationContext as GeoStampApplication).container
