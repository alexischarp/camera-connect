package app.cameraconnect.discovery.upnp

import app.cameraconnect.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScpdParserTest {

    @Test
    fun `parses actions with their arguments`() {
        val scpd = ScpdParser.parse(Fixtures.read("content_directory_scpd.xml"))

        assertEquals(listOf("Browse", "GetSortCapabilities", "X_GetTransferStatus"), scpd.actions.map { it.name })
        val browse = scpd.actions.first()
        assertEquals(10, browse.arguments.size)
        assertEquals("ObjectID", browse.arguments.first().name)
        assertEquals("in", browse.arguments.first().direction)
        assertEquals("A_ARG_TYPE_ObjectID", browse.arguments.first().relatedStateVariable)
        assertTrue(scpd.hasAction("browse"))
        assertTrue(browse.signature().startsWith("Browse(in: ObjectID, BrowseFlag"))
    }

    @Test
    fun `parses state variables and allowed values`() {
        val scpd = ScpdParser.parse(Fixtures.read("content_directory_scpd.xml"))

        val flag = scpd.stateVariables.first { it.name == "A_ARG_TYPE_BrowseFlag" }
        assertEquals("string", flag.dataType)
        assertEquals(listOf("BrowseMetadata", "BrowseDirectChildren"), flag.allowedValues)
    }
}
