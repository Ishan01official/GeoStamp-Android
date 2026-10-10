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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.GpsNotFixed
import androidx.compose.material.icons.outlined.GpsOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geostamp.camera.R
import com.geostamp.camera.location.LocationUpdate
import com.geostamp.camera.sensors.CompassAccuracy
import com.geostamp.camera.sensors.HeadingFormatter
import com.geostamp.camera.sensors.HeadingKind
import com.geostamp.camera.sensors.HeadingLabels
import com.geostamp.camera.sensors.HeadingSnapshot
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
    heading: HeadingSnapshot?,
    hasCompass: Boolean,
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
        if (!simpleMode) HeadingChip(heading, hasCompass, Modifier.align(Alignment.Center))
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

/** Shows the same snapshot the stamp uses, labelled as compass heading or travel course. */
@Composable
private fun HeadingChip(heading: HeadingSnapshot?, hasCompass: Boolean, modifier: Modifier) {
    val labels = rememberHeadingLabels()
    when {
        heading != null -> {
            val text = HeadingFormatter.format(heading, labels)
            val calibrate = heading.accuracy == CompassAccuracy.UNRELIABLE && heading.kind != HeadingKind.COURSE
            CameraChip(
                text = if (calibrate) "$text · ${stringResource(R.string.compass_calibrate)}" else text,
                icon = if (heading.kind == HeadingKind.COURSE) Icons.Outlined.Navigation else Icons.Outlined.Explore,
                modifier = modifier
            )
        }
        else -> CameraChip(
            text = stringResource(if (hasCompass) R.string.heading_waiting else R.string.heading_unavailable),
            icon = Icons.Outlined.Explore,
            iconTint = CameraColors.ContentMuted,
            modifier = modifier
        )
    }
}

@Composable
private fun rememberHeadingLabels(): HeadingLabels {
    val trueHeading = stringResource(R.string.heading_true_format)
    val magnetic = stringResource(R.string.heading_magnetic_format)
    val course = stringResource(R.string.heading_course_format)
    return remember(trueHeading, magnetic, course) { HeadingLabels(trueHeading, magnetic, course) }
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

/** Large, high-contrast recording status: elapsed time, the one-minute limit, and a warning near the end. */
@Composable
fun RecordingBadge(seconds: Long?, limitSeconds: Long, hasAudio: Boolean, modifier: Modifier = Modifier) {
    if (seconds == null) return
    val remaining = (limitSeconds - seconds).coerceAtLeast(0)
    val status = stringResource(R.string.rec_status, formatClock(seconds), formatClock(limitSeconds))
    val description = stringResource(R.string.cd_recording_status, seconds, limitSeconds)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs), modifier = modifier) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(CameraColors.Recording)
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    liveRegion = LiveRegionMode.Polite
                }
                .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
        ) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(CameraColors.Content))
            Text(status, style = CameraLabel.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), color = CameraColors.Content)
        }
        LinearProgressIndicator(
            progress = { seconds.toFloat() / limitSeconds },
            color = CameraColors.Recording,
            trackColor = CameraColors.Scrim,
            modifier = Modifier.width(160.dp)
        )
        when {
            remaining <= RECORDING_WARNING_SECONDS ->
                CameraChip(stringResource(R.string.rec_seconds_left, remaining.toInt()), iconTint = CameraColors.Warning)
            !hasAudio -> CameraChip(stringResource(R.string.rec_no_audio), icon = Icons.Outlined.MicOff)
        }
    }
}

/** Shown while the stamp is being added to a finished recording. Cancel keeps the recording, unstamped. */
@Composable
fun VideoStampingCard(progressPercent: Int?, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val label = if (progressPercent == null) {
        stringResource(R.string.video_stamping_preparing)
    } else {
        stringResource(R.string.video_stamping_progress, progressPercent)
    }
    Row(
        modifier
            .padding(horizontal = Dimens.SpaceL)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.CornerMedium))
            .background(CameraColors.ScrimStrong)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
            Text(label, style = CameraLabel.copy(fontSize = 16.sp), color = CameraColors.Content)
            if (progressPercent == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { progressPercent / 100f }, modifier = Modifier.fillMaxWidth())
            }
        }
        TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = Dimens.TouchTarget)) {
            Text(stringResource(R.string.cancel), color = CameraColors.Content)
        }
    }
}

private fun formatClock(seconds: Long): String = "%02d:%02d".format(seconds / 60, seconds % 60)

private const val RECORDING_WARNING_SECONDS = 10L
