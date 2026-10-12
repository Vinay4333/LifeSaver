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

enum class RequestResult { Created, NotAllowed, InvalidKey }

class UnlockConfig(
    val windowMs: Long = 15L * 60 * 1000,
    val requestTtlMs: Long = 24L * 60 * 60 * 1000,
    /** Where the partner's approve page lives. */
    val approveBase: String,
    /** Relay base URL, carried in the link so the approve page knows where to post. */
    val relayBase: String,
    /** Shown in the share text, for example "LifeSaver on SM-A155F". */
    val label: String = "LifeSaver",
)

/** What the user sends to the partner. Public data only. */
class ShareRequest(
    val text: String,
    val webLink: String,
    val appLink: String,
    val contact: String?,
    val subject: String,
)

/**
 * Partner-approved unlock. See docs/UNLOCK.md.
 *
 * The phone holds only the partner's public key. A request is approved by a signature that only
 * the partner's private key can make, so nothing secret travels and the user cannot approve their
 * own request. All timing uses the monotonic clock and the boot count; a reboot drops a pending
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

    fun partnerKey(): String? = synchronized(lock) { store.get(PARTNER_KEY) }

    fun partnerFingerprint(): String? = partnerKey()?.let(PartnerCrypto::fingerprint)

    fun partnerContact(): String? = synchronized(lock) { store.get(PARTNER_CONTACT) }

    /** The candidate partner's fingerprint while pairing, for the user to compare with the partner. */
    fun pendingFingerprint(): String? = synchronized(lock) { store.get(PENDING_KEY)?.let(PartnerCrypto::fingerprint) }

    fun isOpen(): Boolean = synchronized(lock) { remaining(WINDOW) > 0 }

    fun state(): UnlockState = synchronized(lock) { stateLocked() }

    private fun stateLocked(): UnlockState {
        val window = remaining(WINDOW)
        if (window > 0) {
            return UnlockState.Open(window)
        }
        val pending = remaining(PENDING)
        val purpose = store.get(PENDING_PURPOSE)?.let(Purpose::fromWire)
        if (pending > 0 && purpose != null && store.get(PENDING_ID) != null) {
            return UnlockState.Pending(purpose, pending)
        }
        clearPending()
        return if (store.get(PARTNER_KEY) == null) UnlockState.NoPartner else UnlockState.Idle
    }

    /** First-time pairing. The partner is saved only once they sign the pairing request. */
    fun beginPairing(
        publicKey: String,
        contact: String?,
    ): RequestResult =
        synchronized(lock) {
            when {
                stateLocked() != UnlockState.NoPartner -> RequestResult.NotAllowed
                !PartnerCrypto.isValidPublicKey(publicKey) -> RequestResult.InvalidKey
                else -> {
                    createRequest(Purpose.Pair, publicKey.trim(), contact)
                    RequestResult.Created
                }
            }
        }

    fun requestUnlock(): RequestResult =
        synchronized(lock) {
            if (stateLocked() != UnlockState.Idle) {
                RequestResult.NotAllowed
            } else {
                createRequest(Purpose.Unlock, newKey = null, contact = null)
                RequestResult.Created
            }
        }

    /** The current partner must sign the new key. The new partner is saved only after that. */
    fun requestChangePartner(
        newKey: String,
        newContact: String?,
    ): RequestResult =
        synchronized(lock) {
            when {
                stateLocked() != UnlockState.Idle -> RequestResult.NotAllowed
                !PartnerCrypto.isValidPublicKey(newKey) || newKey.trim() == store.get(PARTNER_KEY) ->
                    RequestResult.InvalidKey
                else -> {
                    createRequest(Purpose.ChangePartner, newKey.trim(), newContact)
                    RequestResult.Created
                }
            }
        }

    /** The request waiting for a signature, ready to send, or null. */
    fun share(): ShareRequest? =
        synchronized(lock) {
            val approval = pendingApproval() ?: return null
            val web = ApproveLink.web(config.approveBase, approval)
            val app = ApproveLink.app(approval)
            val what =
                when (approval.purpose) {
                    Purpose.Pair -> "confirm you are my accountability partner"
                    Purpose.Unlock -> "pause my protection for ${config.windowMs / MINUTE_MS} minutes"
                    Purpose.ChangePartner -> "change my accountability partner"
                }
            // Pairing is addressed to the candidate, everything else to the current partner.
            val contact =
                if (approval.purpose == Purpose.Pair) store.get(PENDING_CONTACT) else store.get(PARTNER_CONTACT)
            ShareRequest(
                text =
                    "${config.label} is asking you to $what.\n\n" +
                        "Open this link and press Approve only if you agree:\n$web\n\n" +
                        "With the LifeSaver app: $app",
                webLink = web,
                appLink = app,
                contact = contact,
                subject = "LifeSaver approval request",
            )
        }

    fun cancel() = synchronized(lock) { clearPending() }

    /** Re-locking early is always allowed. */
    fun relock() = synchronized(lock) { clearTimer(WINDOW) }

    /** Checks the request's topic. Returns true if the request was just approved. Network call. */
    fun poll(): Boolean {
        val snapshot =
            synchronized(lock) {
                if (stateLocked() !is UnlockState.Pending) return false
                pendingApproval()?.let { it to signerKey(it.purpose) }
            } ?: return false
        val (approval, signer) = snapshot
        if (signer == null) {
            return false
        }
        val messages = relay.poll(approval.topic)
        return synchronized(lock) {
            // The request may have been cancelled, replaced or expired while we were on the network.
            if (stateLocked() !is UnlockState.Pending || store.get(PENDING_ID) != approval.requestId) {
                false
            } else {
                val approved = messages.any { approves(it, approval, signer) }
                if (approved) accept(approval.purpose)
                approved
            }
        }
    }

    private fun approves(
        message: String,
        approval: Approval,
        signer: String,
    ): Boolean {
        val parts = message.split('.')
        if (parts.size != RESPONSE_PARTS || parts[0] != approval.purpose.wire || parts[1] != approval.requestId) {
            return false
        }
        return PartnerCrypto.verify(signer, approval.message(), parts[2])
    }

    /** The key that has to sign: the candidate while pairing, otherwise the saved partner. */
    private fun signerKey(purpose: Purpose): String? =
        if (purpose == Purpose.Pair) store.get(PENDING_KEY) else store.get(PARTNER_KEY)

    private fun pendingApproval(): Approval? {
        val purpose = store.get(PENDING_PURPOSE)?.let(Purpose::fromWire) ?: return null
        val id = store.get(PENDING_ID) ?: return null
        val topic = store.get(PENDING_TOPIC) ?: return null
        val fingerprint = signerKey(purpose)?.let(PartnerCrypto::fingerprint) ?: return null
        val newKey = if (purpose == Purpose.ChangePartner) store.get(PENDING_KEY).orEmpty() else ""
        return Approval(purpose, id, topic, config.relayBase, fingerprint, newKey)
    }

    private fun createRequest(
        purpose: Purpose,
        newKey: String?,
        contact: String?,
    ) {
        store.put(PENDING_PURPOSE, purpose.wire)
        store.put(PENDING_ID, UnlockTokens.random())
        store.put(PENDING_TOPIC, UnlockTokens.random())
        store.put(PENDING_KEY, newKey)
        store.put(PENDING_CONTACT, contact?.trim()?.ifBlank { null })
        startTimer(PENDING, config.requestTtlMs)
    }

    private fun accept(purpose: Purpose) {
        when (purpose) {
            Purpose.Unlock -> startTimer(WINDOW, config.windowMs)
            Purpose.Pair, Purpose.ChangePartner -> {
                store.put(PARTNER_KEY, store.get(PENDING_KEY))
                store.put(PARTNER_CONTACT, store.get(PENDING_CONTACT))
            }
        }
        clearPending()
    }

    private fun clearPending() {
        clearTimer(PENDING)
        listOf(PENDING_PURPOSE, PENDING_ID, PENDING_TOPIC, PENDING_KEY, PENDING_CONTACT).forEach {
            store.put(it, null)
        }
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

    private companion object {
        const val PARTNER_KEY = "partnerKey"
        const val PARTNER_CONTACT = "partnerContact"
        const val PENDING = "pending"
        const val WINDOW = "window"
        const val PENDING_PURPOSE = "pendingPurpose"
        const val PENDING_ID = "pendingId"
        const val PENDING_TOPIC = "pendingTopic"
        const val PENDING_KEY = "pendingKey"
        const val PENDING_CONTACT = "pendingContact"
        const val MINUTE_MS = 60_000L
        const val RESPONSE_PARTS = 3
    }
}
