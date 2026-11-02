package app.cameraconnect.discovery.ssdp

import java.util.Locale

/** One SSDP datagram (M-SEARCH response or NOTIFY advertisement). Header names are upper-cased. */
data class SsdpMessage(
    val startLine: String,
    val headers: Map<String, String>,
) {
    val isSearchResponse: Boolean get() = startLine.startsWith("HTTP/1.1 200", ignoreCase = true)
    val isNotify: Boolean get() = startLine.startsWith("NOTIFY", ignoreCase = true)
    val isSearchRequest: Boolean get() = startLine.startsWith("M-SEARCH", ignoreCase = true)

    val location: String? get() = headers["LOCATION"]
    val searchTarget: String? get() = headers["ST"] ?: headers["NT"]
    val usn: String? get() = headers["USN"]
    val server: String? get() = headers["SERVER"]
    val notificationSubType: String? get() = headers["NTS"]

    fun header(name: String): String? = headers[name.uppercase(Locale.ROOT)]
}

object SsdpMessageParser {

    const val MULTICAST_ADDRESS = "239.255.255.250"
    const val PORT = 1900

    /** Parses a raw datagram; returns null when the first line is empty. */
    fun parse(raw: String): SsdpMessage? {
        val lines = raw.split("\r\n", "\n")
        val startLine = lines.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val headers = LinkedHashMap<String, String>()
        for (line in lines.drop(1)) {
            if (line.isBlank()) break
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            val name = line.substring(0, separator).trim().uppercase(Locale.ROOT)
            val value = line.substring(separator + 1).trim()
            headers[name] = value
        }
        return SsdpMessage(startLine, headers)
    }

    /** Builds an M-SEARCH request. [host] is "address:port" as it must appear in the HOST header. */
    fun buildSearchRequest(host: String, searchTarget: String, mxSeconds: Int, userAgent: String): String =
        buildString {
            append("M-SEARCH * HTTP/1.1\r\n")
            append("HOST: ").append(host).append("\r\n")
            append("MAN: \"ssdp:discover\"\r\n")
            append("MX: ").append(mxSeconds).append("\r\n")
            append("ST: ").append(searchTarget).append("\r\n")
            append("USER-AGENT: ").append(userAgent).append("\r\n")
            append("\r\n")
        }
}
