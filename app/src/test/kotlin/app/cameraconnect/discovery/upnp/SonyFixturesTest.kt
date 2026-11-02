package app.cameraconnect.discovery.upnp

import app.cameraconnect.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parsers against the documents captured from the real ILCE-7RM4 on 2026-11-02. */
class SonyFixturesTest {

    @Test
    fun `device description of the camera`() {
        val device = DeviceDescriptionParser.parse(
            Fixtures.read("sony_a7riv_dd.xml"),
            "http://192.168.122.1:64321/dd.xml",
        )

        assertEquals("ILCE-7RM4", device.friendlyName)
        assertEquals("SonyImagingDevice", device.modelName)
        assertEquals("urn:schemas-upnp-org:device:MediaServer:1", device.deviceType)
        assertNull(device.urlBase)
        assertEquals(3, device.services.size)
        val contentDirectory = requireNotNull(device.findService("ContentDirectory"))
        assertEquals("http://192.168.122.1:64321/upnp/control/ContentDirectory", contentDirectory.controlUrl)
        assertEquals("http://192.168.122.1:64321/CdsDesc.xml", contentDirectory.scpdUrl)
        assertEquals("PhotoRoot", device.extras["av:photoRoot"])
        assertEquals("DMS-1.50", device.extras["dlna:X_DLNADOC"])
    }

    @Test
    fun `content directory scpd of the camera`() {
        val scpd = ScpdParser.parse(Fixtures.read("sony_a7riv_cds_scpd.xml"))

        assertEquals(listOf("GetSearchCapabilities", "GetSortCapabilities", "GetSystemUpdateID", "Browse"), scpd.actions.map { it.name })
        assertTrue(scpd.hasAction("Browse"))
        val flag = scpd.stateVariables.first { it.name == "A_ARG_TYPE_BrowseFlag" }
        assertEquals(listOf("BrowseMetadata", "BrowseDirectChildren"), flag.allowedValues)
    }

    @Test
    fun `didl of the camera hierarchy`() {
        val didl = DidlLiteParser.parse(Fixtures.read("sony_a7riv_didl.xml"))

        assertEquals(6, didl.containers.size)
        val date = didl.containers.first { it.title == "Date" }
        assertEquals("photoAllDateTime:1", date.extras["av:containerClass"])
        assertEquals(3, date.childCount)
        assertEquals("P,V", date.extras["av:mediaClass"])
        assertEquals(6, didl.items.size)
        val jpeg = didl.items.first { it.title == "DSC00005.JPG" }
        assertEquals(4, jpeg.resources.size)
        assertEquals("STILL", jpeg.extras["av:contentType"])
        val raw = didl.items.first { it.title == "DSC00002.ARW" }
        assertEquals(listOf("JPEG_SM", "JPEG_LRG", "JPEG_TN"), raw.resources.map { it.dlnaProfile })
    }
}
