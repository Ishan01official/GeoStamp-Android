package com.geostamp.camera.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import com.geostamp.camera.ui.theme.CameraColors
import com.geostamp.camera.ui.theme.CameraLabel
import com.geostamp.camera.ui.theme.Dimens

/** Circular, translucent icon button for use over the viewfinder. Icon follows device rotation. */
@Composable
fun CameraIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rotation: Float = 0f,
    enabled: Boolean = true,
    active: Boolean = false,
    size: Dp = 44.dp
) {
    val animatedRotation by animateFloatAsState(rotation, label = "iconRotation")
    Box(
        modifier = modifier
            .size(Dimens.TouchTarget.coerceAtLeast(size))
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (active) CameraColors.Selected else CameraColors.Scrim),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when {
                    !enabled -> CameraColors.ContentMuted.copy(alpha = 0.4f)
                    active -> CameraColors.OnSelected
                    else -> CameraColors.Content
                },
                modifier = Modifier.size(Dimens.Icon).rotate(animatedRotation)
            )
        }
    }
}

/** Compact translucent pill showing a live reading such as GPS accuracy or heading. */
@Composable
fun CameraChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = CameraColors.Content,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null
) {
    Row(
        modifier = modifier
            .heightIn(min = Dimens.ChipHeight)
            .clip(RoundedCornerShape(50))
            .background(CameraColors.Scrim)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier)
            .padding(horizontal = Dimens.SpaceM, vertical = Dimens.SpaceXs + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(Dimens.IconSmall - 2.dp))
        }
        Text(text, style = CameraLabel, color = CameraColors.Content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

