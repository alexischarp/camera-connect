package app.cameraconnect.network

import java.net.InetAddress

/**
 * Decides which hosts the camera HTTP client may talk to. Only private (RFC 1918) and link-local
 * addresses are accepted, which is the runtime counterpart of "cleartext only towards the camera".
 */
object LocalAddressPolicy {

    private val ipv4Literal = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    fun isAllowed(address: InetAddress): Boolean =
        address.isSiteLocalAddress || address.isLinkLocalAddress

    /** Parses an IP literal without triggering a DNS lookup; returns null for host names. */
    fun parseLiteral(host: String): InetAddress? {
        val candidate = host.removePrefix("[").removeSuffix("]")
        val isLiteral = ipv4Literal.matches(candidate) || candidate.contains(':')
        if (!isLiteral) return null
        return runCatching { InetAddress.getByName(candidate) }.getOrNull()
    }
}
