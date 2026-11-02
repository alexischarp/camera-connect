package app.cameraconnect.discovery.ptpip

import app.cameraconnect.discovery.DiscoveryLogger
import app.cameraconnect.wifi.CameraLink
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PtpIpProbeResult(
    val host: String,
    val port: Int,
    val tcpConnected: Boolean = false,
    val commandAck: InitCommandAck? = null,
    val initFailReason: Int? = null,
    val eventChannelAcknowledged: Boolean = false,
    val deviceInfo: PtpDeviceInfo? = null,
    val error: String? = null,
) {
    val handshakeSucceeded: Boolean get() = commandAck != null
}

/**
 * Checks whether a PTP/IP responder (the protocol behind Sony's "PC Remote" mode) answers on the
 * camera. It performs the two-channel handshake and asks for the DeviceInfo dataset, which lists
 * the model name and every supported operation.
 */
class PtpIpProbe(private val logger: DiscoveryLogger) {

    suspend fun probe(
        link: CameraLink,
        host: InetAddress,
        port: Int = PtpIp.DEFAULT_PORT,
        guid: UUID,
        friendlyName: String,
    ): PtpIpProbeResult = withContext(Dispatchers.IO) {
        var result = PtpIpProbeResult(host.hostAddress.orEmpty(), port)
        val command = link.network.socketFactory.createSocket()
        var event: Socket? = null
        try {
            command.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            command.soTimeout = READ_TIMEOUT_MS
            result = result.copy(tcpConnected = true)
            logger.i(TAG, "TCP connection established to ${host.hostAddress}:$port")

            val output = command.getOutputStream()
            val input = DataInputStream(BufferedInputStream(command.getInputStream()))

            send(output, PtpIpCodec.initCommandRequest(guid, friendlyName), "InitCommandRequest(guid=$guid, name=$friendlyName)")
            val ackPacket = readPacket(input)
            when (ackPacket.type) {
                PtpIp.PacketType.INIT_COMMAND_ACK -> {
                    val ack = PtpIpCodec.parseInitCommandAck(ackPacket.payload)
                    logger.i(
                        TAG,
                        "InitCommandAck: connection=${ack.connectionNumber} name=\"${ack.friendlyName}\" " +
                            "guid=${PtpIpCodec.bytesToUuid(ack.guid)} version=0x${ack.protocolVersion.toString(16)}",
                    )
                    result = result.copy(commandAck = ack)
                }
                PtpIp.PacketType.INIT_FAIL -> {
                    val reason = PtpIpCodec.parseInitFail(ackPacket.payload)
                    logger.w(TAG, "InitFail: reason=0x${reason.toString(16)} (camera refused the initiator)")
                    return@withContext result.copy(initFailReason = reason)
                }
                else -> {
                    logger.w(TAG, "Unexpected packet ${ackPacket.typeName} after InitCommandRequest")
                    return@withContext result.copy(error = "Unexpected ${ackPacket.typeName}")
                }
            }
            val ack = result.commandAck ?: return@withContext result

            val eventSocket = link.network.socketFactory.createSocket()
            event = eventSocket
            eventSocket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            eventSocket.soTimeout = READ_TIMEOUT_MS
            send(eventSocket.getOutputStream(), PtpIpCodec.initEventRequest(ack.connectionNumber), "InitEventRequest(connection=${ack.connectionNumber})")
            val eventInput = DataInputStream(BufferedInputStream(eventSocket.getInputStream()))
            val eventAck = readPacket(eventInput)
            when (eventAck.type) {
                PtpIp.PacketType.INIT_EVENT_ACK -> {
                    logger.i(TAG, "InitEventAck received: event channel open")
                    result = result.copy(eventChannelAcknowledged = true)
                }
                PtpIp.PacketType.INIT_FAIL -> {
                    val reason = PtpIpCodec.parseInitFail(eventAck.payload)
                    logger.w(TAG, "Event channel InitFail: reason=0x${reason.toString(16)}")
                }
                else -> logger.w(TAG, "Unexpected packet ${eventAck.typeName} on event channel")
            }

            send(output, PtpIpCodec.operationRequest(PtpIp.Operation.GET_DEVICE_INFO, transactionId = 0), "GetDeviceInfo")
            val data = java.io.ByteArrayOutputStream()
            var responseCode: Int? = null
            var packets = 0
            while (responseCode == null && packets < MAX_PACKETS_PER_OPERATION) {
                val packet = readPacket(input)
                packets++
                when (packet.type) {
                    PtpIp.PacketType.START_DATA -> {
                        val start = PtpIpCodec.parseStartData(packet.payload)
                        logger.d(TAG, "StartData: transaction=${start.transactionId} totalLength=${start.totalLength}")
                    }
                    PtpIp.PacketType.DATA, PtpIp.PacketType.END_DATA -> {
                        val chunk = PtpIpCodec.parseDataChunk(packet.payload)
                        data.write(chunk.bytes)
                        logger.d(TAG, "${packet.typeName}: ${chunk.bytes.size} byte(s)")
                    }
                    PtpIp.PacketType.OPERATION_RESPONSE -> {
                        val response = PtpIpCodec.parseOperationResponse(packet.payload)
                        responseCode = response.responseCode
                        logger.i(TAG, "OperationResponse: code=0x${response.responseCode.toString(16)} transaction=${response.transactionId} params=${response.parameters}")
                    }
                    else -> logger.w(TAG, "Ignoring packet ${packet.typeName} (${packet.payload.size} bytes)")
                }
            }
            if (responseCode == PtpIp.ResponseCode.OK && data.size() > 0) {
                val info = runCatching { PtpIpCodec.parseDeviceInfo(data.toByteArray()) }
                    .onFailure { logger.w(TAG, "Cannot parse DeviceInfo: $it") }
                    .getOrNull()
                if (info != null) {
                    logger.i(TAG, "DeviceInfo:\n${info.summary()}")
                    result = result.copy(deviceInfo = info)
                }
            }
            result
        } catch (e: IOException) {
            logger.w(TAG, "PTP/IP probe of ${host.hostAddress}:$port failed: $e")
            result.copy(error = e.toString())
        } finally {
            runCatching { command.close() }
            runCatching { event?.close() }
        }
    }

    private fun send(output: OutputStream, packet: ByteArray, description: String) {
        output.write(packet)
        output.flush()
        logger.d(TAG, "Sent $description (${packet.size} bytes)")
    }

    @Throws(IOException::class)
    private fun readPacket(input: DataInputStream): PtpIpPacket {
        val header = ByteArray(PtpIp.HEADER_SIZE)
        input.readFully(header)
        val (length, type) = PtpIpCodec.decodeHeader(header)
        if (length < PtpIp.HEADER_SIZE || length > PtpIp.MAX_PACKET_SIZE) {
            throw IOException("Invalid PTP/IP packet length $length")
        }
        val payload = ByteArray(length - PtpIp.HEADER_SIZE)
        input.readFully(payload)
        return PtpIpPacket(type, payload)
    }

    private companion object {
        const val TAG = "PtpIp"
        const val CONNECT_TIMEOUT_MS = 4_000
        const val READ_TIMEOUT_MS = 6_000
        const val MAX_PACKETS_PER_OPERATION = 64
    }
}
