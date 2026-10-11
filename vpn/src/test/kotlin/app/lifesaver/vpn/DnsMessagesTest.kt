package app.lifesaver.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsMessagesTest {
    @Test
    fun readsQuestionName() {
        val query = queryFor("www.example.com")
        assertEquals(listOf("www.example.com"), DnsMessages.questionNames(query))
    }

    @Test
    fun nxDomainKeepsIdAndQuestion() {
        val query = queryFor("example.com")
        val response = DnsMessages.nxDomain(query)
        assertNotNull(response)
        assertEquals(query[0], response!![0])
        assertEquals(query[1], response[1])
        assertTrue(DnsMessages.isResponse(response))
        assertEquals(3, DnsMessages.rcode(response))
        assertEquals(listOf("example.com"), namesFromResponseQuestion(response))
    }

    @Test
    fun followsCompressionPointer() {
        val query = queryFor("example.com")
        val secondQuestion = query.size
        val compressed = query.copyOf(secondQuestion + 6)
        compressed[secondQuestion] = 0xC0.toByte()
        compressed[secondQuestion + 1] = 12
        Bytes.putU16(compressed, 4, 2)
        Bytes.putU16(compressed, secondQuestion + 2, 1)
        Bytes.putU16(compressed, secondQuestion + 4, 1)
        assertEquals(
            listOf("example.com", "example.com"),
            DnsMessages.questionNames(compressed),
        )
    }

    private fun namesFromResponseQuestion(response: ByteArray): List<String>? {
        val asQuery = response.copyOf()
        val flags = Bytes.getU16(asQuery, 2) and 0x7FFF
        Bytes.putU16(asQuery, 2, flags)
        return DnsMessages.questionNames(asQuery)
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
