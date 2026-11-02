package app.cameraconnect.discovery.upnp

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

class UpnpParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Thin DOM helpers. DOM is used (rather than XmlPullParser) because it exists both on Android and
 * on the plain JVM used by unit tests, and the documents involved are small.
 */
internal object XmlDom {

    fun parse(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            runCatching { isXIncludeAware = false }
            trySetFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            trySetFeature("http://xml.org/sax/features/external-general-entities", false)
            trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        // Some devices prefix the document with a byte-order mark or whitespace, which the parser rejects.
        val cleaned = xml.trimStart { it.isWhitespace() || it == Char(BYTE_ORDER_MARK) }
        return try {
            builder.parse(InputSource(StringReader(cleaned)))
        } catch (e: Exception) {
            throw UpnpParseException("Invalid XML: ${e.message}", e)
        }
    }

    private const val BYTE_ORDER_MARK = 0xFEFF

    private fun DocumentBuilderFactory.trySetFeature(name: String, value: Boolean) {
        runCatching { setFeature(name, value) }
    }

    val Node.localNameOrTag: String
        get() = localName ?: nodeName.substringAfter(':')

    val Element.qualifiedName: String
        get() = prefix?.let { "$it:$localNameOrTag" } ?: localNameOrTag

    fun Node.childElements(): List<Element> {
        val children = childNodes
        return (0 until children.length).mapNotNull { children.item(it) as? Element }
    }

    fun Node.firstChild(localName: String): Element? =
        childElements().firstOrNull { it.localNameOrTag == localName }

    fun Node.childText(localName: String): String? =
        firstChild(localName)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    fun Element.attributeMap(): Map<String, String> {
        val map = attributes
        return (0 until map.length).mapNotNull { map.item(it) as? Attr }
            .associate { it.name to it.value }
    }

    /** Depth-first search for the first element with the given local name. */
    fun Node.findFirst(localName: String): Element? {
        for (child in childElements()) {
            if (child.localNameOrTag == localName) return child
            child.findFirst(localName)?.let { return it }
        }
        return null
    }
}
