package app.cameraconnect.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cameraconnect.AppContainer
import app.cameraconnect.camera.CameraPhoto
import app.cameraconnect.camera.CameraSession
import app.cameraconnect.camera.CameraSessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GalleryUiState(
    val cameraName: String? = null,
    val photos: List<CameraPhoto> = emptyList(),
    val loadingInitial: Boolean = false,
    val loadingMore: Boolean = false,
    val refreshing: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
)

class GalleryViewModel(private val container: AppContainer) : ViewModel() {

    private data class Flags(
        val loadingInitial: Boolean = false,
        val loadingMore: Boolean = false,
        val refreshing: Boolean = false,
        val error: String? = null,
    )

    private val flags = MutableStateFlow(Flags())

    val uiState: StateFlow<GalleryUiState> = combine(
        container.gallery.state,
        flags,
        container.sessionManager.state,
    ) { gallery, flags, session ->
        GalleryUiState(
            cameraName = (session as? CameraSessionState.Ready)?.session?.displayName,
            photos = gallery.photos,
            loadingInitial = flags.loadingInitial,
            loadingMore = flags.loadingMore,
            refreshing = flags.refreshing,
            endReached = gallery.endReached,
            error = flags.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUiState())

    init {
        if (container.gallery.state.value.photos.isEmpty()) load(initial = true)
    }

    fun refresh() = load(initial = false)

    fun loadMore() {
        if (flags.value.loadingMore || flags.value.loadingInitial || container.gallery.state.value.endReached) return
        viewModelScope.launch {
            flags.update { it.copy(loadingMore = true) }
            try {
                container.gallery.loadMore()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                flags.update { it.copy(error = e.message ?: e.toString()) }
            } finally {
                flags.update { it.copy(loadingMore = false) }
            }
        }
    }

    private fun load(initial: Boolean) {
        val session = currentSession() ?: return
        if (flags.value.loadingInitial || flags.value.refreshing) return
        viewModelScope.launch {
            flags.update { it.copy(loadingInitial = initial, refreshing = !initial, error = null) }
            try {
                container.gallery.refresh(session)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                flags.update { it.copy(error = e.message ?: e.toString()) }
            } finally {
                flags.update { it.copy(loadingInitial = false, refreshing = false) }
            }
        }
    }

    private fun currentSession(): CameraSession? =
        (container.sessionManager.state.value as? CameraSessionState.Ready)?.session
}
