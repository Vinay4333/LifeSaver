package app.lifesaver.policy

import java.net.URLDecoder
import java.net.URLEncoder

enum class Purpose(
    val wire: String,
) {
    Pair("pair"),
    Unlock("unlock"),
    ChangePartner("partner"),
    ;

    companion object {
        fun fromWire(value: String): Purpose? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * A request the partner is asked to sign. Nothing here is secret: [requestId] is random, [topic] is
 * where the signature is posted, [expectedFingerprint] names the key that has to sign, and [newKey]
 * is the replacement public key for a partner change.
 */
class Approval(
    val purpose: Purpose,
    val requestId: String,
    val topic: String,
    val relay: String,
    val expectedFingerprint: String,
    val newKey: String = "",
) {
    /** The exact text the partner's key signs. */
    fun message(): String = PartnerCrypto.message(purpose, requestId, newKey)

    /** What the partner posts to [topic]. The phone ignores anything that does not verify. */
    fun response(signature: String): String = "${purpose.wire}.$requestId.$signature"
}

object ApproveLink {
    const val APP_SCHEME_LINK = "lifesaver://approve"

    fun web(
        approveBase: String,
        approval: Approval,
    ): String = "$approveBase#${query(approval)}"

    fun app(approval: Approval): String = "$APP_SCHEME_LINK?${query(approval)}"

    /** Accepts the web link (`#...`), the app link (`?...`), or a bare query string. */
    fun parse(text: String): Approval? {
        val trimmed = text.trim()
        val params = params(trimmed.substringAfter('#', trimmed.substringAfter('?', trimmed)))
        val purpose = params["p"]?.let(Purpose::fromWire)
        val requestId = params["r"].orEmpty()
        val topic = params["topic"].orEmpty()
        val fingerprint = params["fp"].orEmpty()
        val relay = params["relay"].orEmpty()
        val newKey = params["n"].orEmpty()
        val complete =
            purpose != null &&
                listOf(requestId, topic, fingerprint).none { it.isBlank() } &&
                (relay.startsWith("https://") || relay.startsWith("http://")) &&
                (purpose != Purpose.ChangePartner || PartnerCrypto.isValidPublicKey(newKey))
        return purpose?.takeIf { complete }?.let { Approval(it, requestId, topic, relay, fingerprint, newKey) }
    }

    private fun params(query: String): Map<String, String> =
        query
            .split('&')
            .mapNotNull {
                val key = it.substringBefore('=', "")
                if (key.isEmpty()) {
                    null
                } else {
                    key to URLDecoder.decode(it.substringAfter('=', ""), Charsets.UTF_8.name())
                }
            }.toMap()

    private fun query(approval: Approval): String =
        buildList {
            add("p" to approval.purpose.wire)
            add("r" to approval.requestId)
            add("topic" to approval.topic)
            add("relay" to approval.relay)
            add("fp" to approval.expectedFingerprint)
            if (approval.newKey.isNotEmpty()) add("n" to approval.newKey)
        }.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, Charsets.UTF_8.name())}" }
}
