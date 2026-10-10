package com.geostamp.camera.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.GpsNotFixed
import androidx.compose.material.icons.outlined.GpsOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geostamp.camera.R
import com.geostamp.camera.location.LocationUpdate
import com.geostamp.camera.sensors.CompassAccuracy
import com.geostamp.camera.sensors.CompassHeading
import com.geostamp.camera.sensors.CompassUpdate
import com.geostamp.camera.ui.components.CameraChip
import com.geostamp.camera.ui.components.CameraIconButton
import com.geostamp.camera.ui.theme.CameraColors
import com.geostamp.camera.ui.theme.CameraLabel
import com.geostamp.camera.ui.theme.Dimens
import kotlin.math.roundToInt

/** GPS chip, heading chip, and details/settings buttons over the top of the preview. */
@Composable
fun TopOverlay(
    location: LocationUpdate?,
    compass: CompassUpdate?,
    maxAccuracyMeters: Int,
    simpleMode: Boolean,
    iconRotation: Float,
    diagnosticsOpen: Boolean,
    onToggleDiagnostics: () -> Unit,
    onLocationClick: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceM, vertical = Dimens.SpaceS)) {
        GpsChip(location, maxAccuracyMeters, simpleMode, onLocationClick, Modifier.align(Alignment.CenterStart))
        if (!simpleMode) CompassChip(compass, Modifier.align(Alignment.Center))
        Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
            CameraIconButton(
                icon = Icons.Outlined.Info,
                contentDescription = stringResource(R.string.cd_diagnostics),
                onClick = onToggleDiagnostics,
                rotation = iconRotation,
                active = diagnosticsOpen,
                size = 36.dp
            )
            CameraIconButton(
                icon = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.cd_settings),
                onClick = onOpenSettings,
                rotation = iconRotation,
                size = 36.dp
            )
        }
    }
}

@Composable
private fun GpsChip(location: LocationUpdate?, maxAccuracyMeters: Int, simpleMode: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val (icon, text, tint) = when (location) {
        is LocationUpdate.Available -> {
            val accuracy = location.location.accuracyMeters.roundToInt()
            val good = location.location.isAccurateEnough(maxAccuracyMeters.toFloat())
            Triple(
                if (good) Icons.Outlined.GpsFixed else Icons.Outlined.GpsNotFixed,
                when {
                    location.location.approximate -> stringResource(R.string.gps_approximate)
                    simpleMode -> stringResource(if (good) R.string.gps_ready else R.string.gps_improving)
                    else -> stringResource(R.string.gps_accuracy, accuracy)
                },
                if (good) CameraColors.Content else CameraColors.Warning
            )
        }
        is LocationUpdate.StaleLastKnown -> Triple(
            Icons.Outlined.GpsNotFixed,
            stringResource(if (simpleMode) R.string.gps_improving else R.string.gps_stale),
            CameraColors.Warning
        )
        LocationUpdate.PermissionDenied -> Triple(
            Icons.Outlined.GpsOff,
            stringResource(R.string.gps_denied),
            CameraColors.ContentMuted
        )
        is LocationUpdate.ProvidersDisabled, is LocationUpdate.ProviderUnavailable ->
            Triple(Icons.Outlined.GpsOff, stringResource(if (simpleMode) R.string.gps_unavailable else R.string.gps_off), CameraColors.ContentMuted)
        is LocationUpdate.Waiting, null -> Triple(
            Icons.Outlined.GpsNotFixed,
            stringResource(if (simpleMode) R.string.gps_improving else R.string.gps_searching),
            CameraColors.ContentMuted
        )
    }
    CameraChip(
        text = text,
        icon = icon,
        iconTint = tint,
        onClick = onClick,
        contentDescription = stringResource(R.string.cd_location_status, text),
        modifier = modifier
    )
}

@Composable
private fun CompassChip(compass: CompassUpdate?, modifier: Modifier) {
    when (compass) {
        is CompassUpdate.Available -> {
            val reading = compass.reading
            val degrees = reading.displayDegrees.roundToInt() % 360
            val text = if (reading.accuracy == CompassAccuracy.UNRELIABLE) {
                "${CompassHeading.cardinal(reading.displayDegrees)} $degrees° · ${stringResource(R.string.compass_calibrate)}"
            } else {
                "${CompassHeading.cardinal(reading.displayDegrees)} $degrees°"
            }
            CameraChip(text = text, icon = Icons.Outlined.Explore, modifier = modifier)
        }
        CompassUpdate.Unavailable -> CameraChip(
            text = stringResource(R.string.compass_unavailable),
            icon = Icons.Outlined.Explore,
            iconTint = CameraColors.ContentMuted,
            modifier = modifier
        )
        null -> Unit
    }
}

/** Collapsible technical details; kept out of the primary UI. */
@Composable
fun DiagnosticsPanel(visible: Boolean, rows: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Column(
            Modifier
                .padding(horizontal = Dimens.SpaceM)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.CornerMedium))
                .background(CameraColors.ScrimStrong)
                .padding(Dimens.SpaceM),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            rows.forEach { (label, value) ->
                Row {
                    Text(label, style = CameraLabel, color = CameraColors.ContentMuted, modifier = Modifier.weight(0.32f))
                    Text(value, style = CameraLabel, color = CameraColors.Content, modifier = Modifier.weight(0.68f))
                }
            }
        }
    }
}

@Composable
fun GridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val stroke = 1.dp.toPx()
        for (i in 1..2) {
            val x = size.width * i / 3f
            val y = size.height * i / 3f
            drawLine(CameraColors.GridLine, Offset(x, 0f), Offset(x, size.height), stroke)
            drawLine(CameraColors.GridLine, Offset(0f, y), Offset(size.width, y), stroke)
        }
    }
}

/** Animated focus ring: shrinks in, then fades out after metering completes. */
@Composable
fun FocusIndicator(point: Offset, key: Int, modifier: Modifier = Modifier) {
    val scale = remember(key) { Animatable(1.5f) }
    val alpha = remember(key) { Animatable(1f) }
    LaunchedEffect(key) {
        scale.animateTo(1f, tween(220))
        alpha.animateTo(1f, tween(900))
        alpha.animateTo(0f, tween(400))
    }
    val sizePx = with(LocalDensity.current) { 64.dp.toPx() }
    val description = stringResource(R.string.cd_focus)
    Canvas(modifier.fillMaxSize().semantics { contentDescription = description }) {
        val radius = sizePx / 2f * scale.value
        drawCircle(CameraColors.FocusRing.copy(alpha = alpha.value), radius, point, style = Stroke(1.5.dp.toPx()))
        drawCircle(CameraColors.FocusRing.copy(alpha = alpha.value), 2.dp.toPx(), point)
    }
}

@Composable
fun CountdownOverlay(seconds: Int?, modifier: Modifier = Modifier) {
    if (seconds == null) return
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = seconds.toString(),
            color = CameraColors.Content,
            fontSize = 96.sp,
            fontWeight = FontWeight.Light
        )
    }
}

@Composable
fun RecordingBadge(seconds: Long?, modifier: Modifier = Modifier) {
    if (seconds == null) return
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(CameraColors.Recording)
            .padding(horizontal = Dimens.SpaceM, vertical = Dimens.SpaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(CameraColors.Content))
        Text("%02d:%02d".format(seconds / 60, seconds % 60), style = CameraLabel, color = CameraColors.Content)
    }
}
