package app.lifesaver.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartnerKeysTest {
    private val store = MemoryStore()
    private val keys = PartnerKeys(store)
    private val relay = FakeRelay()

    private fun approval(fingerprint: String) = Approval(Purpose.Unlock, "req", "topic", "https://r.test", fingerprint)

    @Test
    fun createIsIdempotent() {
        assertNull(keys.publicKey())
        val first = keys.create()
        assertEquals(first, keys.create())
        assertEquals(first, keys.publicKey())
        assertEquals(PartnerCrypto.fingerprint(first), keys.fingerprint())
    }

    @Test
    fun approveWithoutAKeyIsRefused() {
        assertEquals(ApproveResult.NoKey, keys.approve(approval("AAAA-BBBB-CCCC-DDDD"), relay))
        assertTrue(relay.topics.isEmpty())
    }

    @Test
    fun approveForAnotherKeyIsRefused() {
        keys.create()
        assertEquals(ApproveResult.WrongKey, keys.approve(approval("AAAA-BBBB-CCCC-DDDD"), relay))
        assertTrue(relay.topics.isEmpty())
    }

    @Test
    fun approvePostsAVerifiableSignature() {
        val publicKey = keys.create()
        val request = approval(keys.fingerprint()!!)
        assertEquals(ApproveResult.Sent, keys.approve(request, relay))
        val parts =
            relay.topics
                .getValue("topic")
                .single()
                .split('.')
        assertEquals(listOf("unlock", "req"), parts.take(2))
        assertTrue(PartnerCrypto.verify(publicKey, request.message(), parts[2]))
    }

    @Test
    fun theKeyIsKeptSeparateFromTheUnlockStoreWhenGivenAnother() {
        keys.create()
        assertTrue(store.values.containsKey("myPrivateKey"))
        assertTrue(MemoryStore().values.isEmpty())
    }
}
