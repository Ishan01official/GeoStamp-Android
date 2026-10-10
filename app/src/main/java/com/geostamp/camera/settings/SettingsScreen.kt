package com.geostamp.camera.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Grid3x3
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geostamp.camera.BuildConfig
import com.geostamp.camera.R
import com.geostamp.camera.stamps.TemperatureUnit
import com.geostamp.camera.ui.components.ChoiceRow
import com.geostamp.camera.ui.components.ClickRow
import com.geostamp.camera.ui.components.InfoRow
import com.geostamp.camera.ui.components.SettingsSection
import com.geostamp.camera.ui.components.SliderRow
import com.geostamp.camera.ui.components.SwitchRow
import com.geostamp.camera.ui.labelRes
import com.geostamp.camera.ui.theme.Dimens
import kotlin.math.roundToInt

private const val SOURCE_URL = "https://github.com/Ishan01official/GeoStamp-Android"
private const val PRIVACY_URL = "$SOURCE_URL/blob/main/PRIVACY.md"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit, onOpenStampSettings: () -> Unit, onOpenDiagnostics: () -> Unit) {
    val settings = viewModel.settings.collectAsStateWithLifecycle().value ?: return
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val update = viewModel::update

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            androidx.compose.material3.LargeTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                SettingsSection(stringResource(R.string.section_camera)) {
                    SwitchRow(
                        title = stringResource(R.string.ctl_grid),
                        checked = settings.camera.gridEnabled,
                        onCheckedChange = { v -> update { it.copy(camera = it.camera.copy(gridEnabled = v)) } },
                        icon = Icons.Outlined.Grid3x3
                    )
                    SwitchRow(
                        title = stringResource(R.string.ctl_focus),
                        summary = stringResource(R.string.ctl_focus_summary),
                        checked = settings.camera.tapToFocus,
                        onCheckedChange = { v -> update { it.copy(camera = it.camera.copy(tapToFocus = v)) } },
                        icon = Icons.Outlined.CenterFocusStrong
                    )
                    SwitchRow(
                        title = stringResource(R.string.ctl_simple_mode),
                        summary = stringResource(R.string.ctl_simple_mode_summary),
                        checked = settings.camera.simpleMode,
                        onCheckedChange = { v -> update { it.copy(camera = it.camera.copy(simpleMode = v)) } },
                        icon = Icons.Outlined.Visibility
                    )
                    SwitchRow(
                        title = stringResource(R.string.ctl_live_stamp),
                        summary = stringResource(R.string.ctl_live_stamp_summary),
                        checked = settings.camera.liveStampPreview,
                        onCheckedChange = { v -> update { it.copy(camera = it.camera.copy(liveStampPreview = v)) } },
                        icon = Icons.Outlined.Visibility
                    )
                }
            }
            item {
                SettingsSection(stringResource(R.string.section_location)) {
                    ChoiceRow(
                        title = stringResource(R.string.setting_max_accuracy),
                        options = LocationSettings.ACCURACY_CHOICES,
                        selected = settings.location.maxAccuracyMeters,
                        label = { stringResource(R.string.setting_max_accuracy_summary, it) },
                        onSelect = { v -> update { it.copy(location = it.location.copy(maxAccuracyMeters = v)) } },
                        icon = Icons.Outlined.GpsFixed
                    )
                    ChoiceRow(
                        title = stringResource(R.string.setting_max_age),
                        options = LocationSettings.AGE_CHOICES,
                        selected = settings.location.maxAgeSeconds,
                        label = { stringResource(R.string.setting_max_age_summary, it) },
                        onSelect = { v -> update { it.copy(location = it.location.copy(maxAgeSeconds = v)) } },
                        icon = Icons.Outlined.Schedule
                    )
                    ChoiceRow(
                        title = stringResource(R.string.setting_location_refresh),
                        options = LocationDisplayRefresh.entries,
                        selected = settings.location.displayRefresh,
                        label = { stringResource(it.labelRes) },
                        onSelect = { v -> update { it.copy(location = it.location.copy(displayRefresh = v)) } },
                        icon = Icons.Outlined.GpsFixed
                    )
                    if (viewModel.hasCompass) {
                        ChoiceRow(
                            title = stringResource(R.string.setting_compass_smoothing),
                            options = CompassSmoothing.entries,
                            selected = settings.location.compassSmoothing,
                            label = { stringResource(it.labelRes) },
                            onSelect = { v -> update { it.copy(location = it.location.copy(compassSmoothing = v)) } },
                            icon = Icons.Outlined.Explore
                        )
                    } else {
                        InfoRow(stringResource(R.string.setting_no_compass), stringResource(R.string.setting_no_compass_summary), Icons.Outlined.Explore)
                    }
                    SwitchRow(
                        title = stringResource(R.string.setting_address),
                        summary = stringResource(R.string.setting_address_summary),
                        checked = settings.services.addressLookup,
                        onCheckedChange = { v -> update { it.copy(services = it.services.copy(addressLookup = v)) } },
                        icon = Icons.Outlined.Place
                    )
                    ChoiceRow(
                        title = stringResource(R.string.setting_address_detail),
                        options = com.geostamp.camera.environment.AddressDetail.entries,
                        selected = settings.location.addressDetail,
                        label = { stringResource(when (it) {
                            com.geostamp.camera.environment.AddressDetail.DETAILED -> R.string.address_detail_detailed
                            com.geostamp.camera.environment.AddressDetail.STANDARD -> R.string.address_detail_standard
                            com.geostamp.camera.environment.AddressDetail.SHORT -> R.string.address_detail_short
                        }) },
                        onSelect = { v -> update { it.copy(location = it.location.copy(addressDetail = v)) } },
                        icon = Icons.Outlined.Place
                    )
                }
            }
            item {
                SettingsSection(stringResource(R.string.section_stamps)) {
                    ClickRow(
                        title = stringResource(R.string.setting_stamp_open),
                        summary = stringResource(R.string.setting_stamp_open_summary),
                        onClick = onOpenStampSettings,
                        icon = Icons.Outlined.Layers,
                        value = stringResource(settings.stamp.template.labelRes)
                    )
                    SwitchRow(
                        title = stringResource(R.string.ctl_stamp_enabled),
                        checked = settings.stamp.enabled,
                        onCheckedChange = { v -> update { it.copy(stamp = it.stamp.copy(enabled = v)) } },
                        icon = Icons.Outlined.Photo
                    )
                }
            }
            item {
                SettingsSection(stringResource(R.string.section_maps)) {
                    SwitchRow(
                        title = stringResource(R.string.setting_map_tiles),
                        summary = stringResource(R.string.setting_map_tiles_summary),
                        checked = settings.services.mapTiles,
                        onCheckedChange = { v -> update { it.copy(services = it.services.copy(mapTiles = v)) } },
                        icon = Icons.Outlined.Map
                    )
                    ChoiceRow(
                        title = stringResource(R.string.setting_map_link),
                        options = MapLinkProvider.entries,
                        selected = settings.services.mapLinkProvider,
                        label = { stringResource(it.labelRes) },
                        onSelect = { v -> update { it.copy(services = it.services.copy(mapLinkProvider = v)) } }
                    )
                    val cleared = stringResource(R.string.map_cache_cleared)
                    ClickRow(
                        title = stringResource(R.string.setting_clear_map_cache),
                        onClick = {
                            viewModel.clearMapCache()
                            Toast.makeText(context, cleared, Toast.LENGTH_SHORT).show()
                        },
                        icon = Icons.Outlined.DeleteSweep
                    )
                }
            }
            item {
                SettingsSection(stringResource(R.string.section_weather)) {
                    SwitchRow(
                        title = stringResource(R.string.setting_weather),
                        summary = stringResource(R.string.setting_weather_summary),
                        checked = settings.services.weather,
                        onCheckedChange = { v -> update { it.copy(services = it.services.copy(weather = v)) } },
                        icon = Icons.Outlined.Cloud
                    )
                    ChoiceRow(
                        title = stringResource(R.string.temperature_unit),
                        options = TemperatureUnit.entries,
                        selected = settings.stamp.temperatureUnit,
                        label = { stringResource(it.labelRes) },
                        onSelect = { v -> update { it.copy(stamp = it.stamp.copy(temperatureUnit = v)) } },
                        icon = Icons.Outlined.Thermostat
                    )
                }
            }
            item {
                SettingsSection(stringResource(R.string.section_storage)) {
                    SwitchRow(
                        title = stringResource(R.string.setting_save_original),
                        summary = stringResource(R.string.setting_save_original_summary),
                        checked = settings.storage.saveOriginal,
                        onCheckedChange = { v -> update { it.copy(storage = it.storage.copy(saveOriginal = v)) } },
                        icon = Icons.Outlined.Photo
                    )
                    var quality by remember(settings.storage.jpegQuality) { mutableFloatStateOf(settings.storage.jpegQuality.toFloat()) }
                    SliderRow(
                        title = stringResource(R.string.setting_jpeg_quality),
                        value = quality,
                        valueRange = StorageSettings.QUALITY_RANGE.first.toFloat()..StorageSettings.QUALITY_RANGE.last.toFloat(),
                        valueLabel = quality.roundToInt().toString(),
                        onValueChange = { quality = it },
                        onValueChangeFinished = { update { it.copy(storage = it.storage.copy(jpegQuality = quality.roundToInt())) } }
                    )
                    InfoRow(stringResource(R.string.setting_storage_location), stringResource(R.string.setting_storage_location_value), Icons.Outlined.Folder)
                }
            }
            item {
                SettingsSection(stringResource(R.string.section_appearance)) {
                    ChoiceRow(
                        title = stringResource(R.string.setting_theme),
                        options = ThemeMode.entries,
                        selected = settings.appearance.themeMode,
                        label = { stringResource(it.labelRes) },
                        onSelect = { v -> update { it.copy(appearance = it.appearance.copy(themeMode = v)) } },
                        icon = Icons.Outlined.DarkMode
                    )
                    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    SwitchRow(
                        title = stringResource(R.string.setting_dynamic_color),
                        summary = stringResource(if (dynamicSupported) R.string.setting_dynamic_color_summary else R.string.setting_dynamic_color_unavailable),
                        checked = settings.appearance.dynamicColor && dynamicSupported,
                        enabled = dynamicSupported,
                        onCheckedChange = { v -> update { it.copy(appearance = it.appearance.copy(dynamicColor = v)) } },
                        icon = Icons.Outlined.ColorLens
                    )
                }
            }
            item {
                SettingsSection(stringResource(R.string.section_privacy)) {
                    Text(
                        stringResource(R.string.privacy_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS)
                    )
                    SwitchRow(
                        title = stringResource(R.string.setting_exif_location),
                        summary = stringResource(R.string.setting_exif_location_summary),
                        checked = settings.stamp.writeExifLocation,
                        onCheckedChange = { v -> update { it.copy(stamp = it.stamp.copy(writeExifLocation = v)) } },
                        icon = Icons.Outlined.Policy
                    )
                    ClickRow(
                        title = stringResource(R.string.setting_permissions),
                        summary = stringResource(R.string.setting_permissions_summary),
                        icon = Icons.Outlined.AdminPanelSettings,
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            )
                        }
                    )
                }
            }
            item {
                SettingsSection(stringResource(R.string.section_about)) {
                    InfoRow(stringResource(R.string.about_version), BuildConfig.VERSION_NAME, Icons.Outlined.Info)
                    ClickRow(
                        title = stringResource(R.string.diagnostics_title),
                        summary = stringResource(R.string.diagnostics_summary),
                        icon = Icons.Outlined.Memory,
                        onClick = onOpenDiagnostics
                    )
                    ClickRow(
                        title = stringResource(R.string.about_source),
                        summary = SOURCE_URL.removePrefix("https://"),
                        icon = Icons.Outlined.Code,
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL))) }
                    )
                    ClickRow(
                        title = stringResource(R.string.about_privacy),
                        icon = Icons.Outlined.Policy,
                        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))) }
                    )
                }
            }
            item { Spacer(Modifier.navigationBarsPadding().height(Dimens.SpaceL)) }
        }
    }
}
