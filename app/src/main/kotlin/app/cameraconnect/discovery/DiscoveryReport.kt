package app.cameraconnect.discovery

import app.cameraconnect.discovery.ptpip.PtpIpProbeResult
import app.cameraconnect.discovery.ssdp.SsdpClient
import app.cameraconnect.discovery.upnp.DeviceDescription
import app.cameraconnect.discovery.upnp.DidlContainer
import app.cameraconnect.discovery.upnp.Scpd
import app.cameraconnect.discovery.upnp.UpnpService

enum class DiscoveryPhase { IDLE, SSDP, DESCRIPTION, SCPD, BROWSE, PTP_IP, DONE }

data class DescribedDevice(val locationUrl: String, val description: DeviceDescription)

data class KnownContainer(val container: DidlContainer, val depth: Int)

/** Structured outcome of a discovery run, exposed to the debug screen next to the raw log. */
data class DiscoveryReport(
    val running: Boolean = false,
    val phase: DiscoveryPhase = DiscoveryPhase.IDLE,
    val ssdpResponses: List<SsdpClient.Response> = emptyList(),
    val devices: List<DescribedDevice> = emptyList(),
    val contentDirectoryDevice: DeviceDescription? = null,
    val contentDirectory: UpnpService? = null,
    val scpd: Scpd? = null,
    val containers: List<KnownContainer> = emptyList(),
    val ptpIp: List<PtpIpProbeResult> = emptyList(),
)
