package com.geostamp.camera.navigation

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.geostamp.camera.camera.CameraRoute
import com.geostamp.camera.gallery.GalleryScreen
import com.geostamp.camera.gallery.GalleryViewModel
import com.geostamp.camera.gallery.MediaViewerScreen
import com.geostamp.camera.settings.MapLinkProvider
import com.geostamp.camera.settings.SettingsScreen
import com.geostamp.camera.settings.SettingsViewModel
import com.geostamp.camera.stamps.ui.StampSettingsScreen

private object Routes {
    const val CAMERA = "camera"
    const val GALLERY = "gallery"
    const val VIEWER = "viewer?uri={uri}"
    const val SETTINGS = "settings"
    const val STAMP = "stamp"

    fun viewer(uri: Uri) = "viewer?uri=${Uri.encode(uri.toString())}"
}

@Composable
fun GeoStampNavHost(settingsViewModel: SettingsViewModel) {
    val navController = rememberNavController()
    // Gallery state is shared by the grid and the viewer so deletes and consent prompts stay in sync.
    val activity = LocalContext.current as ComponentActivity
    val galleryViewModel: GalleryViewModel = viewModel(activity)

    NavHost(navController = navController, startDestination = Routes.CAMERA) {
        composable(Routes.CAMERA) {
            CameraRoute(
                onOpenGallery = { navController.navigate(Routes.GALLERY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenStampSettings = { navController.navigate(Routes.STAMP) },
                onOpenMedia = { navController.navigate(Routes.viewer(it)) }
            )
        }
        composable(Routes.GALLERY) {
            GalleryScreen(
                viewModel = galleryViewModel,
                onBack = { navController.popBackStack() },
                onOpenItem = { navController.navigate(Routes.viewer(it.uri)) }
            )
        }
        composable(Routes.VIEWER, arguments = listOf(navArgument("uri") { type = NavType.StringType })) { entry ->
            val uri = Uri.parse(entry.arguments?.getString("uri").orEmpty())
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            MediaViewerScreen(
                uri = uri,
                viewModel = galleryViewModel,
                mapLinkProvider = settings?.services?.mapLinkProvider ?: MapLinkProvider.OPEN_STREET_MAP,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { navController.popBackStack() },
                onOpenStampSettings = { navController.navigate(Routes.STAMP) }
            )
        }
        composable(Routes.STAMP) {
            StampSettingsScreen(viewModel = settingsViewModel, onBack = { navController.popBackStack() })
        }
    }
}
