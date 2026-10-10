package com.geostamp.camera.qr

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.geostamp.camera.R
import com.geostamp.camera.maps.LocationUriBuilder
import com.geostamp.camera.maps.MapLauncher
import com.geostamp.camera.settings.MapLinkProvider
import com.geostamp.camera.ui.theme.Dimens
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shows a QR code for a coordinate so someone else can scan it and open the exact spot. Built on the device;
 * the location is only sent anywhere if the user taps Open in Maps or Share.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationQrSheet(
    latitude: Double,
    longitude: Double,
    address: String?,
    mapLinkProvider: MapLinkProvider,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var qr by remember(latitude, longitude) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(latitude, longitude) {
        qr = withContext(Dispatchers.Default) {
            QrLocationEncoder.encode(latitude, longitude)?.let { QrDrawing.bitmap(it, QR_BITMAP_PX) }
        }
    }
    val noMapApp = stringResource(R.string.qr_no_map_app)
    val shareFailed = stringResource(R.string.qr_share_failed)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = Dimens.SpaceL).padding(bottom = Dimens.SpaceL),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.qr_sheet_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Dimens.SpaceXs))
            Text(
                stringResource(R.string.qr_sheet_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Dimens.SpaceL))
            // Always black on white, whatever the theme, so scanners get full contrast.
            Box(
                Modifier
                    .widthIn(max = 280.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(Dimens.CornerMedium))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                qr?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_location_qr),
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.None,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    )
                }
            }
            Spacer(Modifier.height(Dimens.SpaceL))
            SelectionContainer {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        String.format(Locale.US, "%.6f, %.6f", latitude, longitude),
                        style = MaterialTheme.typography.titleMedium
                    )
                    address?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(Dimens.SpaceXs))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Spacer(Modifier.height(Dimens.SpaceL))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
                OutlinedButton(
                    onClick = {
                        if (!MapLauncher.open(context, latitude, longitude, mapLinkProvider)) {
                            Toast.makeText(context, noMapApp, Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.weight(1f).heightIn(min = Dimens.TouchTarget)
                ) {
                    Icon(Icons.Outlined.Map, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.qr_open_in_maps), maxLines = 2, textAlign = TextAlign.Center)
                }
                Button(
                    onClick = {
                        val bitmap = qr ?: return@Button
                        scope.launch {
                            val shared = shareQr(context, bitmap, latitude, longitude)
                            if (!shared) Toast.makeText(context, shareFailed, Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = qr != null,
                    modifier = Modifier.weight(1f).heightIn(min = Dimens.TouchTarget)
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.qr_share), maxLines = 2, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Writes the QR to the shareable cache folder and opens the share sheet with the image and the plain link. */
private suspend fun shareQr(context: Context, bitmap: Bitmap, latitude: Double, longitude: Double): Boolean {
    val uri = withContext(Dispatchers.IO) {
        runCatching {
            val folder = File(context.cacheDir, "qr").apply { mkdirs() }
            folder.listFiles()?.forEach { it.delete() }
            val file = File(folder, "geostamp-location-qr.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }.getOrNull()
    } ?: return false
    val intent = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TEXT, LocationUriBuilder.googleMaps(latitude, longitude))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return runCatching { context.startActivity(Intent.createChooser(intent, null)) }.isSuccess
}

private const val QR_BITMAP_PX = 720
