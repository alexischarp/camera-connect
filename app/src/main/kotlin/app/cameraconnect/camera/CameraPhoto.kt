package app.cameraconnect.camera

import app.cameraconnect.discovery.upnp.DidlItem
import app.cameraconnect.discovery.upnp.DidlResource
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/** One downloadable rendition of a photo, as advertised by a <res> element. */
data class PhotoRendition(
    val url: String,
    val dlnaProfile: String?,
    val mimeType: String?,
    val width: Int?,
    val height: Int?,
    val sizeBytes: Long?,
) {
    val resolutionLabel: String? get() = if (width != null && height != null) "${width}×${height}" else null
}

/** A photo on the camera with the renditions chosen for each use of the app. */
data class CameraPhoto(
    val id: String,
    val title: String,
    val dayId: String,
    /** Capture time as sent by the camera, e.g. 2026-11-01T12:13:26, or null. */
    val dateText: String?,
    val thumbnail: PhotoRendition?,
    val preview: PhotoRendition?,
    /** Full-resolution file, absent when the camera only offers transcoded JPEG renditions. */
    val original: PhotoRendition?,
) {
    /** RAW photos whose original the camera does not offer: only JPEG renditions are available. */
    val isRawOnly: Boolean get() = original == null && RAW_EXTENSIONS.any { title.endsWith(".$it", ignoreCase = true) }

    /** Best rendition available for saving to the phone. */
    val downloadRendition: PhotoRendition? get() = original ?: preview ?: thumbnail

    /** Name of the file saved in the phone gallery: an original keeps its extension, transcoded renditions are JPEG. */
    val fileName: String
        get() {
            val base = title.substringBeforeLast('.').ifBlank { id }
            val extension = if (original != null && downloadRendition === original) title.substringAfterLast('.', "JPG") else "JPG"
            return "$base.$extension"
        }

    val dateTakenMillis: Long? get() = dateText?.let { text ->
        try {
            LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }
    }

    companion object {
        /** Extensions of RAW formats: Sony, Canon, Nikon, Fujifilm, Olympus, Panasonic, Pentax and DNG. */
        private val RAW_EXTENSIONS = setOf("ARW", "CR2", "CR3", "NEF", "RAF", "ORF", "RW2", "PEF", "DNG")
    }
}

/** Maps DIDL-Lite items to [CameraPhoto], picking renditions by DLNA profile. */
object PhotoMapper {

    /** DLNA JPEG profiles from smallest to largest. */
    private val profileRank = listOf("JPEG_TN", "JPEG_SM", "JPEG_MED", "JPEG_LRG")

    fun fromItem(item: DidlItem, dayId: String): CameraPhoto? {
        if (!item.isImage) return null
        val images = item.resources.filter { it.mimeType?.startsWith("image/") != false }
        if (images.isEmpty()) return null
        val profiled = images.filter { it.dlnaProfile != null }.associateBy { it.dlnaProfile!!.uppercase() }
        val original = images.filter { it.dlnaProfile == null }.maxByOrNull { it.pixelCount ?: it.size ?: 0L }
        val preview = profiled["JPEG_LRG"] ?: profiled["JPEG_MED"] ?: original ?: profiled["JPEG_SM"]
        val thumbnail = profiled["JPEG_SM"] ?: profiled["JPEG_MED"] ?: profiled["JPEG_TN"] ?: preview
        return CameraPhoto(
            id = item.id,
            title = item.title ?: item.id,
            dayId = dayId,
            dateText = item.date,
            thumbnail = thumbnail?.toRendition(),
            preview = preview?.toRendition(),
            original = original?.toRendition(),
        )
    }

    fun isKnownProfile(profile: String?): Boolean = profile?.uppercase() in profileRank

    private fun DidlResource.toRendition() = PhotoRendition(
        url = url,
        dlnaProfile = dlnaProfile,
        mimeType = mimeType,
        width = width,
        height = height,
        sizeBytes = size,
    )
}
