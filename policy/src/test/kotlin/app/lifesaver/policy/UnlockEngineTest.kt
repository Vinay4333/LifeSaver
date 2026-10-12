package app.lifesaver.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockEngineTest {
    private val store = MemoryStore()
    private val clock = FakeClock()
    private val relay = FakeRelay()
    private val config =
        UnlockConfig(
            approveBase = "https://example.test/approve/",
            relayBase = "https://relay.test",
            label = "LifeSaver on test",
        )
    private val engine = UnlockEngine(store, clock, relay, config)
    private val partner = TestPartner()

    private val minute = 60_000L
    private val hour = 60 * minute

    /** Pairs [partner] the way a real user would: paste the key, share the request, partner signs. */
    private fun paired(contact: String? = "partner@example.test"): UnlockEngine {
        assertEquals(RequestResult.Created, engine.beginPairing(partner.keys.publicKey, contact))
        partner.approve(engine.pendingApproval(), relay)
        assertTrue(engine.poll())
        return engine
    }

    private fun unlocked(): UnlockEngine {
        paired()
        engine.requestUnlock()
        partner.approve(engine.pendingApproval(), relay)
        assertTrue(engine.poll())
        return engine
    }

    @Test
    fun startsWithoutPartner() {
        assertEquals(UnlockState.NoPartner, engine.state())
        assertEquals(RequestResult.NotAllowed, engine.requestUnlock())
        assertNull(engine.share())
    }

    @Test
    fun pairingRejectsKeysThatAreNotP256Points() {
        assertEquals(RequestResult.InvalidKey, engine.beginPairing("not-a-key", null))
        assertEquals(RequestResult.InvalidKey, engine.beginPairing(PartnerCrypto.encode(ByteArray(65)), null))
        assertEquals(UnlockState.NoPartner, engine.state())
    }

    @Test
    fun partnerIsSavedOnlyAfterTheySignThePairingRequest() {
        assertEquals(RequestResult.Created, engine.beginPairing(partner.keys.publicKey, "p@x.test"))
        assertTrue(engine.state() is UnlockState.Pending)
        assertEquals(partner.fingerprint, engine.pendingFingerprint())
        assertNull(engine.partnerKey())
        assertEquals("p@x.test", engine.share()!!.contact)
        partner.approve(engine.pendingApproval(), relay)
        assertTrue(engine.poll())
        assertEquals(UnlockState.Idle, engine.state())
        assertEquals(partner.keys.publicKey, engine.partnerKey())
        assertEquals(partner.fingerprint, engine.partnerFingerprint())
        assertEquals("p@x.test", engine.partnerContact())
    }

    @Test
    fun aDifferentKeyCannotCompletePairing() {
        engine.beginPairing(partner.keys.publicKey, null)
        TestPartner().approve(engine.pendingApproval(), relay)
        assertFalse(engine.poll())
        assertNull(engine.partnerKey())
    }

    @Test
    fun pairingCanBeCancelledAndRestarted() {
        engine.beginPairing(partner.keys.publicKey, null)
        engine.cancel()
        assertEquals(UnlockState.NoPartner, engine.state())
        assertEquals(RequestResult.Created, engine.beginPairing(TestPartner().keys.publicKey, null))
    }

    @Test
    fun pairingIsNotAvailableOnceAPartnerExists() {
        paired()
        assertEquals(RequestResult.NotAllowed, engine.beginPairing(TestPartner().keys.publicKey, null))
    }

    @Test
    fun unlockRequestNeedsNoNetworkAndSharesOnlyPublicData() {
        paired()
        assertEquals(RequestResult.Created, engine.requestUnlock())
        val share = engine.share()!!
        assertTrue(share.webLink.startsWith("https://example.test/approve/#p=unlock&r="))
        assertTrue("lifesaver://approve?p=unlock" in share.appLink)
        assertEquals("partner@example.test", share.contact)
        assertTrue(share.text.contains(share.webLink))
        assertTrue("no private key anywhere", store.values.values.none { partner.keys.privateKey in it })
    }

    @Test
    fun partnerSignatureOpensTheWindowForFifteenMinutes() {
        unlocked()
        assertTrue(engine.isOpen())
        assertEquals(UnlockState.Open(15 * minute), engine.state())
        clock.elapsed += 14 * minute
        assertTrue(engine.isOpen())
        clock.elapsed += 2 * minute
        assertFalse(engine.isOpen())
        assertEquals(UnlockState.Idle, engine.state())
    }

    @Test
    fun relockClosesTheWindow() {
        unlocked()
        engine.relock()
        assertFalse(engine.isOpen())
    }

    @Test
    fun badResponsesAreIgnored() {
        paired()
        engine.requestUnlock()
        val approval = engine.pendingApproval()
        val good = partner.sign(approval).substringAfterLast('.')
        relay.publish(approval.topic, "unlock.${approval.requestId}.not-a-signature")
        relay.publish(approval.topic, "unlock.OTHERID.$good")
        relay.publish(approval.topic, "partner.${approval.requestId}.$good")
        relay.publish(approval.topic, "unlock.${approval.requestId}")
        relay.publish(approval.topic, "garbage")
        relay.publish(approval.topic, TestPartner().sign(approval))
        assertFalse(engine.poll())
        assertFalse(engine.isOpen())
    }

    @Test
    fun anOldSignatureDoesNotWorkForANewRequest() {
        paired()
        engine.requestUnlock()
        val first = engine.pendingApproval()
        val oldResponse = partner.sign(first)
        relay.publish(first.topic, oldResponse)
        assertTrue(engine.poll())
        engine.relock()
        engine.requestUnlock()
        val fresh = engine.pendingApproval()
        assertNotEquals(first.requestId, fresh.requestId)
        relay.publish(fresh.topic, oldResponse)
        assertFalse(engine.poll())
        partner.approve(fresh, relay)
        assertTrue(engine.poll())
    }

    @Test
    fun cancelKillsTheRequest() {
        paired()
        engine.requestUnlock()
        val approval = engine.pendingApproval()
        engine.cancel()
        partner.approve(approval, relay)
        assertFalse(engine.poll())
        assertEquals(UnlockState.Idle, engine.state())
    }

    @Test
    fun cannotRequestWhilePendingOrOpen() {
        paired()
        engine.requestUnlock()
        assertEquals(RequestResult.NotAllowed, engine.requestUnlock())
        partner.approve(engine.pendingApproval(), relay)
        engine.poll()
        assertEquals(RequestResult.NotAllowed, engine.requestUnlock())
    }

    @Test
    fun requestExpiresAfterTwentyFourHoursOnTime() {
        paired()
        engine.requestUnlock()
        val approval = engine.pendingApproval()
        clock.elapsed += 25 * hour
        partner.approve(approval, relay)
        assertFalse(engine.poll())
        assertEquals(UnlockState.Idle, engine.state())
    }

    @Test
    fun rebootClosesTheWindow() {
        unlocked()
        clock.boot += 1
        clock.elapsed = 5_000
        assertFalse(engine.isOpen())
    }

    @Test
    fun rebootDropsAPendingRequest() {
        paired()
        engine.requestUnlock()
        val approval = engine.pendingApproval()
        clock.boot += 1
        clock.elapsed = 5_000
        partner.approve(approval, relay)
        assertFalse(engine.poll())
        assertEquals(UnlockState.Idle, engine.state())
    }

    @Test
    fun elapsedGoingBackwardsIsTreatedAsReboot() {
        unlocked()
        clock.elapsed -= 10 * minute
        assertFalse(engine.isOpen())
    }

    @Test
    fun changingPartnerNeedsTheCurrentPartnersSignatureOverTheNewKey() {
        paired()
        val next = TestPartner()
        assertEquals(RequestResult.InvalidKey, engine.requestChangePartner("nope", null))
        assertEquals(RequestResult.InvalidKey, engine.requestChangePartner(partner.keys.publicKey, null))
        assertEquals(RequestResult.Created, engine.requestChangePartner(next.keys.publicKey, "new@x.test"))
        val approval = engine.pendingApproval()
        assertEquals(next.keys.publicKey, approval.newKey)
        assertEquals(partner.fingerprint, approval.expectedFingerprint)
        assertEquals("partner@example.test", engine.share()!!.contact)

        next.approve(approval, relay)
        assertFalse("the new key cannot approve its own appointment", engine.poll())
        assertEquals(partner.keys.publicKey, engine.partnerKey())

        partner.approve(approval, relay)
        assertTrue(engine.poll())
        assertEquals(next.keys.publicKey, engine.partnerKey())
        assertEquals("new@x.test", engine.partnerContact())
        assertFalse(engine.isOpen())
    }

    @Test
    fun aSignatureForOneNewKeyCannotAppointAnother() {
        paired()
        val intended = TestPartner()
        val attacker = TestPartner()
        engine.requestChangePartner(attacker.keys.publicKey, null)
        val real = engine.pendingApproval()
        val forged = Approval(Purpose.ChangePartner, real.requestId, real.topic, "r", "fp", intended.keys.publicKey)
        relay.publish(real.topic, partner.sign(forged))
        assertFalse(engine.poll())
        assertNotEquals(attacker.keys.publicKey, engine.partnerKey())
    }

    @Test
    fun afterPartnerChangeOnlyTheNewKeyUnlocks() {
        paired()
        val next = TestPartner()
        engine.requestChangePartner(next.keys.publicKey, null)
        partner.approve(engine.pendingApproval(), relay)
        engine.poll()
        engine.requestUnlock()
        val approval = engine.pendingApproval()
        partner.approve(approval, relay)
        assertFalse(engine.poll())
        next.approve(approval, relay)
        assertTrue(engine.poll())
    }
}
