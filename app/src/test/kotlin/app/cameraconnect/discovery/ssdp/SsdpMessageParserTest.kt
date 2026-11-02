package app.cameraconnect.discovery.ssdp

import app.cameraconnect.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SsdpMessageParserTest {

    @Test
    fun `parses an M-SEARCH response`() {
        val message = requireNotNull(SsdpMessageParser.parse(Fixtures.read("ssdp_response.txt")))

        assertTrue(message.isSearchResponse)
        assertFalse(message.isNotify)
        assertEquals("http://192.168.122.1:64321/DmsDescPush.xml", message.location)
        assertEquals("urn:schemas-upnp-org:device:MediaServer:1", message.searchTarget)
        assertEquals("UPnP/1.0 SonyImagingDevice/1.0", message.server)
        assertEquals(
            "uuid:00000000-0000-1010-8000-00aabbccddee::urn:schemas-upnp-org:device:MediaServer:1",
            message.usn,
        )
        assertEquals("pa=\"ILCE-7RM4\";", message.header("x-av-physical-unit-info"))
    }

    @Test
    fun `parses a NOTIFY advertisement`() {
        val message = requireNotNull(SsdpMessageParser.parse(Fixtures.read("ssdp_notify.txt")))

        assertTrue(message.isNotify)
        assertEquals("upnp:rootdevice", message.searchTarget)
        assertEquals("ssdp:alive", message.notificationSubType)
        assertEquals("http://192.168.122.1:64321/DmsDescPush.xml", message.location)
    }

    @Test
    fun `header names are case-insensitive and values trimmed`() {
        val message = requireNotNull(SsdpMessageParser.parse("HTTP/1.1 200 OK\r\nlocation:   http://x/desc.xml  \r\nExt:\r\n\r\n"))

        assertEquals("http://x/desc.xml", message.location)
        assertEquals("", message.header("EXT"))
    }

    @Test
    fun `stops reading headers at the first blank line`() {
        val message = requireNotNull(SsdpMessageParser.parse("HTTP/1.1 200 OK\r\nST: a\r\n\r\nLOCATION: ignored\r\n"))

        assertNull(message.location)
    }

    @Test
    fun `empty datagram yields null`() {
        assertNull(SsdpMessageParser.parse(""))
        assertNull(SsdpMessageParser.parse("\r\n\r\n"))
    }

    @Test
    fun `builds a well-formed M-SEARCH request`() {
        val request = SsdpMessageParser.buildSearchRequest("239.255.255.250:1900", "ssdp:all", 2, "Test/1.0")

        assertTrue(request.startsWith("M-SEARCH * HTTP/1.1\r\n"))
        assertTrue(request.contains("HOST: 239.255.255.250:1900\r\n"))
        assertTrue(request.contains("MAN: \"ssdp:discover\"\r\n"))
        assertTrue(request.contains("MX: 2\r\n"))
        assertTrue(request.contains("ST: ssdp:all\r\n"))
        assertTrue(request.endsWith("\r\n\r\n"))
        val parsed = requireNotNull(SsdpMessageParser.parse(request))
        assertTrue(parsed.isSearchRequest)
    }
}
