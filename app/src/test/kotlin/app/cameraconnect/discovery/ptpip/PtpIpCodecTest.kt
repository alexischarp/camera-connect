package app.cameraconnect.discovery.ptpip

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PtpIpCodecTest {

    private val guid = UUID.fromString("12345678-9abc-def0-1234-56789abcdef0")

    @Test
    fun `init command request has the expected layout`() {
        val packet = PtpIpCodec.initCommandRequest(guid, "AB")

        val buffer = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(packet.size, buffer.int)
        assertEquals(PtpIp.PacketType.INIT_COMMAND_REQUEST, buffer.int)
        val guidBytes = ByteArray(16).also { buffer.get(it) }
        assertArrayEquals(PtpIpCodec.uuidToBytes(guid), guidBytes)
        assertEquals('A'.code, buffer.short.toInt())
        assertEquals('B'.code, buffer.short.toInt())
        assertEquals(0, buffer.short.toInt())
        assertEquals(PtpIp.PROTOCOL_VERSION, buffer.int)
        assertEquals(0, buffer.remaining())
    }

    @Test
    fun `init command ack round trips`() {
        val name = "ILCE-7RM4".toByteArray(Charsets.UTF_16LE) + byteArrayOf(0, 0)
        val payload = ByteBuffer.allocate(4 + 16 + name.size + 4).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(1)
            .put(PtpIpCodec.uuidToBytes(guid))
            .put(name)
            .putInt(PtpIp.PROTOCOL_VERSION)
            .array()

        val ack = PtpIpCodec.parseInitCommandAck(payload)

        assertEquals(1, ack.connectionNumber)
        assertEquals(guid, PtpIpCodec.bytesToUuid(ack.guid))
        assertEquals("ILCE-7RM4", ack.friendlyName)
        assertEquals(PtpIp.PROTOCOL_VERSION, ack.protocolVersion)
    }

    @Test
    fun `operation request and response encode little endian fields`() {
        val request = PtpIpCodec.operationRequest(PtpIp.Operation.GET_DEVICE_INFO, transactionId = 7, parameters = intArrayOf(0x11))
        val buffer = ByteBuffer.wrap(request).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(8 + 4 + 2 + 4 + 4, buffer.int)
        assertEquals(PtpIp.PacketType.OPERATION_REQUEST, buffer.int)
        assertEquals(PtpIp.DATA_PHASE_NONE_OR_IN, buffer.int)
        assertEquals(PtpIp.Operation.GET_DEVICE_INFO, buffer.short.toInt() and 0xFFFF)
        assertEquals(7, buffer.int)
        assertEquals(0x11, buffer.int)

        val responsePayload = ByteBuffer.allocate(2 + 4 + 4).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(0x2001).putInt(7).putInt(42).array()
        val response = PtpIpCodec.parseOperationResponse(responsePayload)
        assertEquals(PtpIp.ResponseCode.OK, response.responseCode)
        assertEquals(7, response.transactionId)
        assertEquals(listOf(42), response.parameters)
    }

    @Test
    fun `parses a DeviceInfo dataset`() {
        val data = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN).apply {
            putShort(100)                      // StandardVersion
            putInt(0x00000011)                 // VendorExtensionID
            putShort(100)                      // VendorExtensionVersion
            putPtpString("Sony PTP Extensions")
            putShort(0)                        // FunctionalMode
            putUint16Array(listOf(0x1001, 0x1002, 0x9201))
            putUint16Array(listOf(0x4002))
            putUint16Array(listOf(0x5001, 0xD200))
            putUint16Array(emptyList())
            putUint16Array(listOf(0x3801, 0xB101))
            putPtpString("Sony Corporation")
            putPtpString("ILCE-7RM4")
            putPtpString("1.20")
            putPtpString("00000000000000001234")
        }.let { buffer -> buffer.array().copyOf(buffer.position()) }

        val info = PtpIpCodec.parseDeviceInfo(data)

        assertEquals(100, info.standardVersion)
        assertEquals(0x11L, info.vendorExtensionId)
        assertEquals("Sony PTP Extensions", info.vendorExtensionDesc)
        assertEquals(listOf(0x1001, 0x1002, 0x9201), info.operationsSupported)
        assertEquals(listOf(0x4002), info.eventsSupported)
        assertEquals(listOf(0x5001, 0xD200), info.devicePropertiesSupported)
        assertEquals(emptyList<Int>(), info.captureFormats)
        assertEquals(listOf(0x3801, 0xB101), info.imageFormats)
        assertEquals("Sony Corporation", info.manufacturer)
        assertEquals("ILCE-7RM4", info.model)
        assertEquals("1.20", info.deviceVersion)
        assertEquals("00000000000000001234", info.serialNumber)
    }

    @Test
    fun `header decoding returns length and type`() {
        val packet = PtpIpCodec.encode(PtpIp.PacketType.PROBE_REQUEST, byteArrayOf(1, 2, 3))

        val (length, type) = PtpIpCodec.decodeHeader(packet)
        assertEquals(11, length)
        assertEquals(PtpIp.PacketType.PROBE_REQUEST, type)
    }

    private fun ByteBuffer.putPtpString(value: String) {
        put((value.length + 1).toByte())
        put(value.toByteArray(Charsets.UTF_16LE))
        putShort(0)
    }

    private fun ByteBuffer.putUint16Array(values: List<Int>) {
        putInt(values.size)
        values.forEach { putShort(it.toShort()) }
    }
}
