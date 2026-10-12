package app.lifesaver.policy

sealed interface UnlockState {
    data object NoPartner : UnlockState

    data object Idle : UnlockState

    data class Pending(
        val purpose: Purpose,
        val remainingMs: Long,
    ) : UnlockState

    data class Open(
        val remainingMs: Long,
    ) : UnlockState
}

enum class RequestResult { Sent, NotAllowed, InvalidEmail, SendFailed }

class UnlockConfig(
    val windowMs: Long = 15L * 60 * 1000,
    val requestTtlMs: Long = 24L * 60 * 60 * 1000,
    /** Where the partner's approve page lives. */
    val approveBase: String,
    /** Relay base URL, carried in the link so the approve page knows where to post. */
    val relayBase: String,
    /** Shown in the email, for example "LifeSaver on SM-A155F". */
    val label: String = "LifeSaver",
)

/**
 * Partner-approved unlock. See docs/UNLOCK.md.
 *
 * The phone keeps only SHA-256 of the token it emailed, so the user cannot approve their own
 * request. All timing uses the monotonic clock and the boot count; a reboot drops a pending
 * request and closes the window.
 */
class UnlockEngine(
    private val store: KeyValueStore,
    private val clock: MonotonicClock,
    private val relay: Relay,
    private val config: UnlockConfig,
) {
    /** Guards the store. Never held across a network call, so the UI is never blocked on one. */
    private val lock = Any()

    fun partnerEmail(): String? = synchronized(lock) { store.get(PARTNER) }

    fun isOpen(): Boolean = synchronized(lock) { remaining(WINDOW) > 0 }

    fun state(): UnlockState = synchronized(lock) { stateLocked() }

    private fun stateLocked(): UnlockState {
        val window = remaining(WINDOW)
        if (window > 0) {
            return UnlockState.Open(window)
        }
        val pending = remaining(PENDING)
        val purpose = store.get(PENDING_PURPOSE)?.let(Purpose::fromWire)
        if (pending > 0 && purpose != null && store.get(PENDING_HASH) != null) {
            return UnlockState.Pending(purpose, pending)
        }
        clearPending()
        return if (store.get(PARTNER) == null) UnlockState.NoPartner else UnlockState.Idle
    }

    /** First-time setup only. Changing the partner later goes through [requestChangePartner]. */
    fun setupPartner(email: String): Boolean =
        synchronized(lock) {
            if (store.get(PARTNER) != null || !validEmail(email)) {
                false
            } else {
                store.put(PARTNER, email.trim())
                store.put(TOPIC, UnlockTokens.random())
                true
            }
        }

    fun requestUnlock(): RequestResult = request(Purpose.Unlock, newEmail = null)

    /** Emails the current partner. The new address is applied only after they approve. */
    fun requestChangePartner(newEmail: String): RequestResult {
        if (!validEmail(newEmail)) {
            return RequestResult.InvalidEmail
        }
        return request(Purpose.ChangePartner, newEmail.trim())
    }

    fun cancel() = synchronized(lock) { clearPending() }

    /** Re-locking early is always allowed. */
    fun relock() = synchronized(lock) { clearTimer(WINDOW) }

    /** Checks the approval topic. Returns true if a pending request was just approved. Network call. */
    fun poll(): Boolean {
        val snapshot =
            synchronized(lock) {
                val pending = stateLocked() as? UnlockState.Pending ?: return false
                Triple(pending.purpose, store.get(PENDING_HASH), store.get(TOPIC))
            }
        val (purpose, hash, topic) = snapshot
        if (hash == null || topic == null) {
            return false
        }
        val messages = relay.poll(topic)
        return synchronized(lock) {
            // The request may have been cancelled, replaced or expired while we were on the network.
            if (stateLocked() !is UnlockState.Pending || store.get(PENDING_HASH) != hash) {
                false
            } else {
                val approved = messages.any { approves(it, purpose, hash) }
                if (approved) accept(purpose)
                approved
            }
        }
    }

    private fun approves(
        message: String,
        purpose: Purpose,
        hash: String,
    ): Boolean {
        val sent = Purpose.fromWire(message.substringBefore('.', "")) ?: return false
        val token = message.substringAfter('.', "")
        return sent == purpose && token.isNotEmpty() && UnlockTokens.matches(token, hash)
    }

    private fun request(
        purpose: Purpose,
        newEmail: String?,
    ): RequestResult {
        val ready =
            synchronized(lock) {
                val partner = store.get(PARTNER)
                val topic = store.get(TOPIC)
                if (partner == null || topic == null || stateLocked() != UnlockState.Idle) null else partner to topic
            } ?: return RequestResult.NotAllowed
        val (partner, topic) = ready
        val token = UnlockTokens.random()
        val approval = Approval(purpose, token, topic, config.relayBase)
        val sent =
            relay.sendEmail(
                email = partner,
                title = "LifeSaver approval request",
                body = emailBody(purpose, newEmail, approval),
                clickUrl = ApproveLink.web(config.approveBase, approval),
            )
        if (!sent) {
            return RequestResult.SendFailed
        }
        return synchronized(lock) {
            if (stateLocked() != UnlockState.Idle) {
                RequestResult.NotAllowed
            } else {
                store.put(PENDING_HASH, UnlockTokens.hash(token))
                store.put(PENDING_PURPOSE, purpose.wire)
                store.put(PENDING_NEW_EMAIL, newEmail)
                startTimer(PENDING, config.requestTtlMs)
                RequestResult.Sent
            }
        }
    }

    private fun accept(purpose: Purpose) {
        when (purpose) {
            Purpose.Unlock -> startTimer(WINDOW, config.windowMs)
            Purpose.ChangePartner -> {
                store.get(PENDING_NEW_EMAIL)?.let { store.put(PARTNER, it) }
                store.put(TOPIC, UnlockTokens.random())
            }
        }
        clearPending()
    }

    private fun emailBody(
        purpose: Purpose,
        newEmail: String?,
        approval: Approval,
    ): String {
        val ask =
            when (purpose) {
                Purpose.Unlock -> "pause its protection for ${config.windowMs / MINUTE_MS} minutes"
                Purpose.ChangePartner -> "change its accountability partner to $newEmail"
            }
        return "${config.label} is asking to $ask.\n\n" +
            "Open this link and press Approve only if you agree:\n" +
            "${ApproveLink.web(config.approveBase, approval)}\n\n" +
            "If you have the LifeSaver app: ${ApproveLink.app(approval)}\n" +
            "Ignore this email to refuse. The request expires on its own."
    }

    private fun clearPending() {
        clearTimer(PENDING)
        store.put(PENDING_HASH, null)
        store.put(PENDING_PURPOSE, null)
        store.put(PENDING_NEW_EMAIL, null)
    }

    private fun startTimer(
        prefix: String,
        durationMs: Long,
    ) {
        val now = clock.elapsedRealtime()
        store.put("${prefix}Until", (now + durationMs).toString())
        store.put("${prefix}At", now.toString())
        store.put("${prefix}Boot", clock.bootCount().toString())
    }

    private fun clearTimer(prefix: String) {
        store.put("${prefix}Until", null)
        store.put("${prefix}At", null)
        store.put("${prefix}Boot", null)
    }

    /** Milliseconds left on a timer, 0 if none, expired, or the phone rebooted since it started. */
    private fun remaining(prefix: String): Long {
        val until = store.get("${prefix}Until")?.toLongOrNull() ?: return 0
        val at = store.get("${prefix}At")?.toLongOrNull() ?: return 0
        val boot = store.get("${prefix}Boot")?.toIntOrNull()
        val now = clock.elapsedRealtime()
        if (boot != clock.bootCount() || now < at) {
            clearTimer(prefix)
            return 0
        }
        return (until - now).coerceAtLeast(0)
    }

    private fun validEmail(email: String): Boolean = EMAIL.matches(email.trim())

    private companion object {
        const val PARTNER = "partnerEmail"
        const val TOPIC = "approvalTopic"
        const val PENDING = "pending"
        const val WINDOW = "window"
        const val PENDING_HASH = "pendingHash"
        const val PENDING_PURPOSE = "pendingPurpose"
        const val PENDING_NEW_EMAIL = "pendingNewEmail"
        const val MINUTE_MS = 60_000L
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
