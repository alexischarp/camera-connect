package app.cameraconnect.discovery.upnp

data class UpnpService(
    val serviceType: String,
    val serviceId: String?,
    val scpdUrl: String?,
    val controlUrl: String?,
    val eventSubUrl: String?,
)

data class DeviceDescription(
    val deviceType: String?,
    val friendlyName: String?,
    val manufacturer: String?,
    val modelName: String?,
    val modelDescription: String?,
    val modelNumber: String?,
    val serialNumber: String?,
    val udn: String?,
    val presentationUrl: String?,
    val urlBase: String?,
    val services: List<UpnpService>,
    val embeddedDevices: List<DeviceDescription>,
    /** Non-standard child elements of <device> (vendor extensions such as Sony's av:X_... blocks). */
    val extras: Map<String, String>,
) {
    /** Services of this device and of every embedded device, depth first. */
    fun allServices(): List<UpnpService> =
        services + embeddedDevices.flatMap { it.allServices() }

    /** First service whose type contains [fragment], e.g. "ContentDirectory". */
    fun findService(fragment: String): UpnpService? =
        allServices().firstOrNull { it.serviceType.contains(fragment, ignoreCase = true) }

    fun summary(indent: String = ""): String = buildString {
        appendLine("${indent}deviceType=$deviceType")
        appendLine("${indent}friendlyName=$friendlyName manufacturer=$manufacturer model=$modelName ($modelNumber)")
        appendLine("${indent}modelDescription=$modelDescription serial=$serialNumber")
        appendLine("${indent}UDN=$udn presentationURL=$presentationUrl URLBase=$urlBase")
        for (service in services) {
            appendLine("${indent}service ${service.serviceType}")
            appendLine("$indent  id=${service.serviceId}")
            appendLine("$indent  SCPD=${service.scpdUrl}")
            appendLine("$indent  control=${service.controlUrl}")
            appendLine("$indent  events=${service.eventSubUrl}")
        }
        for ((name, value) in extras) {
            appendLine("${indent}extra $name=${value.take(300)}")
        }
        for (embedded in embeddedDevices) {
            appendLine("${indent}embedded device:")
            append(embedded.summary("$indent  "))
        }
    }
}
