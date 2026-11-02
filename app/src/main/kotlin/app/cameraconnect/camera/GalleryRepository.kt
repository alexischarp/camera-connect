package app.cameraconnect.camera

import app.cameraconnect.discovery.DiscoveryLogger
import app.cameraconnect.discovery.upnp.ContentDirectoryClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

data class GalleryState(
    val photos: List<CameraPhoto> = emptyList(),
    val days: List<PhotoDay> = emptyList(),
    val endReached: Boolean = false,
)

/**
 * Holds the photo list across screens (gallery → preview → back) for the current camera session,
 * loading pages newest first.
 */
class GalleryRepository(
    private val http: OkHttpClient,
    private val logger: DiscoveryLogger,
) {

    private val _state = MutableStateFlow(GalleryState())
    val state: StateFlow<GalleryState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var session: CameraSession? = null
    private var library: CameraLibrary? = null
    private var pager: PhotoPager? = null

    fun photo(id: String): CameraPhoto? = _state.value.photos.firstOrNull { it.id == id }

    /** Loads the first page, re-reading the day list; also used by pull-to-refresh. */
    suspend fun refresh(session: CameraSession): Unit = mutex.withLock {
        val library = libraryFor(session)
        val days = library.loadDays()
        val pager = PhotoPager(days, CameraLibrary.PAGE) { day, start, count -> library.loadPhotos(day, start, count) }
        this.pager = pager
        val first = pager.next()
        _state.value = GalleryState(photos = first, days = days, endReached = pager.isExhausted)
    }

    /** Appends the next (older) page; no-op when everything has been read. */
    suspend fun loadMore(): Unit = mutex.withLock {
        val pager = pager ?: return@withLock
        if (pager.isExhausted) return@withLock
        val page = pager.next()
        _state.update { current ->
            val known = current.photos.mapTo(HashSet()) { it.id }
            current.copy(
                photos = current.photos + page.filterNot { it.id in known },
                endReached = pager.isExhausted,
            )
        }
    }

    fun clear() {
        session = null
        library = null
        pager = null
        _state.value = GalleryState()
    }

    private fun libraryFor(session: CameraSession): CameraLibrary {
        val current = library
        if (current != null && this.session === session) return current
        logger.i(TAG, "New gallery library for ${session.displayName}")
        this.session = session
        _state.value = GalleryState()
        return CameraLibrary(
            client = ContentDirectoryClient(http, session.contentDirectory, logger),
            logger = logger,
            photoRootHint = session.photoRootHint,
        ).also { library = it }
    }

    private companion object {
        const val TAG = "Gallery"
    }
}
