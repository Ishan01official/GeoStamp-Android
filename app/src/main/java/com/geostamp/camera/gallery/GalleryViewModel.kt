package com.geostamp.camera.gallery

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geostamp.camera.appContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MediaFilter { ALL, PHOTOS, VIDEOS }

enum class StampFilter { ALL, STAMPED, UNSTAMPED }

data class GalleryUiState(
    val items: List<MediaItem> = emptyList(),
    val loading: Boolean = true,
    val mediaFilter: MediaFilter = MediaFilter.ALL,
    val stampFilter: StampFilter = StampFilter.ALL,
    val selection: Set<Uri> = emptySet(),
    val batchProgress: Pair<Int, Int>? = null,
    val message: GalleryMessage? = null
) {
    val visibleItems: List<MediaItem>
        get() = items.filter { item ->
            val mediaOk = when (mediaFilter) {
                MediaFilter.ALL -> true
                MediaFilter.PHOTOS -> !item.isVideo
                MediaFilter.VIDEOS -> item.isVideo
            }
            val stampOk = when (stampFilter) {
                StampFilter.ALL -> true
                StampFilter.STAMPED -> item.isStamped
                StampFilter.UNSTAMPED -> !item.isStamped
            }
            mediaOk && stampOk
        }

    val selectionMode: Boolean get() = selection.isNotEmpty()
}

sealed interface GalleryMessage {
    data class BatchFinished(val result: BatchResult) : GalleryMessage
    data class NeedsDeleteConsent(val uris: List<Uri>) : GalleryMessage
    data class AddressEdited(val uri: Uri) : GalleryMessage
    data object AddressEditFailed : GalleryMessage
}

private const val ENSURE_ATTEMPTS = 6
private const val ENSURE_RETRY_MILLIS = 300L

class GalleryViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.appContainer
    private val repository = container.galleryRepository

    private val _state = MutableStateFlow(GalleryUiState())
    val state: StateFlow<GalleryUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val items = repository.loadMedia()
        _state.update { state ->
            state.copy(items = items, loading = false, selection = state.selection.filterTo(mutableSetOf()) { uri -> items.any { it.uri == uri } })
        }
    }

    /**
     * Makes sure [uri] is in the list, for a viewer opened straight from the camera. A capture made after the
     * gallery was last loaded is otherwise missing, and Android can take a moment to publish a new file.
     */
    suspend fun ensureLoaded(uri: Uri) {
        repeat(ENSURE_ATTEMPTS) { attempt ->
            if (_state.value.items.any { it.uri == uri }) return
            if (attempt > 0) kotlinx.coroutines.delay(ENSURE_RETRY_MILLIS)
            reload()
        }
    }

    suspend fun thumbnail(uri: Uri) = repository.thumbnail(uri)

    suspend fun metadata(uri: Uri) = repository.metadata(uri)

    suspend fun displayBitmap(uri: Uri, maxEdge: Int) = repository.displayBitmap(uri, maxEdge)

    fun setMediaFilter(filter: MediaFilter) = _state.update { it.copy(mediaFilter = filter) }

    fun setStampFilter(filter: StampFilter) = _state.update { it.copy(stampFilter = filter) }

    fun toggleSelection(uri: Uri) = _state.update {
        it.copy(selection = if (uri in it.selection) it.selection - uri else it.selection + uri)
    }

    fun clearSelection() = _state.update { it.copy(selection = emptySet()) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun delete(uris: List<Uri>) {
        viewModelScope.launch {
            val needsConsent = repository.delete(uris)
            _state.update {
                it.copy(
                    selection = emptySet(),
                    message = needsConsent.takeIf { list -> list.isNotEmpty() }?.let(GalleryMessage::NeedsDeleteConsent)
                )
            }
            refresh()
        }
    }

    fun onDeleteConsentResult(uris: List<Uri>) {
        repository.evict(uris)
        refresh()
    }

    /** Re-stamps from the unstamped source with a typed address; the existing photo and its original are kept. */
    fun editAddress(source: MediaItem, address: String) {
        viewModelScope.launch {
            val settings = container.settingsRepository.settings.first()
            val logo = withContext(Dispatchers.IO) { container.stampResources.loadLogo(settings.stamp.logoPath) }
            val result = runCatching {
                container.batchStamper.restampWithAddress(
                    source = source.uri,
                    address = address,
                    preferences = settings.stamp.copy(enabled = true),
                    logo = logo,
                    jpegQuality = settings.storage.jpegQuality
                )
            }
            _state.update {
                it.copy(message = result.fold({ uri -> GalleryMessage.AddressEdited(uri) }, { GalleryMessage.AddressEditFailed }))
            }
            refresh()
        }
    }

    fun batchStamp() {
        val photos = _state.value.items.filter { it.uri in _state.value.selection && !it.isVideo }.map { it.uri }
        if (photos.isEmpty()) return
        viewModelScope.launch {
            val settings = container.settingsRepository.settings.first()
            val logo = withContext(Dispatchers.IO) { container.stampResources.loadLogo(settings.stamp.logoPath) }
            _state.update { it.copy(selection = emptySet(), batchProgress = 0 to photos.size) }
            val result = container.batchStamper.stamp(
                uris = photos,
                preferences = settings.stamp.copy(enabled = true),
                logo = logo,
                jpegQuality = settings.storage.jpegQuality,
                addressDetail = settings.location.addressDetail
            ) { done -> _state.update { it.copy(batchProgress = done to photos.size) } }
            _state.update { it.copy(batchProgress = null, message = GalleryMessage.BatchFinished(result)) }
            refresh()
        }
    }
}
