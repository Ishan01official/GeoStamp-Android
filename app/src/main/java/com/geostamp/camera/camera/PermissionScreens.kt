package com.geostamp.camera.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.geostamp.camera.R
import com.geostamp.camera.permissions.LocationAccess
import com.geostamp.camera.permissions.PermissionAction
import com.geostamp.camera.ui.theme.CameraColors
import com.geostamp.camera.ui.theme.Dimens

/** First screen on a fresh install: explains that GeoStamp is a camera before Android asks for access. */
@Composable
fun CameraIntroScreen(action: PermissionAction, onRequest: () -> Unit, onOpenAppSettings: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(CameraColors.Background).padding(Dimens.SpaceXl),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 440.dp).verticalScroll(rememberScrollState())
        ) {
            Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = CameraColors.Content, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(Dimens.SpaceL))
            Text(
                stringResource(R.string.intro_title),
                color = CameraColors.Content,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Dimens.SpaceM))
            Text(
                stringResource(R.string.intro_body),
                color = CameraColors.ContentMuted,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Dimens.SpaceXl))
            if (action == PermissionAction.OPEN_SETTINGS) {
                Text(
                    stringResource(R.string.intro_camera_blocked),
                    color = CameraColors.Warning,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(Dimens.SpaceL))
                Button(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.permission_open_settings))
                }
            } else {
                Button(onClick = onRequest, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.permission_grant))
                }
            }
        }
    }
}

/** Shown once after the camera starts. Location is optional; "Not now" always works. */
@Composable
fun LocationOfferDialog(onAllow: () -> Unit, onNotNow: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNotNow,
        icon = { Icon(Icons.Outlined.LocationOn, contentDescription = null) },
        title = { Text(stringResource(R.string.location_offer_title)) },
        text = { Text(stringResource(R.string.location_offer_body)) },
        confirmButton = { Button(onClick = onAllow) { Text(stringResource(R.string.location_allow)) } },
        dismissButton = { TextButton(onClick = onNotNow) { Text(stringResource(R.string.not_now)) } }
    )
}

/** Opened from the location chip. Always offers a way forward, never a dead end. */
@Composable
fun LocationHelpDialog(
    access: LocationAccess,
    action: PermissionAction,
    providersOff: Boolean,
    onRequest: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val (title, body) = when {
        access == LocationAccess.DENIED && action == PermissionAction.OPEN_SETTINGS ->
            R.string.location_help_denied_title to R.string.location_help_blocked_body
        access == LocationAccess.DENIED -> R.string.location_help_denied_title to R.string.location_help_denied_body
        providersOff -> R.string.location_help_off_title to R.string.location_help_off_body
        access == LocationAccess.APPROXIMATE -> R.string.location_help_approx_title to R.string.location_help_approx_body
        else -> R.string.location_help_searching_title to R.string.location_help_searching_body
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.LocationOn, contentDescription = null) },
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(body)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs)) {
                when {
                    access != LocationAccess.PRECISE && action == PermissionAction.REQUEST ->
                        Button(onClick = onRequest) {
                            Text(stringResource(if (access == LocationAccess.APPROXIMATE) R.string.location_use_precise else R.string.location_allow))
                        }
                    access != LocationAccess.PRECISE && action == PermissionAction.OPEN_SETTINGS ->
                        Button(onClick = onOpenAppSettings) { Text(stringResource(R.string.permission_open_settings)) }
                    providersOff -> Button(onClick = onOpenLocationSettings) { Text(stringResource(R.string.location_turn_on)) }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}

/** Explains why Dual Capture is unavailable. GeoStamp never fakes a second camera. */
@Composable
fun DualUnsupportedDialog(reason: DualUnsupportedReason?, onDismiss: () -> Unit) {
    val detail = when (reason) {
        DualUnsupportedReason.ANDROID_TOO_OLD -> R.string.dual_reason_android
        DualUnsupportedReason.NO_FRONT_OR_REAR_CAMERA -> R.string.dual_reason_cameras
        DualUnsupportedReason.NO_CONCURRENT_FEATURE, DualUnsupportedReason.NO_FRONT_REAR_PAIR, null -> R.string.dual_reason_hardware
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
        title = { Text(stringResource(R.string.dual_unsupported_title)) },
        text = { Text(stringResource(R.string.dual_unsupported_body, stringResource(detail))) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } }
    )
}
