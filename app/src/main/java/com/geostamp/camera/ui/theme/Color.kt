package com.geostamp.camera.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Neutral fallback palette for devices without dynamic color. Deliberately accent-free. */
internal val NeutralLightColors = lightColorScheme(
    primary = Color(0xFF18181B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE7E7EA),
    onPrimaryContainer = Color(0xFF18181B),
    secondary = Color(0xFF52525B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDEDF0),
    onSecondaryContainer = Color(0xFF27272A),
    tertiary = Color(0xFF3F4A5A),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF18181B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF18181B),
    surfaceVariant = Color(0xFFF1F1F3),
    onSurfaceVariant = Color(0xFF5B5B63),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9F9FA),
    surfaceContainer = Color(0xFFF4F4F5),
    surfaceContainerHigh = Color(0xFFEEEEF0),
    surfaceContainerHighest = Color(0xFFE8E8EB),
    outline = Color(0xFFD4D4D8),
    outlineVariant = Color(0xFFE4E4E7),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF)
)

internal val NeutralDarkColors = darkColorScheme(
    primary = Color(0xFFECECEE),
    onPrimary = Color(0xFF18181B),
    primaryContainer = Color(0xFF2E2E33),
    onPrimaryContainer = Color(0xFFECECEE),
    secondary = Color(0xFFB4B4BC),
    onSecondary = Color(0xFF18181B),
    secondaryContainer = Color(0xFF2A2A2F),
    onSecondaryContainer = Color(0xFFE4E4E7),
    tertiary = Color(0xFFB8C4D6),
    onTertiary = Color(0xFF1B2330),
    background = Color(0xFF0F0F10),
    onBackground = Color(0xFFECECEE),
    surface = Color(0xFF0F0F10),
    onSurface = Color(0xFFECECEE),
    surfaceVariant = Color(0xFF232326),
    onSurfaceVariant = Color(0xFFA1A1AA),
    surfaceContainerLowest = Color(0xFF0A0A0B),
    surfaceContainerLow = Color(0xFF151517),
    surfaceContainer = Color(0xFF1A1A1C),
    surfaceContainerHigh = Color(0xFF212124),
    surfaceContainerHighest = Color(0xFF2A2A2D),
    outline = Color(0xFF3F3F46),
    outlineVariant = Color(0xFF2E2E33),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)

/**
 * Camera chrome stays neutral in both themes so controls are legible over any scene,
 * as on native camera apps.
 */
object CameraColors {
    val Background = Color(0xFF000000)
    val Scrim = Color(0x73000000)
    val ScrimStrong = Color(0xB3000000)
    val Content = Color(0xFFFFFFFF)
    val ContentMuted = Color(0xB3FFFFFF)
    val Selected = Color(0xFFFFFFFF)
    val OnSelected = Color(0xFF000000)
    val ShutterRing = Color(0xFFFFFFFF)
    val ShutterFill = Color(0xFFFFFFFF)
    val Recording = Color(0xFFE5484D)
    val FocusRing = Color(0xFFFFFFFF)
    val Warning = Color(0xFFFFC53D)
    val GridLine = Color(0x66FFFFFF)
}
