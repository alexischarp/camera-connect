package app.cameraconnect.camera

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoPagerTest {

    private val days = listOf(
        PhotoDay("newest", "2026-11-1", 105),
        PhotoDay("older", "2026-10-31", 3),
        PhotoDay("empty", "2026-10-30", 0),
    )

    private val requests = mutableListOf<Triple<String, Int, Int>>()

    private fun pager() = PhotoPager(days, pageSize = 50) { day, start, count ->
        requests += Triple(day.containerId, start, count)
        (start until start + count).map { index -> photo(day.containerId, index) }
    }

    @Test
    fun `pages run from the newest day backwards and reverse each page`() = runTest {
        val pager = pager()

        val first = pager.next()
        assertEquals(50, first.size)
        assertEquals("newest-104", first.first().id)
        assertEquals("newest-55", first.last().id)
        assertEquals(Triple("newest", 55, 50), requests.single())

        val second = pager.next()
        assertEquals("newest-54", second.first().id)
        assertEquals("newest-5", second.last().id)

        val third = pager.next()
        assertEquals(listOf("newest-4", "newest-3", "newest-2", "newest-1", "newest-0"), third.map { it.id })
        assertFalse(pager.isExhausted)

        val fourth = pager.next()
        assertEquals(listOf("older-2", "older-1", "older-0"), fourth.map { it.id })

        assertTrue(pager.next().isEmpty())
        assertTrue(pager.isExhausted)
    }

    @Test
    fun `no days means exhausted immediately`() = runTest {
        val pager = PhotoPager(emptyList(), 50) { _, _, _ -> error("must not be called") }

        assertTrue(pager.isExhausted)
        assertTrue(pager.next().isEmpty())
    }

    private fun photo(day: String, index: Int) = CameraPhoto(
        id = "$day-$index",
        title = "DSC$index.JPG",
        dayId = day,
        dateText = null,
        thumbnail = null,
        preview = null,
        original = null,
    )
}
