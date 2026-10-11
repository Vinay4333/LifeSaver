package app.lifesaver.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainBlocklistTest {
    private val list =
        DomainBlocklist(
            setOf("example.com", "pornhub.com"),
        )

    @Test
    fun matchesExactName() {
        assertTrue(list.isBlocked("example.com"))
    }

    @Test
    fun matchesParentLabel() {
        assertTrue(list.isBlocked("www.example.com"))
        assertTrue(list.isBlocked("a.b.pornhub.com"))
    }

    @Test
    fun ignoresCaseAndTrailingDot() {
        assertTrue(list.isBlocked("Example.COM."))
    }

    @Test
    fun doesNotMatchPartialLabel() {
        assertFalse(list.isBlocked("notexample.com"))
        assertFalse(list.isBlocked("example.com.evil.com"))
        assertFalse(list.isBlocked("example.org"))
    }
}
