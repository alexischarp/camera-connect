package app.cameraconnect.discovery.upnp

/** One <res> element: a downloadable rendition of an item. */
data class DidlResource(
    val url: String,
    val protocolInfo: String?,
    val resolution: String?,
    val size: Long?,
    /** Every attribute of the element, including the ones mapped above. */
    val attributes: Map<String, String>,
) {
    /** Third field of protocolInfo, e.g. "image/jpeg". */
    val mimeType: String?
        get() = protocolInfo?.split(':')?.getOrNull(2)?.takeIf { it.isNotEmpty() && it != "*" }

    /** DLNA profile name, e.g. JPEG_LRG / JPEG_SM / JPEG_TN. */
    val dlnaProfile: String?
        get() = protocolInfo?.substringAfter("DLNA.ORG_PN=", "")?.substringBefore(';')?.takeIf { it.isNotEmpty() }

    val width: Int? get() = resolution?.substringBefore('x')?.trim()?.toIntOrNull()
    val height: Int? get() = resolution?.substringAfter('x', "")?.trim()?.toIntOrNull()

    val pixelCount: Long? get() = width?.let { w -> height?.let { h -> w.toLong() * h } }

    fun summary(): String = buildString {
        append(resolution ?: "?x?")
        append("  size=").append(size ?: "?")
        append("  ").append(mimeType ?: "?")
        dlnaProfile?.let { append("  ").append(it) }
        val others = attributes.keys - setOf("protocolInfo", "resolution", "size")
        for (name in others) append("  ").append(name).append('=').append(attributes[name])
        append("  ").append(url)
    }
}

sealed class DidlObject {
    abstract val id: String
    abstract val parentId: String?
    abstract val title: String?
    abstract val upnpClass: String?
    abstract val restricted: Boolean?
    /** Child elements that are not part of the standard DIDL-Lite vocabulary (vendor extensions). */
    abstract val extras: Map<String, String>
}

data class DidlContainer(
    override val id: String,
    override val parentId: String?,
    override val title: String?,
    override val upnpClass: String?,
    override val restricted: Boolean?,
    override val extras: Map<String, String>,
    val childCount: Int?,
    val searchable: Boolean?,
) : DidlObject()

data class DidlItem(
    override val id: String,
    override val parentId: String?,
    override val title: String?,
    override val upnpClass: String?,
    override val restricted: Boolean?,
    override val extras: Map<String, String>,
    val date: String?,
    val albumArtUri: String?,
    val resources: List<DidlResource>,
) : DidlObject() {
    val isImage: Boolean get() = upnpClass?.contains("imageItem", ignoreCase = true) == true
}

data class DidlLite(
    val containers: List<DidlContainer>,
    val items: List<DidlItem>,
) {
    val all: List<DidlObject> get() = containers + items
    val isEmpty: Boolean get() = containers.isEmpty() && items.isEmpty()
}
