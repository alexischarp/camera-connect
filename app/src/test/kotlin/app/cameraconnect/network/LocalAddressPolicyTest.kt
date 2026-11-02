package app.cameraconnect.network

import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAddressPolicyTest {

    @Test
    fun `private and link-local addresses are allowed`() {
        assertTrue(LocalAddressPolicy.isAllowed(InetAddress.getByName("192.168.122.1")))
        assertTrue(LocalAddressPolicy.isAllowed(InetAddress.getByName("10.0.0.5")))
        assertTrue(LocalAddressPolicy.isAllowed(InetAddress.getByName("172.16.4.9")))
        assertTrue(LocalAddressPolicy.isAllowed(InetAddress.getByName("169.254.1.1")))
    }

    @Test
    fun `public addresses are refused`() {
        assertFalse(LocalAddressPolicy.isAllowed(InetAddress.getByName("8.8.8.8")))
        assertFalse(LocalAddressPolicy.isAllowed(InetAddress.getByName("1.1.1.1")))
    }

    @Test
    fun `literal parsing never resolves host names`() {
        assertEquals(InetAddress.getByName("192.168.122.1"), LocalAddressPolicy.parseLiteral("192.168.122.1"))
        assertEquals(InetAddress.getByName("fe80::1"), LocalAddressPolicy.parseLiteral("[fe80::1]"))
        assertNull(LocalAddressPolicy.parseLiteral("camera.local"))
        assertNull(LocalAddressPolicy.parseLiteral("example.com"))
    }
}
