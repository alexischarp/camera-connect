package app.cameraconnect.discovery.upnp

import app.cameraconnect.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DidlLiteParserTest {

    private val didl by lazy { DidlLiteParser.parse(Fixtures.read("didl_lite.xml")) }

    @Test
    fun `parses containers`() {
        assertEquals(2, didl.containers.size)
        val root = didl.containers.first()
        assertEquals("DateRoot", root.id)
        assertEquals("0", root.parentId)
        assertEquals("Date", root.title)
        assertEquals("object.container", root.upnpClass)
        assertEquals(3, root.childCount)
        assertEquals(true, root.restricted)
        assertEquals(false, root.searchable)

        val album = didl.containers[1]
        assertNull(album.childCount)
        assertEquals("date", album.extras["av:containerClass"])
    }

    @Test
    fun `parses items with every resource`() {
        assertEquals(2, didl.items.size)
        val photo = didl.items.first()
        assertEquals("DSC01234", photo.id)
        assertEquals("DSC01234.JPG", photo.title)
        assertEquals("2026-11-01T10:15:30", photo.date)
        assertEquals("http://192.168.122.1:64321/DSC01234_TN.JPG", photo.albumArtUri)
        assertTrue(photo.isImage)
        assertEquals("31457280", photo.extras["av:fileSize"])

        assertEquals(2, photo.resources.size)
        val original = photo.resources[0]
        assertEquals("http://192.168.122.1:64321/DSC01234.JPG", original.url)
        assertEquals("image/jpeg", original.mimeType)
        assertEquals("JPEG_LRG", original.dlnaProfile)
        assertEquals(9504, original.width)
        assertEquals(6336, original.height)
        assertEquals(31_457_280L, original.size)
        assertEquals("http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_LRG;DLNA.ORG_OP=01", original.attributes["protocolInfo"])

        val screen = photo.resources[1]
        assertEquals("JPEG_SM", screen.dlnaProfile)
        assertEquals(1616L * 1080L, screen.pixelCount)
    }

    @Test
    fun `non image items keep their generic resources`() {
        val raw = didl.items[1]
        assertFalse(raw.isImage)
        assertEquals("object.item", raw.upnpClass)
        val resource = raw.resources.single()
        assertEquals("application/octet-stream", resource.mimeType)
        assertNull(resource.dlnaProfile)
        assertNull(resource.resolution)
        assertEquals(61_000_000L, resource.size)
    }

    @Test
    fun `empty document yields empty lists`() {
        val empty = DidlLiteParser.parse("<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\"/>")

        assertTrue(empty.isEmpty)
    }
}
