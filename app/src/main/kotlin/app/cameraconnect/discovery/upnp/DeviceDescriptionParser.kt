package app.cameraconnect.discovery.upnp

import app.cameraconnect.discovery.upnp.XmlDom.childElements
import app.cameraconnect.discovery.upnp.XmlDom.childText
import app.cameraconnect.discovery.upnp.XmlDom.firstChild
import app.cameraconnect.discovery.upnp.XmlDom.localNameOrTag
import app.cameraconnect.discovery.upnp.XmlDom.qualifiedName
import java.net.URI
import org.w3c.dom.Element

/** Parses a UPnP device description document (the XML behind the SSDP LOCATION header). */
object DeviceDescriptionParser {

    private val standardDeviceChildren = setOf(
        "deviceType", "friendlyName", "manufacturer", "manufacturerURL", "modelDescription",
        "modelName", "modelNumber", "modelURL", "serialNumber", "UDN", "UPC", "iconList",
        "serviceList", "deviceList", "presentationURL",
    )

    /**
     * @param locationUrl the URL the document was fetched from; relative service URLs are
     * resolved against the document's URLBase when present, otherwise against this URL.
     */
    fun parse(xml: String, locationUrl: String): DeviceDescription {
        val root = XmlDom.parse(xml).documentElement
            ?: throw UpnpParseException("Empty document")
        val urlBase = root.childText("URLBase")
        val base = urlBase ?: locationUrl
        val device = root.firstChild("device")
            ?: throw UpnpParseException("No <device> element in description")
        return parseDevice(device, base, urlBase)
    }

    private fun parseDevice(element: Element, base: String, urlBase: String?): DeviceDescription {
        val services = element.firstChild("serviceList")?.childElements().orEmpty()
            .filter { it.localNameOrTag == "service" }
            .mapNotNull { parseService(it, base) }
        val embedded = element.firstChild("deviceList")?.childElements().orEmpty()
            .filter { it.localNameOrTag == "device" }
            .map { parseDevice(it, base, urlBase) }
        val extras = element.childElements()
            .filter { it.localNameOrTag !in standardDeviceChildren }
            .associate { it.qualifiedName to it.textContent.trim().replace(Regex("\\s+"), " ") }
        return DeviceDescription(
            deviceType = element.childText("deviceType"),
            friendlyName = element.childText("friendlyName"),
            manufacturer = element.childText("manufacturer"),
            modelName = element.childText("modelName"),
            modelDescription = element.childText("modelDescription"),
            modelNumber = element.childText("modelNumber"),
            serialNumber = element.childText("serialNumber"),
            udn = element.childText("UDN"),
            presentationUrl = resolveUrl(base, element.childText("presentationURL")),
            urlBase = urlBase,
            services = services,
            embeddedDevices = embedded,
            extras = extras,
        )
    }

    private fun parseService(element: Element, base: String): UpnpService? {
        val serviceType = element.childText("serviceType") ?: return null
        return UpnpService(
            serviceType = serviceType,
            serviceId = element.childText("serviceId"),
            scpdUrl = resolveUrl(base, element.childText("SCPDURL")),
            controlUrl = resolveUrl(base, element.childText("controlURL")),
            eventSubUrl = resolveUrl(base, element.childText("eventSubURL")),
        )
    }

    fun resolveUrl(base: String, reference: String?): String? {
        val ref = reference?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { URI(base).resolve(ref).toString() }.getOrElse { ref }
    }
}
