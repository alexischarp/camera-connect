package app.cameraconnect.discovery.upnp

import app.cameraconnect.discovery.upnp.XmlDom.attributeMap
import app.cameraconnect.discovery.upnp.XmlDom.childElements
import app.cameraconnect.discovery.upnp.XmlDom.childText
import app.cameraconnect.discovery.upnp.XmlDom.localNameOrTag
import app.cameraconnect.discovery.upnp.XmlDom.qualifiedName
import org.w3c.dom.Element

/** Parses the DIDL-Lite document returned in the Result argument of a ContentDirectory Browse. */
object DidlLiteParser {

    private val standardChildren = setOf("title", "class", "res", "date", "albumArtURI")

    fun parse(xml: String): DidlLite {
        val root = XmlDom.parse(xml).documentElement ?: throw UpnpParseException("Empty DIDL-Lite")
        val containers = mutableListOf<DidlContainer>()
        val items = mutableListOf<DidlItem>()
        for (element in root.childElements()) {
            when (element.localNameOrTag) {
                "container" -> containers += parseContainer(element)
                "item" -> items += parseItem(element)
            }
        }
        return DidlLite(containers, items)
    }

    private fun parseContainer(element: Element): DidlContainer = DidlContainer(
        id = element.getAttribute("id"),
        parentId = element.attributeOrNull("parentID"),
        title = element.childText("title"),
        upnpClass = element.childText("class"),
        restricted = element.attributeOrNull("restricted")?.toBooleanFlag(),
        extras = extras(element),
        childCount = element.attributeOrNull("childCount")?.toIntOrNull(),
        searchable = element.attributeOrNull("searchable")?.toBooleanFlag(),
    )

    private fun parseItem(element: Element): DidlItem = DidlItem(
        id = element.getAttribute("id"),
        parentId = element.attributeOrNull("parentID"),
        title = element.childText("title"),
        upnpClass = element.childText("class"),
        restricted = element.attributeOrNull("restricted")?.toBooleanFlag(),
        extras = extras(element),
        date = element.childText("date"),
        albumArtUri = element.childText("albumArtURI"),
        resources = element.childElements()
            .filter { it.localNameOrTag == "res" }
            .mapNotNull { parseResource(it) },
    )

    private fun parseResource(element: Element): DidlResource? {
        val url = element.textContent.trim().takeIf { it.isNotEmpty() } ?: return null
        val attributes = element.attributeMap()
        return DidlResource(
            url = url,
            protocolInfo = attributes["protocolInfo"],
            resolution = attributes["resolution"],
            size = attributes["size"]?.toLongOrNull(),
            attributes = attributes,
        )
    }

    private fun extras(element: Element): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (child in element.childElements()) {
            if (child.localNameOrTag in standardChildren) continue
            val text = child.textContent.trim().replace(Regex("\\s+"), " ")
            val value = if (child.attributes.length > 0) "$text ${child.attributeMap()}" else text
            result.merge(child.qualifiedName, value) { old, new -> "$old | $new" }
        }
        return result
    }

    private fun Element.attributeOrNull(name: String): String? =
        if (hasAttribute(name)) getAttribute(name) else null

    private fun String.toBooleanFlag(): Boolean? = when (lowercase()) {
        "1", "true" -> true
        "0", "false" -> false
        else -> null
    }
}
