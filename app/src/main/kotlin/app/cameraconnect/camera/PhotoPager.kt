package app.cameraconnect.camera

/**
 * Walks the photo days from the newest one backwards and, inside each day, from the last item
 * backwards, so the first page is the most recent shots. The camera lists items oldest first and
 * answers at most [pageSize] items per Browse, hence the backward paging.
 */
class PhotoPager(
    private val days: List<PhotoDay>,
    private val pageSize: Int,
    private val fetch: suspend (day: PhotoDay, startingIndex: Int, count: Int) -> List<CameraPhoto>,
) {
    private var dayIndex = 0
    private var remaining = days.firstOrNull()?.photoCount ?: 0

    val isExhausted: Boolean get() = dayIndex >= days.size

    /** Next page, newest first; empty once every day has been read. */
    suspend fun next(): List<CameraPhoto> {
        while (!isExhausted) {
            if (remaining <= 0) {
                advanceDay()
                continue
            }
            val day = days[dayIndex]
            val start = maxOf(0, remaining - pageSize)
            val count = remaining - start
            val photos = fetch(day, start, count)
            remaining = start
            if (photos.isNotEmpty()) return photos.asReversed()
        }
        return emptyList()
    }

    private fun advanceDay() {
        dayIndex++
        remaining = days.getOrNull(dayIndex)?.photoCount ?: 0
    }
}
