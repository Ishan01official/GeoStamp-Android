package com.geostamp.camera.stamps.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geostamp.camera.R
import com.geostamp.camera.settings.AppSettings
import com.geostamp.camera.settings.SettingsViewModel
import com.geostamp.camera.stamps.CoordinateFormat
import com.geostamp.camera.stamps.StampDateFormat
import com.geostamp.camera.stamps.StampFields
import com.geostamp.camera.stamps.StampPosition
import com.geostamp.camera.stamps.StampPreferences
import com.geostamp.camera.stamps.StampTemplate
import com.geostamp.camera.stamps.StampTextColor
import com.geostamp.camera.ui.components.ChoiceRow
import com.geostamp.camera.ui.components.SegmentedRow
import com.geostamp.camera.ui.components.SettingsSection
import com.geostamp.camera.ui.components.SliderRow
import com.geostamp.camera.ui.components.SwitchRow
import com.geostamp.camera.ui.labelRes
import com.geostamp.camera.ui.theme.Dimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StampSettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings = viewModel.settings.collectAsStateWithLifecycle().value ?: return
    val preview by viewModel.stampPreview.collectAsStateWithLifecycle()
    val stamp = settings.stamp
    fun updateStamp(transform: (StampPreferences) -> StampPreferences) = viewModel.update { it.copy(stamp = transform(it.stamp)) }
    fun updateFields(transform: (StampFields) -> StampFields) = updateStamp { it.withFields(transform(it.fields)) }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.importLogo(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stamp_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .widthIn(max = 360.dp)
                            .fillMaxWidth()
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(Dimens.CornerMedium))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        preview?.let {
                            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Spacer(Modifier.height(Dimens.SpaceS))
                    Text(
                        stringResource(R.string.stamp_preview_sample_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                SettingsSection(stringResource(R.string.section_template)) {
                    FlowRow(
                        Modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceM, vertical = Dimens.SpaceS),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
                    ) {
                        StampTemplate.entries.forEach { template ->
                            FilterChip(
                                selected = stamp.template == template,
                                onClick = { updateStamp { it.copy(template = template) } },
                                label = { Text(stringResource(template.labelRes)) }
                            )
                        }
                    }
                    SwitchRow(
                        title = stringResource(R.string.ctl_stamp_enabled),
                        checked = stamp.enabled,
                        onCheckedChange = { v -> updateStamp { it.copy(enabled = v) } }
                    )
                }
            }
            item { FieldsSection(settings, ::updateFields) }
            item {
                SettingsSection(stringResource(R.string.section_style)) {
                    SegmentedRow(
                        title = stringResource(R.string.position),
                        options = StampPosition.entries,
                        selected = stamp.position,
                        label = { stringResource(it.labelRes) },
                        onSelect = { v -> updateStamp { it.copy(position = v) } }
                    )
                    var fontScale by remember(stamp.fontScale) { mutableFloatStateOf(stamp.fontScale) }
                    SliderRow(
                        title = stringResource(R.string.font_size),
                        value = fontScale,
                        valueRange = StampPreferences.MIN_FONT_SCALE..StampPreferences.MAX_FONT_SCALE,
                        valueLabel = stringResource(R.string.percent_value, (fontScale * 100).roundToInt()),
                        onValueChange = { fontScale = it },
                        onValueChangeFinished = { updateStamp { it.copy(fontScale = fontScale) } }
                    )
                    var opacity by remember(stamp.backgroundOpacity) { mutableFloatStateOf(stamp.backgroundOpacity) }
                    SliderRow(
                        title = stringResource(R.string.background_opacity),
                        value = opacity,
                        valueRange = 0f..1f,
                        valueLabel = stringResource(R.string.percent_value, (opacity * 100).roundToInt()),
                        onValueChange = { opacity = it },
                        onValueChangeFinished = { updateStamp { it.copy(backgroundOpacity = opacity) } }
                    )
                    ColorPicker(stamp.textColor) { v -> updateStamp { it.copy(textColor = v) } }
                }
            }
            item {
                SettingsSection(stringResource(R.string.section_text)) {
                    var text by remember(stamp.customText) { mutableStateOf(stamp.customText) }
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it.take(MAX_CUSTOM_TEXT)
                            updateStamp { prefs -> prefs.copy(customText = text) }
                        },
                        label = { Text(stringResource(R.string.custom_text_label)) },
                        placeholder = { Text(stringResource(R.string.custom_text_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS)
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(if (stamp.logoPath != null) R.string.logo_selected else R.string.logo_none),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (stamp.logoPath != null) {
                            TextButton(onClick = viewModel::removeLogo) { Text(stringResource(R.string.logo_remove)) }
                        }
                        OutlinedButton(onClick = {
                            logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) { Text(stringResource(R.string.logo_choose)) }
                    }
                }
            }
            item {
                SettingsSection(stringResource(R.string.section_format)) {
                    val now = remember { Date() }
                    ChoiceRow(
                        title = stringResource(R.string.date_format),
                        options = StampDateFormat.entries,
                        selected = stamp.dateFormat,
                        label = { SimpleDateFormat(it.pattern, Locale.getDefault()).format(now) },
                        onSelect = { v -> updateStamp { it.copy(dateFormat = v) } }
                    )
                    SegmentedRow(
                        title = stringResource(R.string.coordinate_format),
                        options = CoordinateFormat.entries,
                        selected = stamp.coordinateFormat,
                        label = { stringResource(it.labelRes) },
                        onSelect = { v -> updateStamp { it.copy(coordinateFormat = v) } }
                    )
                }
            }
            item { Spacer(Modifier.navigationBarsPadding().height(Dimens.SpaceL)) }
        }
    }
}

@Composable
private fun FieldsSection(settings: AppSettings, update: ((StampFields) -> StampFields) -> Unit) {
    val fields = settings.stamp.fields
    val services = settings.services
    SettingsSection(stringResource(R.string.section_fields)) {
        FieldSwitch(R.string.field_date_time, fields.dateTime) { v -> update { it.copy(dateTime = v) } }
        FieldSwitch(
            R.string.field_address, fields.address,
            hint = R.string.field_needs_address.takeIf { !services.addressLookup }
        ) { v -> update { it.copy(address = v) } }
        FieldSwitch(R.string.field_coordinates, fields.coordinates) { v -> update { it.copy(coordinates = v) } }
        FieldSwitch(R.string.field_accuracy, fields.accuracy) { v -> update { it.copy(accuracy = v) } }
        FieldSwitch(R.string.field_heading, fields.heading) { v -> update { it.copy(heading = v) } }
        FieldSwitch(R.string.field_altitude, fields.altitude) { v -> update { it.copy(altitude = v) } }
        FieldSwitch(R.string.field_speed, fields.speed) { v -> update { it.copy(speed = v) } }
        FieldSwitch(
            R.string.field_map, fields.map,
            hint = R.string.field_needs_map.takeIf { !services.mapTiles }
        ) { v -> update { it.copy(map = v) } }
        FieldSwitch(
            R.string.field_weather, fields.weather,
            hint = R.string.field_needs_weather.takeIf { !services.weather }
        ) { v -> update { it.copy(weather = v) } }
        FieldSwitch(R.string.field_qr_code, fields.qrCode) { v -> update { it.copy(qrCode = v) } }
        FieldSwitch(R.string.field_custom_text, fields.customText) { v -> update { it.copy(customText = v) } }
        FieldSwitch(R.string.field_logo, fields.logo) { v -> update { it.copy(logo = v) } }
    }
}

@Composable
private fun FieldSwitch(title: Int, checked: Boolean, hint: Int? = null, onChange: (Boolean) -> Unit) {
    SwitchRow(
        title = stringResource(title),
        summary = hint?.takeIf { checked }?.let { stringResource(it) },
        checked = checked,
        onCheckedChange = onChange
    )
}

@Composable
private fun ColorPicker(selected: StampTextColor, onSelect: (StampTextColor) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS)) {
        Text(stringResource(R.string.text_color), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(Dimens.SpaceS))
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
            StampTextColor.entries.forEach { color ->
                val isSelected = color == selected
                val description = stringResource(color.labelRes)
                Box(
                    Modifier
                        .size(Dimens.TouchTarget)
                        .clip(CircleShape)
                        .clickable(role = Role.RadioButton) { onSelect(color) }
                        .semantics {
                            contentDescription = description
                            this.selected = isSelected
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(color.argb))
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}

private const val MAX_CUSTOM_TEXT = 80
