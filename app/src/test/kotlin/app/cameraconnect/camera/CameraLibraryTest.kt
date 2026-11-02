package app.cameraconnect.camera

import app.cameraconnect.Fixtures
import app.cameraconnect.discovery.DiscoveryLogger
import app.cameraconnect.discovery.upnp.BrowseFlag
import app.cameraconnect.discovery.upnp.BrowseOutcome
import app.cameraconnect.discovery.upnp.BrowseResponse
import app.cameraconnect.discovery.upnp.ContentBrowser
import app.cameraconnect.discovery.upnp.DidlLite
import app.cameraconnect.discovery.upnp.DidlLiteParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Replays the hierarchy captured from the real ILCE-7RM4:
 * 0 → PhotoRoot → Date → one container per day → items.
 */
class CameraLibraryTest {

    private val captured = DidlLiteParser.parse(Fixtures.read("sony_a7riv_didl.xml"))

    /** Fake camera answering Browse from the captured containers and items. */
    private inner class FakeCamera : ContentBrowser {
        val calls = mutableListOf<String>()

        override suspend fun browse(objectId: String, flag: BrowseFlag, startingIndex: Int, requestedCount: Int): BrowseOutcome {
            calls += objectId
            val containers = captured.containers.filter { it.parentId == objectId }
            val items = captured.items.filter { it.parentId == objectId }
            val children = DidlLite(containers, items)
            val total = containers.size + items.size
            val page = DidlLite(
                containers.drop(startingIndex).take(requestedCount),
                items.drop((startingIndex - containers.size).coerceAtLeast(0)).take(requestedCount),
            )
            return BrowseOutcome(
                httpCode = 200,
                rawBody = "",
                response = BrowseResponse.Success("", page.all.size, total, "1"),
                didl = if (children.isEmpty && objectId != "0") DidlLite(emptyList(), emptyList()) else page,
                error = null,
            )
        }
    }

    @Test
    fun `finds the day containers under the single-child chain and sorts them newest first`() = runTest {
        val camera = FakeCamera()
        val library = CameraLibrary(camera, DiscoveryLogger(), photoRootHint = null)

        val days = library.loadDays()

        assertEquals(listOf("2026-11-1", "2026-10-31", "2026-10-30"), days.map { it.title })
        assertEquals(listOf(168, 147, 105), days.map { it.photoCount })
        // root, PhotoRoot, Date, then one probe of the first day container
        assertEquals(listOf("0", "PhotoRoot", "02_00_0123456789_000000_000000_000000", "03_01_0123456789_000001_000000_000000"), camera.calls)
    }

    @Test
    fun `the photo root hint skips the root container`() = runTest {
        val camera = FakeCamera()
        val library = CameraLibrary(camera, DiscoveryLogger(), photoRootHint = "PhotoRoot")

        val days = library.loadDays()

        assertEquals(3, days.size)
        assertEquals("PhotoRoot", camera.calls.first())
    }

    @Test
    fun `loads the photos of a day`() = runTest {
        val camera = FakeCamera()
        val library = CameraLibrary(camera, DiscoveryLogger(), null)
        val day = PhotoDay("03_01_0123456789_000003_000000_000000", "2026-11-1", 2)

        val photos = library.loadPhotos(day, 0, 50)

        assertEquals(listOf("DSC00005.JPG", "DSC00006.JPG"), photos.map { it.title })
        assertEquals(day.containerId, photos.first().dayId)
    }

    @Test
    fun `days with unparseable titles go after the dated ones`() {
        val sorted = CameraLibrary.sortNewestFirst(
            listOf(
                PhotoDay("a", "Misc", 1),
                PhotoDay("b", "2026-1-5", 1),
                PhotoDay("c", "2026-11-1", 1),
                PhotoDay("d", "Other", 1),
            ),
        )

        assertEquals(listOf("2026-11-1", "2026-1-5", "Other", "Misc"), sorted.map { it.title })
    }
}
