package app.lifesaver.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkAndJsonTest {
    private val approval = Approval(Purpose.Unlock, "tok_en-1", "topic123", "https://ntfy.sh")

    @Test
    fun webLinkRoundTrips() {
        val link = ApproveLink.web("https://x.test/approve/", approval)
        assertTrue(link.startsWith("https://x.test/approve/#"))
        val parsed = ApproveLink.parse(link)!!
        assertEquals(approval.message(), parsed.message())
        assertEquals("topic123", parsed.topic)
        assertEquals("https://ntfy.sh", parsed.relay)
    }

    @Test
    fun appLinkRoundTrips() {
        val link = ApproveLink.app(approval)
        assertTrue(link.startsWith("lifesaver://approve?"))
        assertEquals(Purpose.Unlock, ApproveLink.parse(link)!!.purpose)
    }

    @Test
    fun tokenIsInTheFragmentNotTheQueryOfTheWebLink() {
        val link = ApproveLink.web("https://x.test/approve/", approval)
        assertFalse("?" in link)
        assertTrue("#" in link)
    }

    @Test
    fun parseRejectsIncompleteOrUnsafeLinks() {
        assertNull(ApproveLink.parse("hello"))
        assertNull(ApproveLink.parse("lifesaver://approve?p=unlock&t=a&topic=b"))
        assertNull(ApproveLink.parse("lifesaver://approve?p=nope&t=a&topic=b&relay=https://r.test"))
        assertNull(ApproveLink.parse("lifesaver://approve?p=unlock&t=a&topic=b&relay=ftp://r.test"))
    }

    @Test
    fun tokensAreRandomAndHashesMatch() {
        val a = UnlockTokens.random()
        assertNotEquals(a, UnlockTokens.random())
        assertTrue(UnlockTokens.matches(a, UnlockTokens.hash(a)))
        assertFalse(UnlockTokens.matches("other", UnlockTokens.hash(a)))
        assertEquals(64, UnlockTokens.hash(a).length)
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
    }
}
