package com.geostamp.camera.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.geostamp.camera.R
import com.geostamp.camera.maps.MapType
import com.geostamp.camera.ui.labelRes
import com.geostamp.camera.ui.theme.Dimens

/**
 * Lets the user pick how map thumbnails look. Previews are drawn locally, so opening the sheet downloads
 * nothing; real tiles are only fetched for stamps, and only when map thumbnails are on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapTypeSheet(selected: MapType, onSelect: (MapType) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = Dimens.SpaceL).padding(bottom = Dimens.SpaceL)) {
            Text(stringResource(R.string.setting_map_type), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Dimens.SpaceXs))
            Text(
                stringResource(R.string.map_type_sheet_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Dimens.SpaceL))
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
                MapType.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
                        row.forEach { type ->
                            MapTypeOption(
                                type = type,
                                selected = type == selected,
                                onClick = {
                                    onSelect(type)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapTypeOption(type: MapType, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(Dimens.CornerMedium)
    Surface(
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .heightIn(min = Dimens.TouchTarget)
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    ) {
        Column(Modifier.padding(Dimens.SpaceS)) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.4f)
                    .clip(RoundedCornerShape(Dimens.CornerMedium / 2))
            ) { drawPreview(type) }
            Spacer(Modifier.height(Dimens.SpaceS))
            Text(
                stringResource(type.labelRes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Schematic illustrations of each style; they are not maps of any real place. */
private fun DrawScope.drawPreview(type: MapType) {
    when (type) {
        MapType.NORMAL -> {
            drawRect(Color(0xFFEFEAE2))
            drawRect(Color(0xFFCDE8C4), topLeft = Offset(size.width * 0.06f, size.height * 0.08f), size = Size(size.width * 0.3f, size.height * 0.34f))
            drawWater(Color(0xFFAAD3DF))
            drawRoads(Color.White, Color(0xFFF6CF65))
        }
        MapType.SATELLITE -> drawSatellite()
        MapType.TERRAIN -> {
            drawRect(Color(0xFFE3EBCF))
            val contour = Stroke(width = size.minDimension * 0.012f)
            for (i in 1..5) {
                val r = size.minDimension * 0.12f * i
                drawOval(Color(0xFFB59B74), topLeft = Offset(size.width * 0.38f - r, size.height * 0.45f - r * 0.7f), size = Size(r * 2, r * 1.4f), style = contour)
            }
            drawWater(Color(0xFF9CC6DA))
            drawRoads(Color(0xFFFFFFFF), Color(0xFFE6A15A))
        }
        MapType.HYBRID -> {
            drawSatellite()
            drawRoads(Color(0xCCFFFFFF), Color(0xE6F6CF65))
        }
    }
    drawCircle(Color.White, radius = size.minDimension * 0.07f, center = center)
    drawCircle(Color(0xFF1A73E8), radius = size.minDimension * 0.05f, center = center)
}

private fun DrawScope.drawSatellite() {
    drawRect(Color(0xFF4F6B3A))
    drawRect(Color(0xFF6E7F45), topLeft = Offset(0f, 0f), size = Size(size.width * 0.45f, size.height * 0.5f))
    drawRect(Color(0xFF8A7A55), topLeft = Offset(size.width * 0.55f, size.height * 0.55f), size = Size(size.width * 0.45f, size.height * 0.45f))
    drawRect(Color(0xFF3C5530), topLeft = Offset(size.width * 0.5f, 0f), size = Size(size.width * 0.5f, size.height * 0.4f))
    drawWater(Color(0xFF2F4F63))
}

private fun DrawScope.drawWater(color: Color) {
    val path = Path().apply {
        moveTo(size.width * 0.7f, size.height)
        quadraticTo(size.width * 0.78f, size.height * 0.62f, size.width, size.height * 0.58f)
        lineTo(size.width, size.height)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawRoads(minor: Color, major: Color) {
    val minorWidth = size.minDimension * 0.035f
    drawLine(minor, Offset(0f, size.height * 0.68f), Offset(size.width, size.height * 0.3f), strokeWidth = minorWidth)
    drawLine(minor, Offset(size.width * 0.22f, 0f), Offset(size.width * 0.32f, size.height), strokeWidth = minorWidth)
    drawLine(major, Offset(size.width * 0.62f, 0f), Offset(size.width * 0.5f, size.height), strokeWidth = minorWidth * 1.6f)
}
