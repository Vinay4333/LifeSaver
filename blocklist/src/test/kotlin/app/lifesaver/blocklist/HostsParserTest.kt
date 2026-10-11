package app.lifesaver.blocklist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostsParserTest {
    private fun parse(vararg lines: String) = HostsParser.parse(lines.asSequence())

    @Test
    fun parsesHostsFormat() {
        val parsed = parse("0.0.0.0 Example.com", "127.0.0.1\tfoo.example.org", "bar.test.net")
        assertEquals(setOf("example.com", "foo.example.org", "bar.test.net"), parsed.domains)
    }

    @Test
    fun skipsCommentsBlanksAndInlineComments() {
        val parsed = parse("# comment", "", "   ", "0.0.0.0 a.com # trailing note")
        assertEquals(setOf("a.com"), parsed.domains)
    }

    @Test
    fun skipsLocalhostEntriesAndBareAddresses() {
        val parsed = parse("127.0.0.1 localhost", "::1 localhost", "0.0.0.0 0.0.0.0", "255.255.255.255 broadcasthost")
        assertTrue(parsed.domains.none { it == "localhost" || it == "broadcasthost" })
    }

    @Test
    fun skipsInvalidNames() {
        val parsed = parse("0.0.0.0 nodots", "0.0.0.0 bad_name!.com", "0.0.0.0 -x.com", "0.0.0.0 a..com")
        assertTrue(parsed.domains.isEmpty())
    }

    @Test
    fun stripsTrailingDot() {
        assertEquals(setOf("a.com"), parse("0.0.0.0 a.com.").domains)
    }

    @Test
    fun readsDateAsVersion() {
        assertEquals("07 October 2026 18:34:34", parse("# Date: 07 October 2026 18:34:34 (UTC)").version)
    }

    @Test
    fun versionFallsBackToUnknown() {
        assertEquals(HostsParser.UNKNOWN_VERSION, parse("0.0.0.0 a.com").version)
    }

    @Test
    fun validatesDomains() {
        assertTrue(HostsParser.isValidDomain("a.co"))
        assertFalse(HostsParser.isValidDomain("a"))
        assertFalse(HostsParser.isValidDomain("a." + "x".repeat(64) + ".com"))
    }
}
