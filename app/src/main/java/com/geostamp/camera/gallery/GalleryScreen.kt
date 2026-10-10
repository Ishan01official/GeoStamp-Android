package com.geostamp.camera.gallery

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geostamp.camera.R
import com.geostamp.camera.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    onBack: () -> Unit,
    onOpenItem: (MediaItem) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf<List<Uri>?>(null) }
    var pendingConsent by remember { mutableStateOf<List<Uri>>(emptyList()) }

    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.onDeleteConsentResult(pendingConsent)
    }

    val batchDone = state.message
    val doneText = (batchDone as? GalleryMessage.BatchFinished)?.result?.let {
        if (it.failed == 0) pluralStringResource(R.plurals.batch_done, it.stamped, it.stamped)
        else stringResource(R.string.batch_partial, it.stamped, it.failed)
    }
    LaunchedEffect(batchDone) {
        when (batchDone) {
            is GalleryMessage.BatchFinished -> {
                viewModel.consumeMessage()
                doneText?.let { snackbar.showSnackbar(it) }
            }
            is GalleryMessage.NeedsDeleteConsent -> {
                viewModel.consumeMessage()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    pendingConsent = batchDone.uris
                    val request = MediaStore.createDeleteRequest(context.contentResolver, batchDone.uris)
                    consentLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                }
            }
            // Address edits are reported by the viewer that started them.
            is GalleryMessage.AddressEdited, GalleryMessage.AddressEditFailed, null -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (state.selectionMode) pluralStringResource(R.plurals.selected_count, state.selection.size, state.selection.size)
                        else stringResource(R.string.gallery_title)
                    )
                },
                navigationIcon = {
                    if (state.selectionMode) {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.cd_close))
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    }
                },
                actions = {
                    if (state.selectionMode) {
                        val selectedItems = state.items.filter { it.uri in state.selection }
                        if (selectedItems.any { !it.isVideo }) {
                            IconButton(onClick = viewModel::batchStamp) {
                                Icon(Icons.Outlined.Layers, contentDescription = stringResource(R.string.action_stamp))
                            }
                        }
                        IconButton(onClick = { shareMedia(context, selectedItems) }) {
                            Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.action_share))
                        }
                        IconButton(onClick = { confirmDelete = state.selection.toList() }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            state.batchProgress?.let { (done, total) ->
                Column(Modifier.padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS)) {
                    Text(stringResource(R.string.batch_progress, done, total), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.batch_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Dimens.SpaceXs))
                    LinearProgressIndicator(progress = { done / total.toFloat() }, modifier = Modifier.fillMaxWidth())
                }
            }
            FilterRow(state, viewModel)
            when {
                state.loading -> Unit
                state.visibleItems.isEmpty() -> EmptyGallery()
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 112.dp),
                    contentPadding = PaddingValues(Dimens.SpaceXs),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXs),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.visibleItems, key = { it.uri.toString() }) { item ->
                        GalleryTile(
                            item = item,
                            selected = item.uri in state.selection,
                            loadThumbnail = viewModel::thumbnail,
                            onClick = {
                                if (state.selectionMode) viewModel.toggleSelection(item.uri) else onOpenItem(item)
                            },
                            onLongClick = { viewModel.toggleSelection(item.uri) }
                        )
                    }
                }
            }
        }
    }

    confirmDelete?.let { uris ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(pluralStringResource(R.plurals.delete_confirm_title, uris.size, uris.size)) },
            text = { Text(stringResource(R.string.delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(uris)
                    confirmDelete = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

@Composable
private fun FilterRow(state: GalleryUiState, viewModel: GalleryViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
    ) {
        listOf(
            MediaFilter.ALL to R.string.filter_all,
            MediaFilter.PHOTOS to R.string.filter_photos,
            MediaFilter.VIDEOS to R.string.filter_videos
        ).forEach { (filter, label) ->
            FilterChip(selected = state.mediaFilter == filter, onClick = { viewModel.setMediaFilter(filter) }, label = { Text(stringResource(label)) })
        }
        Spacer(Modifier.size(Dimens.SpaceS))
        listOf(StampFilter.STAMPED to R.string.filter_stamped, StampFilter.UNSTAMPED to R.string.filter_unstamped)
            .forEach { (filter, label) ->
                val selected = state.stampFilter == filter
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.setStampFilter(if (selected) StampFilter.ALL else filter) },
                    label = { Text(stringResource(label)) }
                )
            }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryTile(
    item: MediaItem,
    selected: Boolean,
    loadThumbnail: suspend (Uri) -> android.graphics.Bitmap?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var thumbnail by remember(item.uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(item.uri) { thumbnail = loadThumbnail(item.uri) }
    val shape = RoundedCornerShape(Dimens.CornerSmall)
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics {
                contentDescription = item.displayName
                this.selected = selected
            }
    ) {
        thumbnail?.let {
            Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (item.isVideo) {
            Icon(
                Icons.Outlined.PlayCircle,
                contentDescription = stringResource(R.string.cd_video),
                tint = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(Dimens.SpaceXs).size(Dimens.Icon)
            )
        }
        if (selected) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = stringResource(R.string.cd_selected),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Dimens.SpaceXs)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .size(Dimens.Icon)
            )
        }
    }
}

@Composable
private fun EmptyGallery() {
    Column(
        Modifier.fillMaxSize().padding(Dimens.SpaceXxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(Dimens.SpaceL))
        Text(stringResource(R.string.gallery_empty_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Dimens.SpaceXs))
        Text(stringResource(R.string.gallery_empty_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun shareMedia(context: android.content.Context, items: List<MediaItem>) {
    if (items.isEmpty()) return
    val type = when {
        items.all { it.isVideo } -> "video/*"
        items.none { it.isVideo } -> "image/jpeg"
        else -> "*/*"
    }
    val intent = if (items.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, items.first().uri)
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(items.map { it.uri }))
    }.setType(type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, null))
}
