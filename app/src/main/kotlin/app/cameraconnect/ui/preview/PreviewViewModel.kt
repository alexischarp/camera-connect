package app.cameraconnect.ui.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cameraconnect.AppContainer
import app.cameraconnect.camera.CameraPhoto
import app.cameraconnect.camera.CameraSessionState
import app.cameraconnect.download.DownloadState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class PreviewViewModel(private val container: AppContainer, photoId: String) : ViewModel() {

    val photo: CameraPhoto? = container.gallery.photo(photoId)

    val downloadState: StateFlow<DownloadState> = container.downloader.states
        .map { it[photoId] ?: DownloadState.Idle }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadState.Idle)

    fun download() {
        val photo = photo ?: return
        val session = (container.sessionManager.state.value as? CameraSessionState.Ready)?.session
        container.downloader.download(photo, session?.folderName)
    }
}
