package app.cameraconnect.discovery.ptpip

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/** PTP/IP (ISO 15740 over TCP) packet types and codes used by the probe. */
object PtpIp {
    const val DEFAULT_PORT = 15740
    const val PROTOCOL_VERSION = 0x00010000
    const val HEADER_SIZE = 8
    const val MAX_PACKET_SIZE = 8 * 1024 * 1024

    object PacketType {
        const val INIT_COMMAND_REQUEST = 1
        const val INIT_COMMAND_ACK = 2
        const val INIT_EVENT_REQUEST = 3
        const val INIT_EVENT_ACK = 4
        const val INIT_FAIL = 5
        const val OPERATION_REQUEST = 6
        const val OPERATION_RESPONSE = 7
        const val EVENT = 8
        const val START_DATA = 9
        const val DATA = 10
        const val CANCEL = 11
        const val END_DATA = 12
        const val PROBE_REQUEST = 13
        const val PROBE_RESPONSE = 14

        fun name(type: Int): String = when (type) {
            INIT_COMMAND_REQUEST -> "InitCommandRequest"
            INIT_COMMAND_ACK -> "InitCommandAck"
            INIT_EVENT_REQUEST -> "InitEventRequest"
            INIT_EVENT_ACK -> "InitEventAck"
            INIT_FAIL -> "InitFail"
            OPERATION_REQUEST -> "OperationRequest"
            OPERATION_RESPONSE -> "OperationResponse"
            EVENT -> "Event"
            START_DATA -> "StartData"
            DATA -> "Data"
            CANCEL -> "Cancel"
            END_DATA -> "EndData"
            PROBE_REQUEST -> "ProbeRequest"
            PROBE_RESPONSE -> "ProbeResponse"
            else -> "Unknown($type)"
        }
    }

    object Operation {
        const val GET_DEVICE_INFO = 0x1001
    }

    object ResponseCode {
        const val OK = 0x2001
    }

    /** Data phase values of an OperationRequest packet. */
    const val DATA_PHASE_NONE_OR_IN = 1
    const val DATA_PHASE_OUT = 2
}

data class PtpIpPacket(val type: Int, val payload: ByteArray) {
    val typeName: String get() = PtpIp.PacketType.name(type)
}

data class InitCommandAck(
    val connectionNumber: Int,
    val guid: ByteArray,
    val friendlyName: String,
    val protocolVersion: Int,
)

data class OperationResponse(val responseCode: Int, val transactionId: Int, val parameters: List<Int>)

data class StartData(val transactionId: Int, val totalLength: Long)

data class DataChunk(val transactionId: Int, val bytes: ByteArray)

data class PtpDeviceInfo(
    val standardVersion: Int,
    val vendorExtensionId: Long,
    val vendorExtensionVersion: Int,
    val vendorExtensionDesc: String,
    val functionalMode: Int,
    val operationsSupported: List<Int>,
    val eventsSupported: List<Int>,
    val devicePropertiesSupported: List<Int>,
    val captureFormats: List<Int>,
    val imageFormats: List<Int>,
    val manufacturer: String,
    val model: String,
    val deviceVersion: String,
    val serialNumber: String,
) {
    fun summary(): String = buildString {
        appendLine("manufacturer=$manufacturer model=$model version=$deviceVersion serial=$serialNumber")
        appendLine("standardVersion=$standardVersion vendorExtensionId=0x${vendorExtensionId.toString(16)} " +
            "vendorExtensionVersion=$vendorExtensionVersion desc=\"$vendorExtensionDesc\" functionalMode=$functionalMode")
        appendLine("operations(${operationsSupported.size})=" + operationsSupported.joinToString { hex16(it) })
        appendLine("events(${eventsSupported.size})=" + eventsSupported.joinToString { hex16(it) })
        appendLine("properties(${devicePropertiesSupported.size})=" + devicePropertiesSupported.joinToString { hex16(it) })
        appendLine("captureFormats=" + captureFormats.joinToString { hex16(it) })
        append("imageFormats=" + imageFormats.joinToString { hex16(it) })
    }

    private fun hex16(value: Int): String = "0x" + value.toString(16).padStart(4, '0')
}

/** Encodes and decodes PTP/IP packets. All integers are little-endian. */
object PtpIpCodec {

    fun encode(type: Int, payload: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(PtpIp.HEADER_SIZE + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(PtpIp.HEADER_SIZE + payload.size)
        buffer.putInt(type)
        buffer.put(payload)
        return buffer.array()
    }

    /** Returns (total length, type) read from an 8-byte header. */
    fun decodeHeader(header: ByteArray): Pair<Int, Int> {
        require(header.size >= PtpIp.HEADER_SIZE) { "Header too short" }
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        return buffer.int to buffer.int
    }

    fun initCommandRequest(guid: UUID, friendlyName: String, protocolVersion: Int = PtpIp.PROTOCOL_VERSION): ByteArray {
        val name = utf16NullTerminated(friendlyName)
        val buffer = ByteBuffer.allocate(16 + name.size + 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(uuidToBytes(guid))
        buffer.put(name)
        buffer.putInt(protocolVersion)
        return encode(PtpIp.PacketType.INIT_COMMAND_REQUEST, buffer.array())
    }

    fun parseInitCommandAck(payload: ByteArray): InitCommandAck {
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val connectionNumber = buffer.int
        val guid = ByteArray(16).also { buffer.get(it) }
        val name = readUtf16NullTerminated(buffer)
        val version = if (buffer.remaining() >= 4) buffer.int else 0
        return InitCommandAck(connectionNumber, guid, name, version)
    }

    fun parseInitFail(payload: ByteArray): Int =
        ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).int

    fun initEventRequest(connectionNumber: Int): ByteArray {
        val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(connectionNumber)
        return encode(PtpIp.PacketType.INIT_EVENT_REQUEST, buffer.array())
    }

    fun operationRequest(
        operationCode: Int,
        transactionId: Int,
        parameters: IntArray = IntArray(0),
        dataPhase: Int = PtpIp.DATA_PHASE_NONE_OR_IN,
    ): ByteArray {
        val buffer = ByteBuffer.allocate(4 + 2 + 4 + parameters.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(dataPhase)
        buffer.putShort(operationCode.toShort())
        buffer.putInt(transactionId)
        parameters.forEach { buffer.putInt(it) }
        return encode(PtpIp.PacketType.OPERATION_REQUEST, buffer.array())
    }

    fun parseOperationResponse(payload: ByteArray): OperationResponse {
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val code = buffer.short.toInt() and 0xFFFF
        val transactionId = buffer.int
        val parameters = mutableListOf<Int>()
        while (buffer.remaining() >= 4) parameters += buffer.int
        return OperationResponse(code, transactionId, parameters)
    }

    fun parseStartData(payload: ByteArray): StartData {
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        return StartData(buffer.int, buffer.long)
    }

    fun parseDataChunk(payload: ByteArray): DataChunk {
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val transactionId = buffer.int
        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
        return DataChunk(transactionId, bytes)
    }

    /** Parses the DeviceInfo dataset returned by GetDeviceInfo (PTP 1.1 §5.5.1). */
    fun parseDeviceInfo(data: ByteArray): PtpDeviceInfo {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val standardVersion = buffer.short.toInt() and 0xFFFF
        val vendorExtensionId = buffer.int.toLong() and 0xFFFFFFFFL
        val vendorExtensionVersion = buffer.short.toInt() and 0xFFFF
        val vendorExtensionDesc = readPtpString(buffer)
        val functionalMode = buffer.short.toInt() and 0xFFFF
        val operations = readUint16Array(buffer)
        val events = readUint16Array(buffer)
        val properties = readUint16Array(buffer)
        val captureFormats = readUint16Array(buffer)
        val imageFormats = readUint16Array(buffer)
        val manufacturer = readPtpString(buffer)
        val model = readPtpString(buffer)
        val deviceVersion = readPtpString(buffer)
        val serialNumber = readPtpString(buffer)
        return PtpDeviceInfo(
            standardVersion, vendorExtensionId, vendorExtensionVersion, vendorExtensionDesc, functionalMode,
            operations, events, properties, captureFormats, imageFormats,
            manufacturer, model, deviceVersion, serialNumber,
        )
    }

    fun uuidToBytes(uuid: UUID): ByteArray =
        ByteBuffer.allocate(16).putLong(uuid.mostSignificantBits).putLong(uuid.leastSignificantBits).array()

    fun bytesToUuid(bytes: ByteArray): UUID {
        val buffer = ByteBuffer.wrap(bytes)
        return UUID(buffer.long, buffer.long)
    }

    private fun utf16NullTerminated(value: String): ByteArray {
        val chars = value.toByteArray(Charsets.UTF_16LE)
        return chars + byteArrayOf(0, 0)
    }

    private fun readUtf16NullTerminated(buffer: ByteBuffer): String {
        val builder = StringBuilder()
        while (buffer.remaining() >= 2) {
            val unit = buffer.short.toInt() and 0xFFFF
            if (unit == 0) break
            builder.append(unit.toChar())
        }
        return builder.toString()
    }

    /** PTP string: uint8 character count (including the terminating null) followed by UTF-16LE units. */
    private fun readPtpString(buffer: ByteBuffer): String {
        if (!buffer.hasRemaining()) return ""
        val count = buffer.get().toInt() and 0xFF
        if (count == 0) return ""
        val builder = StringBuilder()
        repeat(count) {
            if (buffer.remaining() < 2) return builder.toString()
            val unit = buffer.short.toInt() and 0xFFFF
            if (unit != 0) builder.append(unit.toChar())
        }
        return builder.toString()
    }

    private fun readUint16Array(buffer: ByteBuffer): List<Int> {
        if (buffer.remaining() < 4) return emptyList()
        val count = buffer.int
        val result = ArrayList<Int>(count.coerceIn(0, 4096))
        repeat(count) {
            if (buffer.remaining() < 2) return result
            result += buffer.short.toInt() and 0xFFFF
        }
        return result
    }
}
