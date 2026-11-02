package app.cameraconnect.discovery.upnp

import app.cameraconnect.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseResponseParserTest {

    @Test
    fun `extracts the escaped DIDL-Lite result and counters`() {
        val response = BrowseResponseParser.parse(Fixtures.read("browse_response.xml"))

        val success = response as BrowseResponse.Success
        assertEquals(2, success.numberReturned)
        assertEquals(2, success.totalMatches)
        assertEquals("7", success.updateId)
        assertTrue(success.result.startsWith("<DIDL-Lite"))

        val didl = DidlLiteParser.parse(success.result)
        assertEquals(1, didl.containers.size)
        assertEquals(1, didl.items.size)
        assertEquals(3, didl.items.single().resources.size)
    }

    @Test
    fun `reports UPnP faults`() {
        val response = BrowseResponseParser.parse(Fixtures.read("browse_fault.xml"))

        val fault = response as BrowseResponse.Fault
        assertEquals(701, fault.errorCode)
        assertEquals("No such object", fault.errorDescription)
        assertEquals("UPnPError", fault.faultString)
    }

    @Test
    fun `browse envelope is well formed and escapes the object id`() {
        val envelope = ContentDirectoryClient.buildBrowseEnvelope(
            serviceType = "urn:schemas-upnp-org:service:ContentDirectory:1",
            objectId = "a&b<c>",
            flag = BrowseFlag.DIRECT_CHILDREN,
            startingIndex = 0,
            requestedCount = 50,
            filter = "*",
            sortCriteria = "-dc:date",
        )

        assertTrue(envelope.contains("<ObjectID>a&amp;b&lt;c&gt;</ObjectID>"))
        assertTrue(envelope.contains("<BrowseFlag>BrowseDirectChildren</BrowseFlag>"))
        assertTrue(envelope.contains("<RequestedCount>50</RequestedCount>"))
        assertTrue(envelope.contains("xmlns:u=\"urn:schemas-upnp-org:service:ContentDirectory:1\""))
        // Must parse back as XML.
        XmlDom.parse(envelope)
    }
}
