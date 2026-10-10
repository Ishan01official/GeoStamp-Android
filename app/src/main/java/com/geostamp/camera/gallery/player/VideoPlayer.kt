package com.geostamp.camera.gallery.player

import android.content.Context
import android.net.Uri
import android.view.accessibility.AccessibilityManager
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.geostamp.camera.R
import com.geostamp.camera.ui.theme.CameraColors
import com.geostamp.camera.ui.theme.Dimens
import kotlinx.coroutines.delay

/**
 * In-app playback of GeoStamp's own MP4s with Media3 ExoPlayer. The stamp is visible because it is
 * part of the encoded frames; the player draws nothing over the picture except its own controls.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    uri: Uri,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
        }
    }
    var isPlaying by remember(uri) { mutableStateOf(false) }
    var ended by remember(uri) { mutableStateOf(false) }
    var duration by remember(uri) { mutableLongStateOf(0L) }
    var position by remember(uri) { mutableLongStateOf(0L) }
    var muted by remember(uri) { mutableStateOf(false) }
    var controlsVisible by remember(uri) { mutableStateOf(true) }
    var scrubbing by remember(uri) { mutableStateOf<Float?>(null) }
    val keepControls = remember { context.touchExplorationEnabled() }

    DisposableEffect(player, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (player.duration != C.TIME_UNSET) duration = player.duration
                ended = state == Player.STATE_ENDED
                if (ended) controlsVisible = true
            }
        }
        player.addListener(listener)
        // Never play in the background; resume is left to the user.
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, isPlaying) {
        while (true) {
            position = player.currentPosition
            delay(if (isPlaying) POSITION_POLL_MILLIS else IDLE_POLL_MILLIS)
        }
    }
    LaunchedEffect(controlsVisible, isPlaying, keepControls) {
        if (controlsVisible && isPlaying && !keepControls) {
            delay(CONTROLS_HIDE_MILLIS)
            controlsVisible = false
        }
    }

    fun togglePlay() {
        when {
            ended -> {
                player.seekTo(0)
                player.play()
            }
            player.isPlaying -> player.pause()
            else -> player.play()
        }
    }

    Box(modifier.background(Color.Black)) {
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { it.player = player },
            onRelease = { it.player = null },
            modifier = Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    controlsVisible = !controlsVisible
                }
        )
        if (controlsVisible) {
            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXl),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoundButton(Icons.Outlined.Replay10, stringResource(R.string.player_back_10), 56.dp) {
                    player.seekTo((player.currentPosition - SEEK_STEP_MILLIS).coerceAtLeast(0))
                }
                RoundButton(
                    if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    stringResource(if (isPlaying) R.string.player_pause else R.string.action_play),
                    80.dp,
                    ::togglePlay
                )
                RoundButton(Icons.Outlined.Forward10, stringResource(R.string.player_forward_10), 56.dp) {
                    player.seekTo((player.currentPosition + SEEK_STEP_MILLIS).coerceAtMost(duration.coerceAtLeast(0)))
                }
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(CameraColors.ScrimStrong)
                    .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS)
            ) {
                val shown = scrubbing?.toLong() ?: position
                val positionLabel = stringResource(R.string.player_position, formatTime(shown), formatTime(duration))
                Slider(
                    value = shown.coerceIn(0, duration.coerceAtLeast(1)).toFloat(),
                    valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                    onValueChange = { scrubbing = it },
                    onValueChangeFinished = {
                        scrubbing?.let { player.seekTo(it.toLong()) }
                        scrubbing = null
                    },
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White),
                    modifier = Modifier.semantics {
                        contentDescription = positionLabel
                        stateDescription = positionLabel
                    }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(positionLabel, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        muted = !muted
                        player.volume = if (muted) 0f else 1f
                    }, modifier = Modifier.size(Dimens.TouchTarget)) {
                        Icon(
                            if (muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                            contentDescription = stringResource(if (muted) R.string.player_unmute else R.string.player_mute),
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onToggleFullscreen, modifier = Modifier.size(Dimens.TouchTarget)) {
                        Icon(
                            if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                            contentDescription = stringResource(if (fullscreen) R.string.player_exit_fullscreen else R.string.player_fullscreen),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size).clip(CircleShape).background(CameraColors.ScrimStrong)
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

private fun Context.touchExplorationEnabled(): Boolean =
    (getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)?.isTouchExplorationEnabled == true

fun formatTime(millis: Long): String {
    val total = (millis.coerceAtLeast(0) / 1000)
    return "%d:%02d".format(total / 60, total % 60)
}

private const val SEEK_STEP_MILLIS = 10_000L
private const val POSITION_POLL_MILLIS = 250L
private const val IDLE_POLL_MILLIS = 1_000L
private const val CONTROLS_HIDE_MILLIS = 4_000L
