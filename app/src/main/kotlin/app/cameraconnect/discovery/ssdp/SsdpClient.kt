package app.cameraconnect.discovery.ssdp

import android.net.wifi.WifiManager
import app.cameraconnect.discovery.DiscoveryLogger
import app.cameraconnect.wifi.CameraLink
import java.io.IOException
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sends SSDP M-SEARCH requests on the camera network and collects every datagram that comes back.
 *
 * The requests leave from a socket bound to an ephemeral port, so their unicast answers can only
 * land in that socket: another app sharing port 1900 with SO_REUSEADDR would otherwise be able
 * to swallow them. A second socket on port 1900, joined to the SSDP group, catches the NOTIFY
 * advertisements the camera sends on its own. The request burst is repeated every second until
 * the deadline, or until [search]'s `stopWhen` is satisfied: the first datagrams after the Wi-Fi
 * association are easily lost, and a camera may start its UPnP server a few seconds late.
 *
 * Besides the standard multicast search, the same request is unicast to the addresses the link
 * suggests for the camera (DHCP server, gateway...) because some phones silently drop outgoing
 * multicast on peer-to-peer style networks.
 */
class SsdpClient(private val logger: DiscoveryLogger, private val wifiManager: WifiManager?) {

    /**
     * The system refused to bind a socket to the camera network (`EPERM` on `Network.bindSocket`).
     * Android does this for every app while a VPN is active, so no datagram can reach the camera.
     */
    class CameraNetworkBlockedException(cause: IOException) :
        IOException("Cannot bind a socket to the camera network: ${cause.message}", cause)

    data class Response(
        val from: InetAddress,
        val port: Int,
        val message: SsdpMessage,
        val raw: String,
    )

    /**
     * @throws CameraNetworkBlockedException when no socket can be bound to the camera network,
     * typically because a VPN is active on the phone.
     */
    suspend fun search(
        link: CameraLink,
        searchTargets: List<String>,
        unicastTargets: List<InetAddress>,
        listenMillis: Long,
        userAgent: String,
        /** Stops listening early as soon as a received message satisfies it. */
        stopWhen: ((SsdpMessage) -> Boolean)? = null,
    ): List<Response> = withMulticastLock {
        searchInternal(link, searchTargets, unicastTargets, listenMillis, userAgent, stopWhen)
    }

    private suspend fun searchInternal(
        link: CameraLink,
        searchTargets: List<String>,
        unicastTargets: List<InetAddress>,
        listenMillis: Long,
        userAgent: String,
        stopWhen: ((SsdpMessage) -> Boolean)?,
    ): List<Response> = withContext(Dispatchers.IO) {
        val group = InetAddress.getByName(SsdpMessageParser.MULTICAST_ADDRESS)
        val networkInterface = link.interfaceName?.let { name ->
            runCatching { NetworkInterface.getByName(name) }.getOrNull()
        }
        if (networkInterface == null) {
            logger.w(TAG, "Interface ${link.interfaceName} not found; multicast will use the default route")
        }
        val searchSocket = try {
            openSearchSocket(link, networkInterface)
        } catch (e: IOException) {
            logger.e(TAG, "Cannot open SSDP socket on the camera network: $e")
            throw CameraNetworkBlockedException(e)
        }
        val notifySocket = openNotifySocket(group, networkInterface)
        logger.i(
            TAG,
            "SSDP search socket on ${searchSocket.localAddress?.hostAddress}:${searchSocket.localPort} " +
                "via ${networkInterface?.name ?: "default interface"}; NOTIFY listener " +
                if (notifySocket != null) "on port ${notifySocket.localPort}" else "unavailable",
        )

        val inbox = Inbox(stopWhen)
        val started = System.currentTimeMillis()
        try {
            coroutineScope {
                val workers = buildList {
                    add(launch { receiveLoop(searchSocket, inbox) })
                    if (notifySocket != null) add(launch { receiveLoop(notifySocket, inbox) })
                    add(launch { sendRounds(searchSocket, group, searchTargets, unicastTargets, userAgent) })
                }
                withTimeoutOrNull(listenMillis) { inbox.stopped.await() }
                workers.forEach { it.cancel() }
            }
        } finally {
            searchSocket.close()
            notifySocket?.close()
        }
        val responses = inbox.responses()
        logger.i(
            TAG,
            "SSDP listening finished after ${System.currentTimeMillis() - started} ms: " +
                "${responses.size} unique message(s)",
        )
        responses
    }

    /** Receives on [socket] until the coroutine is cancelled or the socket is closed. */
    private fun CoroutineScope.receiveLoop(socket: MulticastSocket, inbox: Inbox) {
        val buffer = ByteArray(16 * 1024)
        socket.soTimeout = RECEIVE_POLL_MS
        while (isActive) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
            } catch (e: SocketTimeoutException) {
                continue
            } catch (e: IOException) {
                if (!socket.isClosed) logger.e(TAG, "receive failed: $e")
                return
            }
            val raw = String(packet.data, packet.offset, packet.length, Charsets.ISO_8859_1)
            val message = SsdpMessageParser.parse(raw)
            if (message == null) {
                logger.w(TAG, "Unparseable datagram from ${packet.address.hostAddress}")
                continue
            }
            if (message.isSearchRequest) continue // our own request or another client's
            if (!inbox.offer(Response(packet.address, packet.port, message, raw))) continue
            val kind = if (message.isSearchResponse) "M-SEARCH response" else message.startLine
            logger.i(TAG, "$kind from ${packet.address.hostAddress}:${packet.port}\n${raw.trim()}")
            if (inbox.stopped.isCompleted) {
                logger.d(TAG, "Stopping SSDP listening early: a matching message arrived")
            }
        }
    }

    /** Sends the whole M-SEARCH burst, then again every [RESEND_INTERVAL_MS], until cancelled. */
    private suspend fun sendRounds(
        socket: MulticastSocket,
        group: InetAddress,
        searchTargets: List<String>,
        unicastTargets: List<InetAddress>,
        userAgent: String,
    ) {
        val multicastHost = "${SsdpMessageParser.MULTICAST_ADDRESS}:${SsdpMessageParser.PORT}"
        var round = 0
        while (true) {
            round++
            var sent = 0
            var failed = 0
            var lastFailure: IOException? = null
            fun attempt(payload: String, target: InetAddress) {
                val failure = send(socket, payload, target)
                if (failure == null) sent++ else { failed++; lastFailure = failure }
            }
            for (searchTarget in searchTargets) {
                val payload = SsdpMessageParser.buildSearchRequest(multicastHost, searchTarget, MX_SECONDS, userAgent)
                attempt(payload, group)
                for (target in unicastTargets) {
                    // UPnP 1.1 puts the device's own address in HOST for a unicast search, but
                    // some stacks only accept the multicast one: send both spellings.
                    val unicastHost = "${target.hostAddress}:${SsdpMessageParser.PORT}"
                    attempt(SsdpMessageParser.buildSearchRequest(unicastHost, searchTarget, MX_SECONDS, userAgent), target)
                    attempt(payload, target)
                }
            }
            val destinations = (listOf("multicast") + unicastTargets.map { it.hostAddress.orEmpty() }).joinToString()
            val outcome = if (failed == 0) "$sent sent" else "$sent sent, $failed failed (last: $lastFailure)"
            logger.d(TAG, "M-SEARCH round $round: ${searchTargets.size} search target(s) to $destinations, $outcome")
            delay(RESEND_INTERVAL_MS)
        }
    }

    /** Ephemeral port: the unicast answers to our requests can only arrive here. */
    private fun openSearchSocket(link: CameraLink, networkInterface: NetworkInterface?): MulticastSocket {
        val socket = MulticastSocket(null)
        try {
            socket.bind(InetSocketAddress(0))
            // Routes every datagram through the camera network, whatever the process default network is.
            link.network.bindSocket(socket)
        } catch (e: IOException) {
            socket.close()
            throw e
        }
        if (networkInterface != null) {
            runCatching { socket.networkInterface = networkInterface }
                .onFailure { logger.w(TAG, "setNetworkInterface failed: $it") }
        }
        socket.timeToLive = 2
        return socket
    }

    /** Port 1900 joined to the SSDP group, to hear NOTIFY advertisements. Best effort: null when unavailable. */
    private fun openNotifySocket(group: InetAddress, networkInterface: NetworkInterface?): MulticastSocket? {
        if (networkInterface == null) {
            logger.w(TAG, "Unknown interface: NOTIFY advertisements will be missed")
            return null
        }
        val socket = MulticastSocket(null)
        try {
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(SsdpMessageParser.PORT))
            socket.joinGroup(InetSocketAddress(group, SsdpMessageParser.PORT), networkInterface)
        } catch (e: IOException) {
            logger.w(TAG, "Cannot listen on port ${SsdpMessageParser.PORT} (NOTIFY advertisements will be missed): $e")
            socket.close()
            return null
        }
        return socket
    }

    /** Sends one datagram; returns the failure, or null when it left. */
    private fun send(socket: MulticastSocket, payload: String, target: InetAddress): IOException? {
        val bytes = payload.toByteArray(Charsets.US_ASCII)
        return try {
            socket.send(DatagramPacket(bytes, bytes.size, target, SsdpMessageParser.PORT))
            null
        } catch (e: IOException) {
            if (e.message?.contains("EPERM") == true && !permissionHintLogged) {
                permissionHintLogged = true
                logger.e(TAG, "EPERM on send: the system blocks this app's local-network traffic (ACCESS_LOCAL_NETWORK permission missing or denied)")
            }
            e
        }
    }

    @Volatile
    private var permissionHintLogged = false

    /** Datagrams received so far, deduplicated; shared by the receive loops. */
    private class Inbox(private val stopWhen: ((SsdpMessage) -> Boolean)?) {
        private val lock = Any()
        private val responses = LinkedHashMap<String, Response>()

        /** Completed as soon as a received message satisfies [stopWhen]. */
        val stopped = CompletableDeferred<Unit>()

        /** Records [response]; false when the same message was already received. */
        fun offer(response: Response): Boolean {
            val message = response.message
            val key = "${response.from.hostAddress}|${message.startLine}|${message.usn}|${message.location}"
            synchronized(lock) {
                if (responses.containsKey(key)) return false
                responses[key] = response
            }
            if (stopWhen != null && stopWhen(message)) stopped.complete(Unit)
            return true
        }

        fun responses(): List<Response> = synchronized(lock) { responses.values.toList() }
    }

    /** Phones drop incoming multicast unless a [WifiManager.MulticastLock] is held. */
    private suspend fun <T> withMulticastLock(block: suspend () -> T): T {
        val lock = wifiManager?.createMulticastLock("cameraconnect-ssdp")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        if (lock == null) logger.w(TAG, "WifiManager unavailable: multicast lock not acquired")
        try {
            return block()
        } finally {
            lock?.takeIf { it.isHeld }?.release()
        }
    }

    private companion object {
        const val TAG = "Ssdp"
        const val MX_SECONDS = 2
        const val RECEIVE_POLL_MS = 250
        const val RESEND_INTERVAL_MS = 1_000L
    }
}
