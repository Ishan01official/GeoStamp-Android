package com.geostamp.camera.camera

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesomeMosaic
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.geostamp.camera.R
import com.geostamp.camera.capture.CaptureMode
import com.geostamp.camera.capture.CaptureTimer
import com.geostamp.camera.capture.FlashMode
import com.geostamp.camera.capture.PhotoAspectRatio
import com.geostamp.camera.capture.PhotoResolution
import com.geostamp.camera.capture.ZoomPresets
import com.geostamp.camera.settings.AppSettings
import com.geostamp.camera.settings.CameraSettings
import com.geostamp.camera.stamps.StampTemplate
import com.geostamp.camera.ui.components.ClickRow
import com.geostamp.camera.ui.components.SegmentedRow
import com.geostamp.camera.ui.components.SliderRow
import com.geostamp.camera.ui.components.SwitchRow
import com.geostamp.camera.ui.labelRes
import com.geostamp.camera.ui.theme.Dimens
import java.util.Locale
import kotlin.math.roundToInt

data class ExposureUi(val index: Int, val min: Int, val max: Int, val stepEv: Float)

data class ZoomUi(val current: Float, val min: Float, val max: Float)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraControlsSheet(
    settings: AppSettings,
    mode: CaptureMode,
    hasFlashUnit: Boolean,
    exposure: ExposureUi?,
    zoom: ZoomUi?,
    onDismiss: () -> Unit,
    onCameraChange: ((CameraSettings) -> CameraSettings) -> Unit,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onExposure: (Int) -> Unit,
    onZoom: (Float) -> Unit,
    onCustomizeStamp: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val camera = settings.camera
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = Dimens.SpaceL)
        ) {
            Text(
                stringResource(R.string.controls_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS)
            )
            if (mode.isPhotoMode()) {
                SegmentedRow(
                    title = stringResource(R.string.ctl_resolution),
                    options = PhotoResolution.entries,
                    selected = camera.resolution,
                    label = { stringResource(it.labelRes) },
                    onSelect = { choice -> onCameraChange { it.copy(resolution = choice) } }
                )
                SegmentedRow(
                    title = stringResource(R.string.ctl_aspect),
                    options = PhotoAspectRatio.entries,
                    selected = camera.aspectRatio,
                    label = { stringResource(it.labelRes) },
                    onSelect = { choice -> onCameraChange { it.copy(aspectRatio = choice) } }
                )
                SegmentedRow(
                    title = stringResource(R.string.ctl_timer),
                    options = CaptureTimer.entries,
                    selected = camera.timer,
                    label = { stringResource(it.labelRes) },
                    onSelect = { choice -> onCameraChange { it.copy(timer = choice) } }
                )
            }
            if (hasFlashUnit) {
                SegmentedRow(
                    title = stringResource(R.string.ctl_flash),
                    options = FlashMode.entries,
                    selected = camera.flashMode,
                    label = { stringResource(it.labelRes) },
                    onSelect = { choice -> onCameraChange { it.copy(flashMode = choice) } }
                )
            }
            ExposureSlider(exposure, onExposure)
            if (zoom != null && zoom.max > zoom.min) {
                var value by remember(zoom.current) { mutableFloatStateOf(zoom.current) }
                SliderRow(
                    title = stringResource(R.string.ctl_zoom),
                    value = value,
                    valueRange = zoom.min..zoom.max,
                    valueLabel = ZoomPresets.label(value),
                    onValueChange = {
                        value = it
                        onZoom(it)
                    }
                )
            }
            SwitchRow(
                title = stringResource(R.string.ctl_grid),
                checked = camera.gridEnabled,
                onCheckedChange = { checked -> onCameraChange { it.copy(gridEnabled = checked) } },
                icon = Icons.Outlined.AutoAwesomeMosaic
            )
            SwitchRow(
                title = stringResource(R.string.ctl_focus),
                summary = stringResource(R.string.ctl_focus_summary),
                checked = camera.tapToFocus,
                onCheckedChange = { checked -> onCameraChange { it.copy(tapToFocus = checked) } },
                icon = Icons.Outlined.CenterFocusStrong
            )
            Spacer(Modifier.height(Dimens.SpaceS))
            SwitchRow(
                title = stringResource(R.string.ctl_stamp_enabled),
                checked = settings.stamp.enabled,
                onCheckedChange = { checked -> onSettingsChange { it.copy(stamp = it.stamp.copy(enabled = checked)) } },
                icon = Icons.Outlined.Layers
            )
            Text(
                stringResource(R.string.ctl_stamp_template),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceXs)
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Dimens.SpaceL),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
            ) {
                StampTemplate.entries.forEach { template ->
                    FilterChip(
                        selected = settings.stamp.template == template,
                        onClick = { onSettingsChange { it.copy(stamp = it.stamp.copy(template = template)) } },
                        label = { Text(stringResource(template.labelRes)) }
                    )
                }
            }
            SwitchRow(
                title = stringResource(R.string.ctl_live_stamp),
                summary = stringResource(R.string.ctl_live_stamp_summary),
                checked = camera.liveStampPreview,
                onCheckedChange = { checked -> onCameraChange { it.copy(liveStampPreview = checked) } },
                icon = Icons.Outlined.Visibility
            )
            ClickRow(title = stringResource(R.string.ctl_customize_stamp), onClick = onCustomizeStamp)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ExposureSlider(exposure: ExposureUi?, onExposure: (Int) -> Unit) {
    if (exposure == null || exposure.max <= exposure.min) {
        SliderRow(
            title = stringResource(R.string.ctl_exposure),
            value = 0f,
            valueRange = -1f..1f,
            valueLabel = stringResource(R.string.ctl_exposure_unsupported),
            onValueChange = {},
            enabled = false
        )
        return
    }
    var index by remember(exposure.index) { mutableFloatStateOf(exposure.index.toFloat()) }
    val ev = index.roundToInt() * exposure.stepEv
    SliderRow(
        title = stringResource(R.string.ctl_exposure),
        value = index,
        valueRange = exposure.min.toFloat()..exposure.max.toFloat(),
        steps = (exposure.max - exposure.min - 1).coerceAtLeast(0),
        valueLabel = String.format(Locale.US, "%+.1f EV", ev),
        onValueChange = {
            index = it
            onExposure(it.roundToInt())
        }
    )
}
