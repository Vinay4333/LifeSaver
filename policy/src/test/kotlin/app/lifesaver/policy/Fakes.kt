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

class SentEmail(
    val email: String,
    val body: String,
    val clickUrl: String,
)

/** In-memory relay. The test plays the partner by reading the emailed link. */
class FakeRelay : Relay {
    val topics = HashMap<String, MutableList<String>>()
    val emails = ArrayList<SentEmail>()
    var sendOk = true

    override fun sendEmail(
        email: String,
        title: String,
        body: String,
        clickUrl: String,
    ): Boolean {
        if (sendOk) emails.add(SentEmail(email, body, clickUrl))
        return sendOk
    }

    override fun publish(
        topic: String,
        message: String,
    ): Boolean {
        topics.getOrPut(topic) { ArrayList() }.add(message)
        return true
    }

    override fun poll(topic: String): List<String> = topics[topic]?.toList() ?: emptyList()

    /** The partner clicks Approve on the most recent email. */
    fun partnerApproves() {
        val approval = ApproveLink.parse(emails.last().clickUrl)!!
        ApproveLink.approve(approval, this)
    }

    fun lastApproval(): Approval = ApproveLink.parse(emails.last().clickUrl)!!
}
