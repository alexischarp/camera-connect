package app.cameraconnect.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.PatternMatcher
import app.cameraconnect.discovery.DiscoveryLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DisconnectReason { NONE, USER, LOST, UNAVAILABLE, ERROR }

sealed interface CameraConnectionState {
    data class Disconnected(val reason: DisconnectReason = DisconnectReason.NONE) : CameraConnectionState
    data class Connecting(val ssid: String?) : CameraConnectionState
    data class Connected(val ssid: String?, val link: CameraLink) : CameraConnectionState
}

/**
 * Connects to the camera's access point with [WifiNetworkSpecifier] + [ConnectivityManager.requestNetwork].
 *
 * The resulting [Network] is deliberately never made the process default: the camera network has
 * no Internet access and the rest of the phone must keep using mobile data. Only sockets explicitly
 * bound to [CameraLink.network] reach the camera.
 */
class CameraWifiConnector(context: Context, private val logger: DiscoveryLogger) {

    private val connectivity: ConnectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private val _state = MutableStateFlow<CameraConnectionState>(CameraConnectionState.Disconnected())
    val state: StateFlow<CameraConnectionState> = _state.asStateFlow()

    @Volatile
    private var activeCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * Requests the camera network. An empty [ssid] lets Android list every nearby network whose
     * name starts with [DIRECT_SSID_PREFIX] so the user can pick the camera.
     */
    fun connect(ssid: String, passphrase: String) {
        release()
        val requestedSsid = ssid.trim().ifBlank { null }
        val specifier = WifiNetworkSpecifier.Builder().apply {
            if (requestedSsid != null) {
                setSsid(requestedSsid)
            } else {
                setSsidPattern(PatternMatcher(DIRECT_SSID_PREFIX, PatternMatcher.PATTERN_PREFIX))
            }
            if (passphrase.isNotEmpty()) setWpa2Passphrase(passphrase)
        }.build()
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()
        val callback = Callback(requestedSsid)
        activeCallback = callback
        _state.value = CameraConnectionState.Connecting(requestedSsid)
        logger.i(TAG, "Requesting Wi-Fi network ${requestedSsid ?: "matching prefix \"$DIRECT_SSID_PREFIX\""}")
        try {
            connectivity.requestNetwork(request, callback, REQUEST_TIMEOUT_MS)
        } catch (e: RuntimeException) {
            // SecurityException (missing permission) or "too many callbacks" style failures.
            logger.e(TAG, "requestNetwork failed: $e")
            activeCallback = null
            _state.value = CameraConnectionState.Disconnected(DisconnectReason.ERROR)
        }
    }

    fun disconnect() {
        logger.i(TAG, "Releasing camera network on user request")
        release()
        _state.value = CameraConnectionState.Disconnected(DisconnectReason.USER)
    }

    private fun release() {
        val callback = activeCallback ?: return
        activeCallback = null
        runCatching { connectivity.unregisterNetworkCallback(callback) }
    }

    private inner class Callback(private val requestedSsid: String?) : ConnectivityManager.NetworkCallback() {

        override fun onAvailable(network: Network) {
            if (activeCallback !== this) return
            val link = CameraLink.from(network, connectivity.getLinkProperties(network))
            logger.i(TAG, "Camera network available ($network)\n${link.describe()}")
            _state.value = CameraConnectionState.Connected(requestedSsid, link)
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
            if (activeCallback !== this) return
            val link = CameraLink.from(network, linkProperties)
            logger.d(TAG, "Link properties changed\n${link.describe()}")
            _state.value = CameraConnectionState.Connected(requestedSsid, link)
        }

        override fun onLost(network: Network) {
            if (activeCallback !== this) return
            logger.w(TAG, "Camera network lost ($network)")
            release()
            _state.value = CameraConnectionState.Disconnected(DisconnectReason.LOST)
        }

        override fun onUnavailable() {
            if (activeCallback !== this) return
            logger.w(TAG, "Camera network unavailable: request declined, timed out or SSID not found")
            release()
            _state.value = CameraConnectionState.Disconnected(DisconnectReason.UNAVAILABLE)
        }
    }

    companion object {
        private const val TAG = "Wifi"
        private const val REQUEST_TIMEOUT_MS = 60_000
        /** Wi-Fi Direct style access point names, e.g. "DIRECT-xxxx:ILCE-7RM4" on Sony cameras. */
        const val DIRECT_SSID_PREFIX = "DIRECT-"
    }
}
