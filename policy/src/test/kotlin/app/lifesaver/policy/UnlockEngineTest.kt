package app.lifesaver.policy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private val minute = 60_000L
    private val hour = 60 * minute

    private fun withPartner(): UnlockEngine {
        assertTrue(engine.setupPartner("partner@example.test"))
        return engine
    }

    @Test
    fun startsWithoutPartner() {
        assertEquals(UnlockState.NoPartner, engine.state())
        assertEquals(RequestResult.NotAllowed, engine.requestUnlock())
    }

    @Test
    fun setupAcceptsValidEmailOnceOnly() {
        assertFalse(engine.setupPartner("not-an-email"))
        assertTrue(engine.setupPartner("a@b.test"))
        assertFalse(engine.setupPartner("someone-else@b.test"))
        assertEquals("a@b.test", engine.partnerEmail())
        assertEquals(UnlockState.Idle, engine.state())
    }

    @Test
    fun requestEmailsPartnerAndStoresOnlyTheHash() {
        withPartner()
        assertEquals(RequestResult.Sent, engine.requestUnlock())
        assertEquals("partner@example.test", relay.emails.single().email)
        val token = relay.lastApproval().token
        assertTrue(engine.state() is UnlockState.Pending)
        assertTrue("token must not be stored", store.values.values.none { token in it })
        assertTrue(store.values.values.contains(UnlockTokens.hash(token)))
    }

    @Test
    fun failedSendLeavesNothingPending() {
        withPartner()
        relay.sendOk = false
        assertEquals(RequestResult.SendFailed, engine.requestUnlock())
        assertEquals(UnlockState.Idle, engine.state())
    }

    @Test
    fun partnerApprovalOpensTheWindowForFifteenMinutes() {
        withPartner()
        engine.requestUnlock()
        assertFalse(engine.poll())
        relay.partnerApproves()
        assertTrue(engine.poll())
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
        withPartner()
        engine.requestUnlock()
        relay.partnerApproves()
        engine.poll()
        engine.relock()
        assertFalse(engine.isOpen())
    }

    @Test
    fun wrongTokenWrongPurposeAndGarbageDoNothing() {
        withPartner()
        engine.requestUnlock()
        val topic = relay.lastApproval().topic
        relay.publish(topic, "unlock.not-the-token")
        relay.publish(topic, "partner.${relay.lastApproval().token}")
        relay.publish(topic, "garbage")
        relay.publish(topic, "unlock.")
        assertFalse(engine.poll())
        assertFalse(engine.isOpen())
    }

    @Test
    fun approvalWorksOnlyOnce() {
        withPartner()
        engine.requestUnlock()
        relay.partnerApproves()
        assertTrue(engine.poll())
        engine.relock()
        assertFalse(engine.poll())
        assertFalse(engine.isOpen())
    }

    @Test
    fun cannotRequestWhilePendingOrOpen() {
        withPartner()
        engine.requestUnlock()
        assertEquals(RequestResult.NotAllowed, engine.requestUnlock())
        relay.partnerApproves()
        engine.poll()
        assertEquals(RequestResult.NotAllowed, engine.requestUnlock())
        assertEquals(1, relay.emails.size)
    }

    @Test
    fun cancelKillsTheLink() {
        withPartner()
        engine.requestUnlock()
        engine.cancel()
        relay.partnerApproves()
        assertFalse(engine.poll())
        assertEquals(UnlockState.Idle, engine.state())
    }

    @Test
    fun requestExpiresAfterTwentyFourHoursOnTime() {
        withPartner()
        engine.requestUnlock()
        clock.elapsed += 25 * hour
        relay.partnerApproves()
        assertFalse(engine.poll())
        assertEquals(UnlockState.Idle, engine.state())
    }

    @Test
    fun rebootClosesTheWindow() {
        withPartner()
        engine.requestUnlock()
        relay.partnerApproves()
        engine.poll()
        clock.boot += 1
        clock.elapsed = 5_000
        assertFalse(engine.isOpen())
    }

    @Test
    fun rebootDropsAPendingRequest() {
        withPartner()
        engine.requestUnlock()
        clock.boot += 1
        clock.elapsed = 5_000
        relay.partnerApproves()
        assertFalse(engine.poll())
        assertEquals(UnlockState.Idle, engine.state())
    }

    @Test
    fun elapsedGoingBackwardsIsTreatedAsReboot() {
        withPartner()
        engine.requestUnlock()
        relay.partnerApproves()
        engine.poll()
        clock.elapsed -= 10 * minute
        assertFalse(engine.isOpen())
    }

    @Test
    fun changingPartnerNeedsTheCurrentPartnersApproval() {
        withPartner()
        val oldTopic = store.values["approvalTopic"]
        assertEquals(RequestResult.InvalidEmail, engine.requestChangePartner("nope"))
        assertEquals(RequestResult.Sent, engine.requestChangePartner("new@example.test"))
        assertEquals("partner@example.test", relay.emails.last().email)
        assertEquals("partner@example.test", engine.partnerEmail())
        val stale = relay.lastApproval()
        relay.partnerApproves()
        assertTrue(engine.poll())
        assertEquals("new@example.test", engine.partnerEmail())
        assertFalse(engine.isOpen())
        assertTrue(store.values["approvalTopic"] != oldTopic)
        engine.requestUnlock()
        assertEquals("new@example.test", relay.emails.last().email)
        ApproveLink.approve(stale, relay)
        assertFalse(engine.poll())
    }

    @Test
    fun aChangePartnerApprovalCannotOpenAnUnlockWindow() {
        withPartner()
        engine.requestChangePartner("new@example.test")
        val topic = relay.lastApproval().topic
        relay.publish(topic, "unlock.${relay.lastApproval().token}")
        assertFalse(engine.poll())
        assertFalse(engine.isOpen())
    }
}
