package app.cameraconnect.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import app.cameraconnect.camera.CameraPhoto
import app.cameraconnect.discovery.DiscoveryLogger
import app.cameraconnect.network.CameraHttpClient
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface DownloadState {
    data object Idle : DownloadState
    data class InProgress(val bytesRead: Long, val totalBytes: Long?) : DownloadState {
        val fraction: Float? get() = totalBytes?.takeIf { it > 0 }?.let { (bytesRead.toDouble() / it).toFloat().coerceIn(0f, 1f) }
    }
    /** [folder] is the gallery folder the file went to, e.g. "Pictures/CameraConnect/ILCE-7RM4". */
    data class Done(val uri: Uri, val bytes: Long, val folder: String) : DownloadState
    data class Failed(val message: String) : DownloadState
}

/**
 * Saves a photo's best rendition into the phone gallery through MediaStore, under
 * Pictures/CameraConnect/<camera folder>.
 * Downloads run on the application scope so leaving the preview screen does not cancel them.
 */
class PhotoDownloader(
    private val context: Context,
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
    private val logger: DiscoveryLogger,
) {

    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    /** [cameraFolder] is the camera's sub-folder (see CameraSession.folderName), or null when no camera is known. */
    fun download(photo: CameraPhoto, cameraFolder: String?) {
        if (_states.value[photo.id] is DownloadState.InProgress) return
        val rendition = photo.downloadRendition
        if (rendition == null) {
            set(photo.id, DownloadState.Failed("No downloadable rendition"))
            return
        }
        set(photo.id, DownloadState.InProgress(0, rendition.sizeBytes))
        val folder = folderFor(cameraFolder)
        scope.launch(Dispatchers.IO) {
            try {
                val (uri, bytes) = save(photo, folder, rendition.url, rendition.sizeBytes, rendition.mimeType ?: "image/jpeg")
                logger.i(TAG, "Saved ${photo.fileName} ($bytes bytes) in $folder as $uri")
                set(photo.id, DownloadState.Done(uri, bytes, folder))
            } catch (e: IOException) {
                logger.e(TAG, "Download of ${photo.title} failed: $e")
                set(photo.id, DownloadState.Failed(e.message ?: e.toString()))
            }
        }
    }

    fun reset(photoId: String) = _states.update { it - photoId }

    private fun set(photoId: String, state: DownloadState) = _states.update { it + (photoId to state) }

    private fun save(photo: CameraPhoto, folder: String, url: String, expectedSize: Long?, mimeType: String): Pair<Uri, Long> {
        val resolver = context.contentResolver
        val request = Request.Builder().url(url).header("User-Agent", CameraHttpClient.USER_AGENT).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 } ?: expectedSize
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, photo.fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, folder)
                put(MediaStore.Images.Media.IS_PENDING, 1)
                photo.dateTakenMillis?.let { put(MediaStore.Images.Media.DATE_TAKEN, it) }
            }
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore refused the file")
            var copied = 0L
            try {
                val output = resolver.openOutputStream(uri) ?: throw IOException("Cannot open $uri for writing")
                output.use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var lastReport = 0L
                    body.byteStream().use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            copied += read
                            if (copied - lastReport >= PROGRESS_STEP) {
                                lastReport = copied
                                set(photo.id, DownloadState.InProgress(copied, total))
                            }
                        }
                    }
                }
                if (total != null && copied < total) throw IOException("Truncated download: $copied of $total bytes")
            } catch (e: IOException) {
                resolver.delete(uri, null, null)
                throw e
            }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri to copied
        }
    }

    companion object {
        private const val TAG = "Download"
        private const val PROGRESS_STEP = 128L * 1024
        const val ROOT_FOLDER = "CameraConnect"

        /** Gallery folder for a camera: Pictures/CameraConnect/<camera folder>, or Pictures/CameraConnect without one. */
        fun folderFor(cameraFolder: String?): String =
            listOfNotNull(Environment.DIRECTORY_PICTURES, ROOT_FOLDER, cameraFolder?.takeIf { it.isNotBlank() }).joinToString("/")
    }
}
