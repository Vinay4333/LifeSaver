package app.lifesaver.blocklist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlocklistIndexTest {
    private val base = setOf("blocked.example", "other.test")

    private fun index(
        allow: Set<String> = emptySet(),
        deny: Set<String> = emptySet(),
    ) = BlocklistIndex(base, "v1", allow, deny)

    @Test
    fun matchesExactAndSubdomain() {
        val list = index()
        assertTrue(list.isBlocked("blocked.example"))
        assertTrue(list.isBlocked("www.blocked.example"))
        assertTrue(list.isBlocked("a.b.blocked.example"))
    }

    @Test
    fun ignoresCaseAndTrailingDot() {
        assertTrue(index().isBlocked("WWW.Blocked.Example."))
    }

    @Test
    fun doesNotMatchPartialLabelOrSuffixTricks() {
        val list = index()
        assertFalse(list.isBlocked("notblocked.example"))
        assertFalse(list.isBlocked("blocked.example.evil.com"))
        assertFalse(list.isBlocked("example"))
        assertFalse(list.isBlocked(""))
    }

    @Test
    fun allowOverrideUnblocksNameAndSubdomains() {
        val list = index(allow = setOf("blocked.example"))
        assertFalse(list.isBlocked("blocked.example"))
        assertFalse(list.isBlocked("www.blocked.example"))
    }

    @Test
    fun allowOnSubdomainLeavesParentBlocked() {
        val list = index(allow = setOf("ok.blocked.example"))
        assertFalse(list.isBlocked("x.ok.blocked.example"))
        assertTrue(list.isBlocked("blocked.example"))
        assertTrue(list.isBlocked("other.blocked.example"))
    }

    @Test
    fun denyOverrideBlocksNewNames() {
        val list = index(deny = setOf("mine.test"))
        assertTrue(list.isBlocked("www.mine.test"))
    }

    @Test
    fun specificDenyBeatsParentAllow() {
        val list = index(allow = setOf("blocked.example"), deny = setOf("bad.blocked.example"))
        assertTrue(list.isBlocked("bad.blocked.example"))
        assertFalse(list.isBlocked("good.blocked.example"))
    }

    @Test
    fun sameLevelAllowBeatsDeny() {
        assertFalse(index(allow = setOf("x.test"), deny = setOf("x.test")).isBlocked("x.test"))
    }

    @Test
    fun withOverridesSharesBaseAndKeepsVersion() {
        val next = index().withOverrides(setOf("blocked.example"), emptySet())
        assertEquals(2, next.baseCount)
        assertEquals("v1", next.version)
        assertFalse(next.isBlocked("blocked.example"))
    }

    @Test
    fun emptyIndexBlocksNothing() {
        assertFalse(BlocklistIndex.EMPTY.isBlocked("pornhub.com"))
    }

    @Test
    fun resolverHostsAreBlockedAndCannotBeAllowed() {
        val list = BlocklistIndex(base, "v1", allow = setOf("dns.google"), resolvers = setOf("dns.google"))
        assertTrue(list.isBlocked("dns.google"))
        assertTrue(list.isBlocked("foo.dns.google"))
        assertFalse(list.isBlocked("google"))
        assertEquals(1, list.resolverCount)
    }

    @Test
    fun resolversSurviveOverrideChanges() {
        val list = BlocklistIndex(base, "v1", resolvers = setOf("dns.google")).withOverrides(emptySet(), emptySet())
        assertTrue(list.isBlocked("dns.google"))
    }
}
