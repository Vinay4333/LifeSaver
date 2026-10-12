package app.lifesaver.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkAndJsonTest {
    private val approval = Approval(Purpose.Unlock, "req_id-1", "topic123", "https://ntfy.sh", "AAAA-BBBB-CCCC-DDDD")

    @Test
    fun webLinkRoundTrips() {
        val link = ApproveLink.web("https://x.test/approve/", approval)
        assertTrue(link.startsWith("https://x.test/approve/#"))
        val parsed = ApproveLink.parse(link)!!
        assertEquals(approval.message(), parsed.message())
        assertEquals("topic123", parsed.topic)
        assertEquals("https://ntfy.sh", parsed.relay)
        assertEquals("AAAA-BBBB-CCCC-DDDD", parsed.expectedFingerprint)
    }

    @Test
    fun appLinkRoundTrips() {
        val link = ApproveLink.app(approval)
        assertTrue(link.startsWith("lifesaver://approve?"))
        assertEquals(Purpose.Unlock, ApproveLink.parse(link)!!.purpose)
    }

    @Test
    fun partnerChangeCarriesAValidNewKey() {
        val key = PartnerCrypto.generate().publicKey
        val change = Approval(Purpose.ChangePartner, "id", "t", "https://r.test", "FP", key)
        val parsed = ApproveLink.parse(ApproveLink.web("https://x.test/a/", change))!!
        assertEquals(key, parsed.newKey)
        assertEquals(change.message(), parsed.message())
        val withoutKey =
            ApproveLink.web(
                "https://x.test/a/",
                Approval(Purpose.ChangePartner, "id", "t", "https://r.test", "FP"),
            )
        assertNull(ApproveLink.parse(withoutKey))
    }

    @Test
    fun parseRejectsIncompleteOrUnsafeLinks() {
        assertNull(ApproveLink.parse("hello"))
        assertNull(ApproveLink.parse("lifesaver://approve?p=unlock&r=a&topic=b&fp=c"))
        assertNull(ApproveLink.parse("lifesaver://approve?p=nope&r=a&topic=b&fp=c&relay=https://r.test"))
        assertNull(ApproveLink.parse("lifesaver://approve?p=unlock&r=a&topic=b&fp=c&relay=ftp://r.test"))
        assertNull(ApproveLink.parse("lifesaver://approve?p=unlock&topic=b&fp=c&relay=https://r.test"))
    }

    @Test
    fun responseFormatIsPurposeIdSignature() {
        assertEquals("unlock.req_id-1.SIG", approval.response("SIG"))
    }

    @Test
    fun requestIdsAndTopicsAreRandom() {
        assertNotEquals(UnlockTokens.random(), UnlockTokens.random())
        assertEquals(22, UnlockTokens.random().length)
    }

    @Test
    fun ntfyJsonKeepsOnlyMessageEventsAndUnescapes() {
        val body =
            """
            {"id":"a","time":1,"event":"open","topic":"t"}
            {"id":"b","time":2,"event":"message","topic":"t","message":"unlock.abc_-1"}
            {"id":"c","time":3,"event":"keepalive","topic":"t"}
            {"id":"d","time":4,"event":"message","topic":"t","message":"say \"hi\"\nline A"}
            """.trimIndent()
        assertEquals(listOf("unlock.abc_-1", "say \"hi\"\nline A"), NtfyJson.messages(body))
    }

    @Test
    fun ntfyJsonHandlesEmptyBody() {
        assertTrue(NtfyJson.messages("").isEmpty())
        assertFalse(NtfyJson.messages("{}").isNotEmpty())
    }
}
