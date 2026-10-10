package com.geostamp.camera.camera

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.geostamp.camera.R
import com.geostamp.camera.address.AddressEditScope
import com.geostamp.camera.address.AddressOverride
import com.geostamp.camera.ui.theme.CameraColors
import com.geostamp.camera.ui.theme.CameraLabel
import com.geostamp.camera.ui.theme.Dimens

/**
 * Shows the address that will be stamped and opens the editor. The whole bar is the touch target; the edit
 * icon sits at its upper-right corner, beside the text rather than over it, so long two-line addresses stay
 * readable and the affordance stays visible.
 */
@Composable
fun AddressBar(detected: String?, override: AddressOverride?, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val text = override?.text ?: detected ?: stringResource(R.string.address_none_detected)
    val description = stringResource(R.string.cd_edit_address, text)
    Row(
        modifier
            .widthIn(max = 520.dp)
            .heightIn(min = Dimens.TouchTarget)
            .clip(RoundedCornerShape(Dimens.CornerLarge))
            .background(CameraColors.Scrim)
            .clickable(role = Role.Button, onClick = onEdit)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(start = Dimens.SpaceM, end = Dimens.SpaceS, top = Dimens.SpaceS, bottom = Dimens.SpaceS),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
    ) {
        Icon(
            Icons.Outlined.Place,
            contentDescription = null,
            tint = CameraColors.Content,
            modifier = Modifier.padding(top = 2.dp).size(Dimens.IconSmall)
        )
        Text(
            text,
            style = CameraLabel,
            color = if (override == null && detected == null) CameraColors.ContentMuted else CameraColors.Content,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).align(Alignment.CenterVertically)
        )
        if (override != null) {
            Text(
                stringResource(R.string.address_edited_badge),
                style = CameraLabel,
                color = CameraColors.Warning,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
        Box(
            Modifier
                .size(Dimens.IconSmall + Dimens.SpaceS)
                .clip(CircleShape)
                .background(CameraColors.Scrim),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Edit, contentDescription = null, tint = CameraColors.Content, modifier = Modifier.size(Dimens.IconSmall - 4.dp))
        }
    }
}

@Composable
fun AddressEditDialog(
    detected: String?,
    override: AddressOverride?,
    onSave: (String, AddressEditScope) -> Unit,
    onRestoreDetected: () -> Unit,
    onDismiss: () -> Unit,
    showScope: Boolean = true,
    note: String = stringResource(R.string.address_edit_note)
) {
    var text by rememberSaveable { mutableStateOf(override?.text ?: detected.orEmpty()) }
    var scope by rememberSaveable { mutableStateOf(override?.scope ?: AddressEditScope.NEXT_CAPTURE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.address_edit_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
                Column {
                    Text(stringResource(R.string.address_detected_label), style = MaterialTheme.typography.labelLarge)
                    Text(
                        detected ?: stringResource(R.string.address_none_detected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(MAX_ADDRESS_LENGTH) },
                    label = { Text(stringResource(R.string.address_field_label)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.address_preview_label), style = MaterialTheme.typography.labelLarge)
                AddressPreview(text.trim())
                if (showScope) {
                    Column(Modifier.selectableGroup()) {
                        ScopeOption(stringResource(R.string.address_scope_next), scope == AddressEditScope.NEXT_CAPTURE) { scope = AddressEditScope.NEXT_CAPTURE }
                        ScopeOption(stringResource(R.string.address_scope_session), scope == AddressEditScope.SESSION) { scope = AddressEditScope.SESSION }
                    }
                }
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (override != null) {
                    TextButton(onClick = onRestoreDetected) { Text(stringResource(R.string.address_restore)) }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(text, scope) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** Mimics the stamp's address row so the user sees exactly what will be written. */
@Composable
private fun AddressPreview(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.CornerSmall))
            .background(Color(0xE6141416))
            .padding(Dimens.SpaceM),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
    ) {
        Icon(Icons.Outlined.Place, contentDescription = null, tint = Color.White, modifier = Modifier.size(Dimens.Icon))
        Text(
            text.ifEmpty { stringResource(R.string.address_preview_empty) },
            color = Color.White,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ScopeOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.TouchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Dimens.SpaceS))
    }
}

private const val MAX_ADDRESS_LENGTH = 160
