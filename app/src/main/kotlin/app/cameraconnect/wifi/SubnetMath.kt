package app.cameraconnect.wifi

import java.net.Inet4Address
import java.net.InetAddress
import java.nio.ByteBuffer

/** Small IPv4 helpers kept free of Android types so they can be unit tested. */
object SubnetMath {

    /**
     * Returns the first usable host of the subnet containing [address] (network address + 1),
     * or null when the prefix leaves no room for other hosts or the result is [address] itself.
     */
    fun firstHostOfSubnet(address: Inet4Address, prefixLength: Int): Inet4Address? {
        if (prefixLength !in 1..30) return null
        val ip = ByteBuffer.wrap(address.address).int
        val mask = -1 shl (32 - prefixLength)
        val first = (ip and mask) + 1
        if (first == ip) return null
        val bytes = ByteBuffer.allocate(4).putInt(first).array()
        return InetAddress.getByAddress(bytes) as Inet4Address
    }
}
