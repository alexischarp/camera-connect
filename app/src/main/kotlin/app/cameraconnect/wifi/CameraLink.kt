package app.cameraconnect.wifi

import android.net.LinkProperties
import android.net.Network
import android.os.Build
import java.net.Inet4Address
import java.net.InetAddress

data class LocalAddress(val address: InetAddress, val prefixLength: Int)

/**
 * Everything the discovery layer needs to know about the camera's Wi-Fi network: the [Network]
 * to bind sockets to and the addresses learned from DHCP. Nothing here is hard-coded; the camera
 * address candidates are derived from what the link reports.
 */
data class CameraLink(
    val network: Network,
    val interfaceName: String?,
    val localAddresses: List<LocalAddress>,
    val gateways: List<InetAddress>,
    val dhcpServer: InetAddress?,
    val dnsServers: List<InetAddress>,
) {

    /** IPv4 hosts that most likely are the camera, most likely first. Used only as SSDP fallback. */
    val candidateHosts: List<Inet4Address>
        get() = buildList {
            dhcpServer?.let { add(it) }
            addAll(gateways)
            addAll(dnsServers)
            localAddresses.forEach { local ->
                val v4 = local.address as? Inet4Address ?: return@forEach
                SubnetMath.firstHostOfSubnet(v4, local.prefixLength)?.let { add(it) }
            }
        }.filterIsInstance<Inet4Address>().distinct()

    val primaryIpv4: Inet4Address?
        get() = localAddresses.map { it.address }.filterIsInstance<Inet4Address>().firstOrNull()

    fun describe(): String = buildString {
        appendLine("interface=${interfaceName ?: "?"}")
        appendLine("local=" + localAddresses.joinToString { "${it.address.hostAddress}/${it.prefixLength}" })
        appendLine("gateways=" + gateways.joinToString { it.hostAddress.orEmpty() })
        appendLine("dhcpServer=${dhcpServer?.hostAddress ?: "-"}")
        appendLine("dns=" + dnsServers.joinToString { it.hostAddress.orEmpty() })
        append("candidates=" + candidateHosts.joinToString { it.hostAddress.orEmpty() })
    }

    companion object {
        fun from(network: Network, linkProperties: LinkProperties?): CameraLink = CameraLink(
            network = network,
            interfaceName = linkProperties?.interfaceName,
            localAddresses = linkProperties?.linkAddresses.orEmpty()
                .map { LocalAddress(it.address, it.prefixLength) },
            gateways = linkProperties?.routes.orEmpty()
                .filter { it.isDefaultRoute }
                .mapNotNull { it.gateway }
                .filterNot { it.isAnyLocalAddress },
            dhcpServer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                linkProperties?.dhcpServerAddress
            } else {
                null
            },
            dnsServers = linkProperties?.dnsServers.orEmpty(),
        )
    }
}
