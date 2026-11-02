package app.cameraconnect.wifi

import java.net.Inet4Address
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubnetMathTest {

    private fun v4(address: String) = InetAddress.getByName(address) as Inet4Address

    @Test
    fun `returns the first host of the subnet`() {
        assertEquals(v4("192.168.122.1"), SubnetMath.firstHostOfSubnet(v4("192.168.122.57"), 24))
        assertEquals(v4("10.0.0.1"), SubnetMath.firstHostOfSubnet(v4("10.3.4.5"), 8))
        assertEquals(v4("172.16.0.1"), SubnetMath.firstHostOfSubnet(v4("172.16.9.200"), 12))
    }

    @Test
    fun `returns null when the local address already is the first host or the prefix is unusable`() {
        assertNull(SubnetMath.firstHostOfSubnet(v4("192.168.122.1"), 24))
        assertNull(SubnetMath.firstHostOfSubnet(v4("192.168.122.2"), 31))
        assertNull(SubnetMath.firstHostOfSubnet(v4("192.168.122.2"), 0))
    }
}
