package com.geostamp.camera.gallery

import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geostamp.camera.R
import com.geostamp.camera.camera.AddressEditDialog
import com.geostamp.camera.location.LocationStamp
import com.geostamp.camera.settings.MapLinkProvider
import com.geostamp.camera.ui.components.InfoRow
import com.geostamp.camera.ui.theme.CameraColors
import com.geostamp.camera.ui.theme.Dimens
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    uri: Uri,
    viewModel: GalleryViewModel,
    mapLinkProvider: MapLinkProvider,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val item = state.items.firstOrNull { it.uri == uri }
    val configuration = LocalConfiguration.current
    val maxEdge = with(LocalDensity.current) { maxOf(configuration.screenWidthDp, configuration.screenHeightDp).dp.roundToPx() } * 2
    var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var metadata by remember(uri) { mutableStateOf<PhotoMetadataInfo?>(null) }
    LaunchedEffect(uri, item?.isVideo) {
        bitmap = if (item?.isVideo == true) viewModel.thumbnail(uri) else viewModel.displayBitmap(uri, maxEdge)
    }
    LaunchedEffect(uri) { metadata = viewModel.metadata(uri) }
    var showInfo by remember { mutableStateOf(false) }
    var editAddress by remember { mutableStateOf(false) }
    val editedMessage = stringResource(R.string.address_edit_saved)
    val editFailedMessage = stringResource(R.string.address_edit_failed)
    LaunchedEffect(state.message) {
        when (state.message) {
            is GalleryMessage.AddressEdited -> Toast.makeText(context, editedMessage, Toast.LENGTH_LONG).show()
            GalleryMessage.AddressEditFailed -> Toast.makeText(context, editFailedMessage, Toast.LENGTH_LONG).show()
            else -> return@LaunchedEffect
        }
        viewModel.consumeMessage()
    }
    var confirmDelete by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(Unit) { if (state.items.isEmpty()) viewModel.refresh() }

    Box(Modifier.fillMaxSize().background(CameraColors.Background)) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = item?.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            scale = if (scale > 1f) 1f else 2.5f
                            offset = Offset.Zero
                        })
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
        }
        if (item?.isVideo == true) {
            IconButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                },
                modifier = Modifier.align(Alignment.Center).size(88.dp)
            ) {
                Icon(Icons.Outlined.PlayCircle, contentDescription = stringResource(R.string.action_play), tint = Color.White, modifier = Modifier.size(72.dp))
            }
        }

        Row(
            Modifier.statusBarsPadding().fillMaxWidth().padding(Dimens.SpaceXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = Color.White)
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(CameraColors.Scrim)
                .navigationBarsPadding()
                .padding(vertical = Dimens.SpaceS),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly
        ) {
            ViewerAction(Icons.Outlined.Share, stringResource(R.string.action_share)) { item?.let { shareMedia(context, listOf(it)) } }
            val lat = metadata?.latitude
            val lon = metadata?.longitude
            if (lat != null && lon != null) {
                ViewerAction(Icons.Outlined.Map, stringResource(R.string.action_open_map)) {
                    val stamp = LocationStamp(lat, lon, Float.NaN, 0L, null, null)
                    val url = when (mapLinkProvider) {
                        MapLinkProvider.OPEN_STREET_MAP -> stamp.openStreetMapUrl()
                        MapLinkProvider.GOOGLE_MAPS -> stamp.googleMapsUrl()
                    }
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
            if (item?.isVideo == false) {
                ViewerAction(Icons.Outlined.EditLocationAlt, stringResource(R.string.address_edit_title)) { editAddress = true }
            }
            ViewerAction(Icons.Outlined.Info, stringResource(R.string.action_info)) { showInfo = true }
            ViewerAction(Icons.Outlined.Delete, stringResource(R.string.action_delete)) { confirmDelete = true }
        }
    }

    if (showInfo && item != null) {
        ModalBottomSheet(onDismissRequest = { showInfo = false }) {
            Column(Modifier.navigationBarsPadding().padding(bottom = Dimens.SpaceL)) {
                val notRecorded = stringResource(R.string.info_not_recorded)
                InfoRow(stringResource(R.string.info_name), item.displayName)
                InfoRow(
                    stringResource(R.string.info_date),
                    DateFormat.getMediumDateFormat(context).format(Date(item.takenAtMillis)) + " · " +
                        DateFormat.getTimeFormat(context).format(Date(item.takenAtMillis))
                )
                if (item.width > 0) InfoRow(stringResource(R.string.info_resolution), "${item.width} × ${item.height}")
                InfoRow(stringResource(R.string.info_size), Formatter.formatShortFileSize(context, item.sizeBytes))
                if (!item.isVideo) {
                    InfoRow(
                        stringResource(R.string.info_stamp),
                        stringResource(if (item.isStamped) R.string.info_stamped_yes else R.string.info_stamped_no)
                    )
                    InfoRow(
                        stringResource(R.string.info_location),
                        metadata?.latitude?.let { String.format(Locale.US, "%.6f, %.6f", it, metadata?.longitude) } ?: notRecorded
                    )
                    metadata?.addressEnteredManually?.let { manual ->
                        InfoRow(
                            stringResource(R.string.info_address_source),
                            stringResource(if (manual) R.string.info_address_manual else R.string.info_address_detected)
                        )
                    }
                    metadata?.altitudeMeters?.let {
                        InfoRow(stringResource(R.string.info_altitude), stringResource(R.string.info_meters, String.format(Locale.US, "%.0f", it)))
                    }
                    metadata?.cameraModel?.let { InfoRow(stringResource(R.string.info_device), it) }
                }
            }
        }
    }

    if (editAddress && item != null) {
        val source = item.unstampedSource(state.items)
        if (source == null) {
            AlertDialog(
                onDismissRequest = { editAddress = false },
                title = { Text(stringResource(R.string.address_edit_title)) },
                text = { Text(stringResource(R.string.address_edit_unavailable)) },
                confirmButton = { TextButton(onClick = { editAddress = false }) { Text(stringResource(R.string.close)) } }
            )
        } else {
            AddressEditDialog(
                detected = null,
                override = null,
                onSave = { text, _ ->
                    editAddress = false
                    viewModel.editAddress(source, text)
                },
                onRestoreDetected = {},
                onDismiss = { editAddress = false },
                showScope = false,
                note = stringResource(R.string.address_edit_existing_note)
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(pluralStringResource(R.plurals.delete_confirm_title, 1, 1)) },
            text = { Text(stringResource(R.string.delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(listOf(uri))
                    onBack()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun ViewerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(Dimens.TouchTarget)) {
        Icon(icon, contentDescription = label, tint = Color.White)
    }
}
