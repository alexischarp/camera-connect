package app.cameraconnect.discovery.upnp

import app.cameraconnect.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDescriptionParserTest {

    private val location = "http://192.168.122.1:64321/DmsDescPush.xml"

    @Test
    fun `parses device fields and services`() {
        val device = DeviceDescriptionParser.parse(Fixtures.read("device_description.xml"), location)

        assertEquals("urn:schemas-upnp-org:device:MediaServer:1", device.deviceType)
        assertEquals("ILCE-7RM4", device.friendlyName)
        assertEquals("Sony Corporation", device.manufacturer)
        assertEquals("ILCE-7RM4", device.modelName)
        assertEquals("uuid:00000000-0000-1010-8000-00aabbccddee", device.udn)
        assertEquals("http://192.168.122.1:64321/", device.urlBase)
        assertEquals(2, device.services.size)
    }

    @Test
    fun `resolves relative URLs against URLBase`() {
        val device = DeviceDescriptionParser.parse(Fixtures.read("device_description.xml"), location)
        val contentDirectory = requireNotNull(device.findService("ContentDirectory"))

        assertEquals("urn:schemas-upnp-org:service:ContentDirectory:1", contentDirectory.serviceType)
        assertEquals("http://192.168.122.1:64321/DmsScpd/ContentDirectory.xml", contentDirectory.scpdUrl)
        assertEquals("http://192.168.122.1:64321/upnp/control/ContentDirectory", contentDirectory.controlUrl)
        assertEquals("http://192.168.122.1:64321/upnp/event/ContentDirectory", contentDirectory.eventSubUrl)
    }

    @Test
    fun `keeps vendor extensions as extras`() {
        val device = DeviceDescriptionParser.parse(Fixtures.read("device_description.xml"), location)

        assertEquals("DMS-1.50", device.extras["dlna:X_DLNADOC"])
        val scalar = requireNotNull(device.extras["av:X_ScalarWebAPI_DeviceInfo"])
        assertTrue(scalar.contains("http://192.168.122.1:8080/sony"))
    }

    @Test
    fun `without URLBase relative URLs resolve against the location and embedded devices are searched`() {
        val device = DeviceDescriptionParser.parse(
            Fixtures.read("device_description_no_urlbase.xml"),
            "http://10.0.0.2:8080/dev/description.xml",
        )

        assertEquals("Outer", device.friendlyName)
        assertEquals(0, device.services.size)
        assertEquals(1, device.embeddedDevices.size)
        val contentDirectory = requireNotNull(device.findService("ContentDirectory"))
        assertEquals("http://10.0.0.2:8080/dev/cds.xml", contentDirectory.scpdUrl)
        assertEquals("http://10.0.0.2:8080/dev/control/cds", contentDirectory.controlUrl)
    }

    @Test
    fun `summary mentions the services`() {
        val device = DeviceDescriptionParser.parse(Fixtures.read("device_description.xml"), location)

        val summary = device.summary()
        assertNotNull(summary)
        assertTrue(summary.contains("ContentDirectory"))
        assertTrue(summary.contains("ILCE-7RM4"))
    }

    @Test(expected = UpnpParseException::class)
    fun `invalid XML raises UpnpParseException`() {
        DeviceDescriptionParser.parse("<root><device>", location)
    }
}
