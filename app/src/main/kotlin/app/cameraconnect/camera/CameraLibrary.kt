package app.cameraconnect.camera

import app.cameraconnect.discovery.DiscoveryLogger
import app.cameraconnect.discovery.upnp.BrowseFlag
import app.cameraconnect.discovery.upnp.BrowseResponse
import app.cameraconnect.discovery.upnp.ContentBrowser
import app.cameraconnect.discovery.upnp.DidlContainer
import app.cameraconnect.discovery.upnp.DidlLite
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** A container that directly holds photos; on Sony cameras there is one per shooting day. */
data class PhotoDay(
    val containerId: String,
    val title: String,
    val photoCount: Int,
) {
    /** Parsed from titles such as "2026-11-1"; null when the title is not a date. */
    val date: LocalDate? get() = try {
        LocalDate.parse(title.trim(), DAY_FORMAT)
    } catch (e: DateTimeParseException) {
        null
    }

    private companion object {
        val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-M-d")
    }
}

class CameraBrowseException(message: String) : IOException(message)

/**
 * Reads the camera's ContentDirectory: locates the containers that hold photos and fetches pages
 * of items from them. Hierarchy and object identifiers all come from the camera's own answers.
 */
class CameraLibrary(
    private val client: ContentBrowser,
    private val logger: DiscoveryLogger,
    private val photoRootHint: String? = null,
) {

    /** Photo containers, newest day first. */
    suspend fun loadDays(): List<PhotoDay> {
        val start = photoRootHint ?: ROOT_ID
        var leaves = try {
            findLeafContainers(null, start, depth = 0)
        } catch (e: CameraBrowseException) {
            if (start == ROOT_ID) throw e
            logger.w(TAG, "Photo root hint \"$start\" unusable (${e.message}); walking from the root")
            emptyList()
        }
        if (leaves.isEmpty() && start != ROOT_ID) leaves = findLeafContainers(null, ROOT_ID, depth = 0)
        val days = leaves.map { container ->
            PhotoDay(
                containerId = container.id,
                title = container.title ?: container.id,
                photoCount = container.childCount ?: countChildren(container.id),
            )
        }
        logger.i(TAG, "Photo days: " + days.joinToString { "${it.title} (${it.photoCount})" })
        return sortNewestFirst(days)
    }

    /** Items of [day] in the camera's own order (oldest first on Sony cameras). */
    suspend fun loadPhotos(day: PhotoDay, startingIndex: Int, count: Int): List<CameraPhoto> {
        val didl = browse(day.containerId, startingIndex, count)
        return didl.items.mapNotNull { PhotoMapper.fromItem(it, day.containerId) }
    }

    /**
     * Descends single-child chains; at the first level with several containers, probes the first
     * one: if it holds items, all siblings are taken as photo containers, otherwise each is walked.
     * [container] is the already-known description of [containerId] (null for the starting point).
     */
    private suspend fun findLeafContainers(container: DidlContainer?, containerId: String, depth: Int): List<DidlContainer> {
        if (depth > MAX_DEPTH) return emptyList()
        val children = browse(containerId, 0, PAGE)
        if (children.items.isNotEmpty()) {
            // Photos directly here: this container is itself a "day".
            return listOf(container ?: syntheticContainer(containerId))
        }
        val containers = children.containers
        return when {
            containers.isEmpty() -> emptyList()
            containers.size == 1 -> findLeafContainers(containers.single(), containers.single().id, depth + 1)
            else -> {
                val probe = browse(containers.first().id, 0, 1)
                if (probe.items.isNotEmpty()) containers
                else containers.flatMap { findLeafContainers(it, it.id, depth + 1) }
            }
        }
    }

    private fun syntheticContainer(containerId: String) = DidlContainer(
        id = containerId,
        parentId = null,
        title = containerId,
        upnpClass = null,
        restricted = null,
        extras = emptyMap(),
        childCount = null,
        searchable = null,
    )

    private suspend fun countChildren(containerId: String): Int {
        val outcome = client.browse(containerId, BrowseFlag.DIRECT_CHILDREN, 0, 1)
        return (outcome.response as? BrowseResponse.Success)?.totalMatches ?: 0
    }

    private suspend fun browse(containerId: String, startingIndex: Int, count: Int): DidlLite {
        val outcome = client.browse(containerId, BrowseFlag.DIRECT_CHILDREN, startingIndex, count)
        outcome.error?.let { throw CameraBrowseException("Browse $containerId failed: $it") }
        return when (val response = outcome.response) {
            is BrowseResponse.Success -> outcome.didl ?: DidlLite(emptyList(), emptyList())
            is BrowseResponse.Fault -> throw CameraBrowseException(
                "Browse $containerId: UPnP error ${response.errorCode} ${response.errorDescription}",
            )
            null -> throw CameraBrowseException("Browse $containerId: HTTP ${outcome.httpCode}, unreadable answer")
        }
    }

    companion object {
        private const val TAG = "Library"
        private const val MAX_DEPTH = 5
        const val ROOT_ID = "0"
        const val PAGE = 50

        /** Newest day first; days whose title is not a date keep their listing order after the dated ones. */
        fun sortNewestFirst(days: List<PhotoDay>): List<PhotoDay> {
            val (dated, undated) = days.partition { it.date != null }
            return dated.sortedByDescending { it.date } + undated.asReversed()
        }
    }
}
