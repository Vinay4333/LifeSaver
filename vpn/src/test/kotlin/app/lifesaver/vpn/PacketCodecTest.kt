package app.lifesaver.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PacketCodecTest {
    @Test
    fun ipv4DnsQueryRoundTripsNxDomain() {
        val dns = queryFor("www.example.com")
        val packet = ipv4Udp(dns, sourcePort = 53000)
        val parsed = PacketCodec.dnsQuery(packet, packet.size)
        assertEquals(listOf("www.example.com"), parsed?.names)
        val nx = DnsMessages.nxDomain(parsed!!.payload)
        val response = PacketCodec.dnsResponsePacket(packet, packet.size, nx!!)
        assertNotNull(response)
        assertEquals(10, response!![12].toInt() and 0xFF)
        assertEquals(1, response[15].toInt() and 0xFF)
        assertEquals(2, response[19].toInt() and 0xFF)
        assertEquals(53, Bytes.getU16(response, 20))
        assertEquals(53000, Bytes.getU16(response, 22))
        assertEquals(
            PacketCodec.ipv4HeaderChecksum(response, 20),
            Bytes.getU16(response, 10),
        )
        assertNull(PacketCodec.dnsQuery(response, response.size))
        assertEquals(3, DnsMessages.rcode(response.copyOfRange(28, response.size)))
    }

    @Test
    fun dropsNonDnsUdp() {
        val packet = ipv4Udp(queryFor("example.com"), sourcePort = 1)
        Bytes.putU16(packet, 22, 80)
        assertNull(PacketCodec.dnsQuery(packet, packet.size))
    }

    @Test
    fun ipv6ResponseHasChecksum() {
        val dns = queryFor("example.com")
        val packet = ipv6Udp(dns)
        val parsed = PacketCodec.dnsQuery(packet, packet.size)
        assertEquals(listOf("example.com"), parsed?.names)
        val response = PacketCodec.dnsResponsePacket(packet, packet.size, DnsMessages.nxDomain(parsed!!.payload)!!)
        assertNotNull(response)
        assertEquals(0x60, response!![0].toInt() and 0xF0)
        assertNotEquals(0, Bytes.getU16(response, 46))
    }

    private fun ipv4Udp(
        dns: ByteArray,
        sourcePort: Int,
    ): ByteArray {
        val udpLength = 8 + dns.size
        val total = 20 + udpLength
        val packet = ByteArray(total)
        packet[0] = 0x45
        Bytes.putU16(packet, 2, total)
        packet[8] = 64
        packet[9] = 17
        packet[12] = 10
        packet[15] = 2
        packet[16] = 10
        packet[19] = 1
        Bytes.putU16(packet, 10, PacketCodec.ipv4HeaderChecksum(packet, 20))
        Bytes.putU16(packet, 20, sourcePort)
        Bytes.putU16(packet, 22, 53)
        Bytes.putU16(packet, 24, udpLength)
        dns.copyInto(packet, 28)
        return packet
    }

    private fun ipv6Udp(dns: ByteArray): ByteArray {
        val udpLength = 8 + dns.size
        val packet = ByteArray(40 + udpLength)
        packet[0] = 0x60
        Bytes.putU16(packet, 4, udpLength)
        packet[6] = 17
        packet[7] = 64
        packet[23] = 2
        packet[39] = 1
        Bytes.putU16(packet, 40, 53000)
        Bytes.putU16(packet, 42, 53)
        Bytes.putU16(packet, 44, udpLength)
        dns.copyInto(packet, 48)
        return packet
    }

    private fun queryFor(name: String): ByteArray {
        val out = ArrayList<Byte>()

        fun add(value: Int) = out.add(value.toByte())
        add(0x12)
        add(0x34)
        add(0x01)
        add(0x00)
        add(0x00)
        add(0x01)
        repeat(6) { add(0) }
        name.split('.').forEach { label ->
            add(label.length)
            label.forEach { add(it.code) }
        }
        add(0)
        add(0)
        add(1)
        add(0)
        add(1)
        return out.toByteArray()
    }
}
