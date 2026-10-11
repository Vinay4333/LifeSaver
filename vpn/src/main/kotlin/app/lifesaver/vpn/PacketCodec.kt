package app.lifesaver.vpn

internal class DnsQuery(
    val names: List<String>,
    val payload: ByteArray,
)

internal object PacketCodec {
    private const val IPV4 = 4
    private const val IPV6 = 6
    private const val UDP = 17
    private const val DNS_PORT = 53
    private const val IPV4_MIN_HEADER = 20
    private const val IPV6_HEADER = 40
    private const val UDP_HEADER = 8
    private const val TTL = 64

    fun dnsQuery(
        packet: ByteArray,
        length: Int,
    ): DnsQuery? {
        val datagram = udpPayload(packet, length) ?: return null
        if (datagram.destPort != DNS_PORT) {
            return null
        }
        val names = DnsMessages.questionNames(datagram.payload) ?: return null
        return DnsQuery(names, datagram.payload)
    }

    fun dnsResponsePacket(
        request: ByteArray,
        length: Int,
        dnsPayload: ByteArray,
    ): ByteArray? {
        if (length < 1) {
            return null
        }
        return when ((request[0].toInt() and 0xF0) ushr 4) {
            IPV4 -> ipv4Response(request, length, dnsPayload)
            IPV6 -> ipv6Response(request, length, dnsPayload)
            else -> null
        }
    }

    fun ipv4HeaderChecksum(
        header: ByteArray,
        headerLength: Int,
    ): Int {
        var sum = 0
        var index = 0
        while (index < headerLength) {
            if (index == CHECKSUM_OFFSET) {
                index += 2
                continue
            }
            sum += Bytes.getU16(header, index)
            index += 2
        }
        return fold(sum)
    }

    private fun udpPayload(
        packet: ByteArray,
        length: Int,
    ): UdpDatagram? {
        if (length < 1) {
            return null
        }
        return when ((packet[0].toInt() and 0xF0) ushr 4) {
            IPV4 -> ipv4Udp(packet, length)
            IPV6 -> ipv6Udp(packet, length)
            else -> null
        }
    }

    private fun ipv4Udp(
        packet: ByteArray,
        length: Int,
    ): UdpDatagram? {
        val headerLength = (packet[0].toInt() and 0x0F) * 4
        if (headerLength < IPV4_MIN_HEADER || length < headerLength + UDP_HEADER) {
            return null
        }
        val fragment = Bytes.getU16(packet, 6) and 0x3FFF
        if (fragment != 0 || packet[9].toInt() and 0xFF != UDP) {
            return null
        }
        return readUdp(packet, headerLength, length)
    }

    private fun ipv6Udp(
        packet: ByteArray,
        length: Int,
    ): UdpDatagram? {
        if (length < IPV6_HEADER + UDP_HEADER) {
            return null
        }
        if (packet[6].toInt() and 0xFF != UDP) {
            return null
        }
        return readUdp(packet, IPV6_HEADER, length)
    }

    private fun readUdp(
        packet: ByteArray,
        headerLength: Int,
        length: Int,
    ): UdpDatagram? {
        val udpLength = Bytes.getU16(packet, headerLength + 4)
        if (udpLength < UDP_HEADER || headerLength + udpLength > length) {
            return null
        }
        val payload = packet.copyOfRange(headerLength + UDP_HEADER, headerLength + udpLength)
        return UdpDatagram(
            sourcePort = Bytes.getU16(packet, headerLength),
            destPort = Bytes.getU16(packet, headerLength + 2),
            payload = payload,
        )
    }

    private fun ipv4Response(
        request: ByteArray,
        length: Int,
        dnsPayload: ByteArray,
    ): ByteArray? {
        val headerLength = (request[0].toInt() and 0x0F) * 4
        if (headerLength < IPV4_MIN_HEADER || length < headerLength + UDP_HEADER) {
            return null
        }
        val udpLength = UDP_HEADER + dnsPayload.size
        val total = headerLength + udpLength
        val out = ByteArray(total)
        request.copyInto(out, 0, 0, headerLength)
        Bytes.putU16(out, 2, total)
        out[8] = TTL.toByte()
        out[10] = 0
        out[11] = 0
        request.copyInto(out, 12, 16, 20)
        request.copyInto(out, 16, 12, 16)
        Bytes.putU16(out, 10, ipv4HeaderChecksum(out, headerLength))
        writeUdp(out, headerLength, request, dnsPayload, checksum = 0)
        return out
    }

    private fun ipv6Response(
        request: ByteArray,
        length: Int,
        dnsPayload: ByteArray,
    ): ByteArray? {
        if (length < IPV6_HEADER + UDP_HEADER) {
            return null
        }
        val udpLength = UDP_HEADER + dnsPayload.size
        val out = ByteArray(IPV6_HEADER + udpLength)
        request.copyInto(out, 0, 0, IPV6_HEADER)
        Bytes.putU16(out, 4, udpLength)
        out[7] = TTL.toByte()
        request.copyInto(out, 8, 24, 40)
        request.copyInto(out, 24, 8, 24)
        writeUdp(out, IPV6_HEADER, request, dnsPayload, checksum = 0)
        val checksum = udpChecksumIpv6(out, udpLength)
        Bytes.putU16(out, IPV6_HEADER + 6, checksum)
        return out
    }

    private fun writeUdp(
        out: ByteArray,
        headerLength: Int,
        request: ByteArray,
        dnsPayload: ByteArray,
        checksum: Int,
    ) {
        val sourcePort = Bytes.getU16(request, headerLength)
        val destPort = Bytes.getU16(request, headerLength + 2)
        Bytes.putU16(out, headerLength, destPort)
        Bytes.putU16(out, headerLength + 2, sourcePort)
        Bytes.putU16(out, headerLength + 4, UDP_HEADER + dnsPayload.size)
        Bytes.putU16(out, headerLength + 6, checksum)
        dnsPayload.copyInto(out, headerLength + UDP_HEADER)
    }

    private fun udpChecksumIpv6(
        packet: ByteArray,
        udpLength: Int,
    ): Int {
        var sum = 0
        sum = addRange(sum, packet, 8, 40)
        sum += (udpLength ushr 16) and 0xFFFF
        sum += udpLength and 0xFFFF
        sum += UDP
        var index = IPV6_HEADER
        val end = IPV6_HEADER + udpLength
        while (index + 1 < end) {
            if (index != IPV6_HEADER + 6) {
                sum += Bytes.getU16(packet, index)
            }
            index += 2
        }
        if (index < end) {
            sum += (packet[index].toInt() and 0xFF) shl 8
        }
        val folded = fold(sum)
        return if (folded == 0) 0xFFFF else folded
    }

    private fun addRange(
        initial: Int,
        buf: ByteArray,
        start: Int,
        end: Int,
    ): Int {
        var sum = initial
        var index = start
        while (index < end) {
            sum += Bytes.getU16(buf, index)
            index += 2
        }
        return sum
    }

    private fun fold(sum: Int): Int {
        var value = sum
        while (value ushr 16 != 0) {
            value = (value and 0xFFFF) + (value ushr 16)
        }
        return value.inv() and 0xFFFF
    }

    private const val CHECKSUM_OFFSET = 10

    private class UdpDatagram(
        val sourcePort: Int,
        val destPort: Int,
        val payload: ByteArray,
    )
}
