package com.geostamp.camera.camera

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Grid3x3
import androidx.compose.material.icons.outlined.GridOff
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Timer10
import androidx.compose.material.icons.outlined.Timer3
import androidx.compose.material.icons.outlined.TimerOff
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.geostamp.camera.R
import com.geostamp.camera.capture.CaptureMode
import com.geostamp.camera.capture.CaptureTimer
import com.geostamp.camera.capture.FlashMode
import com.geostamp.camera.capture.ZoomPresets
import com.geostamp.camera.settings.CameraSettings
import com.geostamp.camera.ui.components.CameraIconButton
import com.geostamp.camera.ui.labelRes
import com.geostamp.camera.ui.theme.CameraColors
import com.geostamp.camera.ui.theme.CameraLabel
import com.geostamp.camera.ui.theme.Dimens

/** The most used controls, one tap each. Everything else lives in the controls sheet. */
@Composable
fun SideControls(
    camera: CameraSettings,
    mode: CaptureMode,
    hasFlashUnit: Boolean,
    iconRotation: Float,
    onCameraChange: ((CameraSettings) -> CameraSettings) -> Unit,
    onOpenSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .clip(RoundedCornerShape(50))
            .background(CameraColors.Scrim)
            .padding(vertical = Dimens.SpaceXs),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val flashLabel = stringResource(camera.flashMode.labelRes)
        SideButton(
            icon = when (camera.flashMode) {
                FlashMode.OFF -> Icons.Outlined.FlashOff
                FlashMode.AUTO -> Icons.Outlined.FlashAuto
                FlashMode.ON -> Icons.Outlined.FlashOn
            },
            description = stringResource(R.string.cd_flash, flashLabel),
            enabled = hasFlashUnit,
            rotation = iconRotation
        ) { onCameraChange { it.copy(flashMode = it.flashMode.next()) } }

        if (mode == CaptureMode.PHOTO) {
            SideTextButton(
                text = stringResource(camera.aspectRatio.labelRes),
                description = stringResource(R.string.cd_aspect, stringResource(camera.aspectRatio.labelRes)),
                rotation = iconRotation
            ) { onCameraChange { it.copy(aspectRatio = it.aspectRatio.next()) } }
            SideTextButton(
                text = stringResource(camera.resolution.labelRes),
                description = stringResource(R.string.cd_resolution, stringResource(camera.resolution.labelRes)),
                rotation = iconRotation
            ) { onCameraChange { it.copy(resolution = it.resolution.next()) } }
            SideButton(
                icon = when (camera.timer) {
                    CaptureTimer.OFF -> Icons.Outlined.TimerOff
                    CaptureTimer.THREE_SECONDS -> Icons.Outlined.Timer3
                    CaptureTimer.TEN_SECONDS -> Icons.Outlined.Timer10
                },
                description = stringResource(R.string.cd_timer, stringResource(camera.timer.labelRes)),
                rotation = iconRotation,
                active = camera.timer != CaptureTimer.OFF
            ) { onCameraChange { it.copy(timer = it.timer.next()) } }
        }
        SideButton(
            icon = if (camera.gridEnabled) Icons.Outlined.Grid3x3 else Icons.Outlined.GridOff,
            description = stringResource(if (camera.gridEnabled) R.string.cd_grid_on else R.string.cd_grid_off),
            rotation = iconRotation,
            active = camera.gridEnabled
        ) { onCameraChange { it.copy(gridEnabled = !it.gridEnabled) } }
        SideButton(
            icon = Icons.Outlined.Tune,
            description = stringResource(R.string.cd_more_controls),
            rotation = iconRotation,
            onClick = onOpenSheet
        )
    }
}

@Composable
private fun SideButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    rotation: Float,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val animated by animateFloatAsState(rotation, label = "sideRotation")
    Box(
        Modifier
            .size(Dimens.TouchTarget)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = when {
                !enabled -> CameraColors.ContentMuted.copy(alpha = 0.35f)
                active -> CameraColors.Warning
                else -> CameraColors.Content
            },
            modifier = Modifier.size(Dimens.Icon).rotate(animated)
        )
    }
}

@Composable
private fun SideTextButton(text: String, description: String, rotation: Float, onClick: () -> Unit) {
    val animated by animateFloatAsState(rotation, label = "sideTextRotation")
    Box(
        Modifier
            .size(Dimens.TouchTarget)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = CameraLabel,
            color = CameraColors.Content,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.rotate(animated)
        )
    }
}

/** Pills for the zoom levels this camera supports; pinch covers everything in between. */
@Composable
fun ZoomSelector(
    presets: List<Float>,
    current: Float,
    onSelect: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    if (presets.size < 2) return
    val selected = ZoomPresets.selected(presets, current)
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(CameraColors.Scrim)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        presets.forEach { preset ->
            val isSelected = preset == selected
            val label = if (isSelected || selected == null && preset == presets.minBy { kotlin.math.abs(it - current) }) {
                ZoomPresets.label(current)
            } else {
                ZoomPresets.label(preset)
            }
            val size by animateDpAsState(if (isSelected) 40.dp else 34.dp, label = "zoomSize")
            val background by animateColorAsState(if (isSelected) CameraColors.Selected else CameraColors.Scrim, label = "zoomBg")
            val description = stringResource(R.string.cd_zoom, ZoomPresets.label(preset))
            Box(
                Modifier
                    .size(Dimens.TouchTarget - 4.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onSelect(preset) }
                    .semantics {
                        contentDescription = description
                        this.selected = isSelected
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        style = CameraLabel,
                        color = if (isSelected) CameraColors.OnSelected else CameraColors.Content,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun ModeSelector(mode: CaptureMode, enabled: Boolean, onSelect: (CaptureMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(CameraColors.Scrim)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        listOf(
            CaptureMode.PHOTO to (R.string.mode_photo to Icons.Outlined.Photo),
            CaptureMode.VIDEO to (R.string.mode_video to Icons.Outlined.Videocam)
        ).forEach { (option, labelAndIcon) ->
            val isSelected = option == mode
            val background by animateColorAsState(if (isSelected) CameraColors.Selected else CameraColors.Scrim.copy(alpha = 0f), label = "modeBg")
            Row(
                Modifier
                    .height(36.dp)
                    .widthIn(min = 84.dp)
                    .clip(RoundedCornerShape(50))
                    .background(background)
                    .clickable(enabled = enabled, role = Role.Tab) { onSelect(option) }
                    .semantics { selected = isSelected }
                    .padding(horizontal = Dimens.SpaceL),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    stringResource(labelAndIcon.first),
                    style = CameraLabel,
                    color = if (isSelected) CameraColors.OnSelected else CameraColors.Content
                )
            }
        }
    }
}

/** Gallery thumbnail | shutter | camera switch. */
@Composable
fun CaptureRow(
    mode: CaptureMode,
    isRecording: Boolean,
    countdownActive: Boolean,
    isProcessing: Boolean,
    thumbnail: Bitmap?,
    canSwitchCamera: Boolean,
    iconRotation: Float,
    onShutter: () -> Unit,
    onOpenGallery: () -> Unit,
    onSwitchCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceXl),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GalleryThumbnail(thumbnail, iconRotation, enabled = !isRecording, onClick = onOpenGallery)
        ShutterButton(mode, isRecording, countdownActive, isProcessing, onShutter)
        CameraIconButton(
            icon = Icons.Outlined.Cameraswitch,
            contentDescription = stringResource(R.string.cd_switch_camera),
            onClick = onSwitchCamera,
            enabled = canSwitchCamera && !isRecording,
            rotation = iconRotation,
            size = Dimens.Thumbnail
        )
    }
}

@Composable
private fun GalleryThumbnail(thumbnail: Bitmap?, rotation: Float, enabled: Boolean, onClick: () -> Unit) {
    val animated by animateFloatAsState(rotation, label = "thumbRotation")
    val description = stringResource(R.string.cd_gallery)
    Box(
        Modifier
            .size(Dimens.TouchTarget)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(Dimens.Thumbnail)
                .rotate(animated)
                .clip(RoundedCornerShape(Dimens.CornerSmall))
                .background(CameraColors.Scrim)
                .border(1.5.dp, CameraColors.Content.copy(alpha = 0.8f), RoundedCornerShape(Dimens.CornerSmall)),
            contentAlignment = Alignment.Center
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(Dimens.Thumbnail)
                )
            } else {
                Icon(Icons.Outlined.Photo, contentDescription = null, tint = CameraColors.ContentMuted, modifier = Modifier.size(Dimens.Icon))
            }
        }
    }
}

@Composable
private fun ShutterButton(
    mode: CaptureMode,
    isRecording: Boolean,
    countdownActive: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit
) {
    val description = stringResource(
        when {
            mode == CaptureMode.VIDEO && isRecording -> R.string.cd_shutter_video_stop
            mode == CaptureMode.VIDEO -> R.string.cd_shutter_video_start
            countdownActive -> R.string.cd_shutter_cancel_timer
            else -> R.string.cd_shutter_photo
        }
    )
    val innerSize by animateDpAsState(
        when {
            isRecording -> 30.dp
            isProcessing -> Dimens.ShutterInner - 8.dp
            else -> Dimens.ShutterInner
        },
        label = "shutterInner"
    )
    val innerColor by animateColorAsState(
        if (mode == CaptureMode.VIDEO) CameraColors.Recording else CameraColors.ShutterFill,
        label = "shutterColor"
    )
    Box(
        Modifier
            .size(Dimens.ShutterSize)
            .clip(CircleShape)
            .border(4.dp, CameraColors.ShutterRing, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(innerSize)
                .clip(if (isRecording) RoundedCornerShape(8.dp) else CircleShape)
                .background(innerColor)
        )
    }
}
