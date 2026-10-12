package app.lifesaver.policy

enum class ApproveResult { Sent, NoKey, WrongKey, SendFailed }

/**
 * The partner's own key, for partners who use the LifeSaver app instead of the web page. The
 * private key lives only in this store, which on the partner's phone is separate from the unlock
 * state. Never put it on the phone that is being protected.
 */
class PartnerKeys(
    private val store: KeyValueStore,
) {
    fun publicKey(): String? = store.get(PUBLIC)

    fun fingerprint(): String? = publicKey()?.let(PartnerCrypto::fingerprint)

    /** Creates the key once. Returns the existing public key if there already is one. */
    fun create(): String {
        publicKey()?.let { return it }
        val pair = PartnerCrypto.generate()
        store.put(PRIVATE, pair.privateKey)
        store.put(PUBLIC, pair.publicKey)
        return pair.publicKey
    }

    /** Signs [approval] and posts the signature to its topic. Network call. */
    fun approve(
        approval: Approval,
        relay: Relay = NtfyRelay(approval.relay),
    ): ApproveResult {
        val privateKey = store.get(PRIVATE)
        if (privateKey == null || fingerprint() == null) {
            return ApproveResult.NoKey
        }
        if (fingerprint() != approval.expectedFingerprint) {
            return ApproveResult.WrongKey
        }
        val signature = PartnerCrypto.sign(privateKey, approval.message()) ?: return ApproveResult.NoKey
        return if (relay.publish(approval.topic, approval.response(signature))) {
            ApproveResult.Sent
        } else {
            ApproveResult.SendFailed
        }
    }

    private companion object {
        const val PUBLIC = "myPublicKey"
        const val PRIVATE = "myPrivateKey"
    }
}
