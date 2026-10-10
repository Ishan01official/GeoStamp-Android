package com.geostamp.camera

import android.content.Context
import android.os.Bundle
import com.geostamp.camera.i18n.AppLanguage
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geostamp.camera.navigation.GeoStampNavHost
import com.geostamp.camera.settings.SettingsViewModel
import com.geostamp.camera.ui.theme.GeoStampTheme
import com.geostamp.camera.ui.theme.isDarkTheme

/** Single-activity Compose host. Screens, camera and stamping logic live in their own packages. */
class MainActivity : ComponentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels()

    /** Applies a language chosen in GeoStamp's settings on Android 12 and earlier; a no-op on 13+. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val appearance = settings?.appearance ?: return@setContent
            val dark = isDarkTheme(appearance.themeMode, isSystemInDarkTheme())
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            GeoStampTheme(themeMode = appearance.themeMode, dynamicColor = appearance.dynamicColor) {
                GeoStampNavHost(settingsViewModel)
            }
        }
    }
}
