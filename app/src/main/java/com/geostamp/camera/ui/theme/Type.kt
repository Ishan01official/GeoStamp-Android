package com.geostamp.camera.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val base = Typography()

/** System font with tightened headings and slightly calmer weights for a clean hierarchy. */
internal val GeoStampTypography = Typography(
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = base.labelSmall
)

/** Monospaced digits keep live readouts (accuracy, heading, timers) from jittering. */
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

/** Camera overlay text. 15sp minimum for readability by older users; still follows system font scale. */
val CameraLabel = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum")
