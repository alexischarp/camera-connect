package app.cameraconnect.camera

import app.cameraconnect.discovery.DiscoveryLogger
import app.cameraconnect.discovery.ssdp.SsdpClient
import app.cameraconnect.discovery.upnp.DeviceDescriptionParser
import app.cameraconnect.discovery.upnp.UpnpParseException
import app.cameraconnect.network.CameraHttpClient
import app.cameraconnect.wifi.CameraConnectionState
import app.cameraconnect.wifi.CameraLink
import app.cameraconnect.wifi.CameraWifiConnector
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Turns a connected camera Wi-Fi link into a [CameraSession] by locating the camera's UPnP
 * MediaServer: an SSDP search that stops at the first answer, then the device description it
 * points to.
 */
class CameraSessionManager(
    scope: CoroutineScope,
    private val connector: CameraWifiConnector,
    private val ssdpClient: SsdpClient,
    private val http: OkHttpClient,
    private val logger: DiscoveryLogger,
) {

    private val _state = MutableStateFlow<CameraSessionState>(CameraSessionState.NoNetwork)
    val state: StateFlow<CameraSessionState> = _state.asStateFlow()

    private val retries = MutableStateFlow(0)

    init {
        scope.launch {
            connector.state
                .distinctUntilChangedBy { (it as? CameraConnectionState.Connected)?.link?.network }
                .collectLatest { connection ->
                    if (connection !is CameraConnectionState.Connected) {
                        _state.value = CameraSessionState.NoNetwork
                        return@collectLatest
                    }
                    retries.collectLatest { discover(latestLink(connection)) }
                }
        }
    }

    fun retry() {
        retries.value++
    }

    /** The link as last reported for [connection]'s network: DHCP details can arrive after the first report. */
    private fun latestLink(connection: CameraConnectionState.Connected): CameraLink =
        (connector.state.value as? CameraConnectionState.Connected)?.link
            ?.takeIf { it.network == connection.link.network }
            ?: connection.link

    private suspend fun discover(link: CameraLink) {
        _state.value = CameraSessionState.Discovering
        val responses = try {
            ssdpClient.search(
                link = link,
                searchTargets = SEARCH_TARGETS,
                unicastTargets = link.candidateHosts,
                listenMillis = SSDP_LISTEN_MS,
                userAgent = CameraHttpClient.USER_AGENT,
                stopWhen = { it.location != null },
            )
        } catch (e: SsdpClient.CameraNetworkBlockedException) {
            logger.w(TAG, "The camera network cannot be used (VPN active?): ${e.message}")
            _state.value = CameraSessionState.Failed(CameraSessionState.FailureReason.NETWORK_BLOCKED)
            return
        }
        val locations = responses.mapNotNull { it.message.location }.distinct()
        if (locations.isEmpty()) {
            logger.w(TAG, "No UPnP device answered on the camera network")
            _state.value = CameraSessionState.Failed(CameraSessionState.FailureReason.NO_SSDP_ANSWER)
            return
        }
        var described = false
        for (location in locations) {
            val description = fetchDescription(location) ?: continue
            described = true
            val contentDirectory = description.findService("ContentDirectory") ?: continue
            logger.i(TAG, "Camera session ready: ${description.friendlyName} via ${contentDirectory.controlUrl}")
            _state.value = CameraSessionState.Ready(CameraSession(link, location, description, contentDirectory))
            return
        }
        _state.value = CameraSessionState.Failed(
            if (described) CameraSessionState.FailureReason.NO_CONTENT_DIRECTORY
            else CameraSessionState.FailureReason.NO_DESCRIPTION,
        )
    }

    private suspend fun fetchDescription(url: String) = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(url).header("User-Agent", CameraHttpClient.USER_AGENT).build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    logger.w(TAG, "GET $url returned HTTP ${response.code}")
                    return@withContext null
                }
                DeviceDescriptionParser.parse(response.body.string(), url)
            }
        } catch (e: IOException) {
            logger.w(TAG, "GET $url failed: $e")
            null
        } catch (e: UpnpParseException) {
            logger.w(TAG, "Cannot parse $url: ${e.message}")
            null
        } catch (e: IllegalArgumentException) {
            logger.w(TAG, "Invalid LOCATION $url")
            null
        }
    }

    private companion object {
        const val TAG = "Session"
        /**
         * Upper bound only: the search stops at the first LOCATION. Long enough for a camera that
         * starts its UPnP server a few seconds after the Wi-Fi association.
         */
        const val SSDP_LISTEN_MS = 8_000L
        val SEARCH_TARGETS = listOf(
            "urn:schemas-upnp-org:device:MediaServer:1",
            "ssdp:all",
        )
    }
}
