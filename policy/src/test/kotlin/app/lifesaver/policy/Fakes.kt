package app.lifesaver.policy

class MemoryStore : KeyValueStore {
    val values = HashMap<String, String>()

    override fun get(key: String): String? = values[key]

    override fun put(
        key: String,
        value: String?,
    ) {
        if (value == null) values.remove(key) else values[key] = value
    }
}

class FakeClock(
    var elapsed: Long = 1_000_000L,
    var boot: Int = 7,
) : MonotonicClock {
    override fun elapsedRealtime(): Long = elapsed

    override fun bootCount(): Int = boot
}

/** In-memory relay: topics map to the messages posted to them. */
class FakeRelay : Relay {
    val topics = HashMap<String, MutableList<String>>()

    override fun publish(
        topic: String,
        message: String,
    ): Boolean {
        topics.getOrPut(topic) { ArrayList() }.add(message)
        return true
    }

    override fun poll(topic: String): List<String> = topics[topic]?.toList() ?: emptyList()
}

/** A person with a key of their own. The test plays them by signing what the phone shares. */
class TestPartner(
    val keys: PartnerKeyPair = PartnerCrypto.generate(),
) {
    val fingerprint: String get() = PartnerCrypto.fingerprint(keys.publicKey)!!

    fun sign(approval: Approval): String = approval.response(PartnerCrypto.sign(keys.privateKey, approval.message())!!)

    fun approve(
        approval: Approval,
        relay: Relay,
    ) = relay.publish(approval.topic, sign(approval))
}

fun UnlockEngine.pendingApproval(): Approval = ApproveLink.parse(share()!!.webLink)!!
