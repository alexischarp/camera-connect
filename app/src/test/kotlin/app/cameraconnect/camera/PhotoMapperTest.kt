package app.cameraconnect.camera

import app.cameraconnect.Fixtures
import app.cameraconnect.discovery.upnp.DidlLiteParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Uses the DIDL-Lite items captured from the real ILCE-7RM4. */
class PhotoMapperTest {

    private val photos by lazy {
        DidlLiteParser.parse(Fixtures.read("sony_a7riv_didl.xml")).items
            .mapNotNull { PhotoMapper.fromItem(it, "day") }
    }

    @Test
    fun `jpeg shots expose the original plus transcoded renditions`() {
        val photo = photos.first { it.title == "DSC00005.JPG" }

        assertFalse(photo.isRawOnly)
        val original = requireNotNull(photo.original)
        assertEquals(9504, original.width)
        assertEquals(6336, original.height)
        assertEquals(15_461_118L, original.sizeBytes)
        assertEquals("image/jpeg", original.mimeType)
        assertTrue(original.url.startsWith("http://192.168.122.1:60151/ORG_DSC00005.JPG?"))
        assertEquals("JPEG_LRG", photo.preview?.dlnaProfile)
        assertEquals("JPEG_SM", photo.thumbnail?.dlnaProfile)
        assertEquals(original, photo.downloadRendition)
        assertEquals("DSC00005.JPG", photo.fileName)
        assertEquals("2026-11-01T12:13:26", photo.dateText)
        assertNotNull(photo.dateTakenMillis)
    }

    @Test
    fun `raw-only shots have no original and fall back to the large jpeg`() {
        val photo = photos.first { it.title == "DSC00002.ARW" }

        assertTrue(photo.isRawOnly)
        assertNull(photo.original)
        assertEquals("JPEG_LRG", photo.downloadRendition?.dlnaProfile)
        assertTrue(photo.downloadRendition!!.url.contains("/LRG_DSC00002.ARW?"))
        assertEquals("DSC00002.JPG", photo.fileName)
        assertEquals("JPEG_SM", photo.thumbnail?.dlnaProfile)
    }

    @Test
    fun `every captured item maps to a photo`() {
        assertEquals(6, photos.size)
        assertEquals(listOf("DSC00001.ARW", "DSC00002.ARW", "DSC00003.ARW", "DSC00004.ARW", "DSC00005.JPG", "DSC00006.JPG"), photos.map { it.title })
    }
}
