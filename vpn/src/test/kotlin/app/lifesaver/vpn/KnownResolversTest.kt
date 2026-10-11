package app.lifesaver.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

class KnownResolversTest {
    @Test
    fun ipv4EntriesAreIpv4Literals() {
        assertTrue(KnownResolvers.ipv4.all { InetAddress.getByName(it) is Inet4Address })
    }

    @Test
    fun ipv6EntriesAreIpv6Literals() {
        assertTrue(KnownResolvers.ipv6.all { InetAddress.getByName(it) is Inet6Address })
    }

    @Test
    fun entriesAreUnique() {
        assertEquals(KnownResolvers.ipv4.size, KnownResolvers.ipv4.toSet().size)
        assertEquals(KnownResolvers.ipv6.size, KnownResolvers.ipv6.toSet().size)
    }

    @Test
    fun doesNotRouteOurOwnResolver() {
        assertTrue("10.0.0.1" !in KnownResolvers.ipv4)
        assertTrue("fd00:1::1" !in KnownResolvers.ipv6)
    }
}
