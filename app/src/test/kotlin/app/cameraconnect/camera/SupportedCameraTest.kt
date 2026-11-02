package app.cameraconnect.camera

import app.cameraconnect.Fixtures
import app.cameraconnect.discovery.upnp.DeviceDescription
import app.cameraconnect.discovery.upnp.DeviceDescriptionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SupportedCameraTest {

    @Test
    fun `recognises the ILCE-7RM4 from its real device description`() {
        val device = DeviceDescriptionParser.parse(
            Fixtures.read("sony_a7riv_dd.xml"),
            "http://192.168.122.1:64321/dd.xml",
        )

        assertEquals(SupportedCamera.SONY_A7R_IV, SupportedCamera.find(device))
    }

    @Test
    fun `an unlisted camera is not matched`() {
        val device = DeviceDescription(
            deviceType = "urn:schemas-upnp-org:device:MediaServer:1",
            friendlyName = "X-T5",
            manufacturer = "FUJIFILM",
            modelName = "X-T5",
            modelDescription = null,
            modelNumber = null,
            serialNumber = null,
            udn = null,
            presentationUrl = null,
            urlBase = null,
            services = emptyList(),
            embeddedDevices = emptyList(),
            extras = emptyMap(),
        )

        assertNull(SupportedCamera.find(device))
    }
}
