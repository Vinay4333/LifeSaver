package app.lifesaver.policy

import java.net.URLDecoder
import java.net.URLEncoder

enum class Purpose(
    val wire: String,
) {
    Unlock("unlock"),
    ChangePartner("partner"),
    ;

    companion object {
        fun fromWire(value: String): Purpose? = entries.firstOrNull { it.wire == value }
    }
}

/** What the partner's link carries. [token] is the secret; [topic] is where approval is posted. */
class Approval(
    val purpose: Purpose,
    val token: String,
    val topic: String,
    val relay: String,
) {
    /** The message the phone is waiting for. */
    fun message(): String = "${purpose.wire}.$token"
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
        val query = trimmed.substringAfter('#', trimmed.substringAfter('?', trimmed))
        val params =
            query
                .split('&')
                .mapNotNull {
                    val key = it.substringBefore('=', "")
                    if (key.isEmpty()) {
                        null
                    } else {
                        key to
                            URLDecoder.decode(it.substringAfter('=', ""), Charsets.UTF_8.name())
                    }
                }.toMap()
        val purpose = params["p"]?.let(Purpose::fromWire) ?: return null
        val token = params["t"]?.takeIf { it.isNotBlank() } ?: return null
        val topic = params["topic"]?.takeIf { it.isNotBlank() } ?: return null
        val relay = params["relay"]?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return null
        return Approval(purpose, token, topic, relay)
    }

    /** Partner side: posts the approval. Network call. */
    fun approve(
        approval: Approval,
        relay: Relay = NtfyRelay(approval.relay),
    ): Boolean = relay.publish(approval.topic, approval.message())

    private fun query(approval: Approval): String =
        listOf(
            "p" to approval.purpose.wire,
            "t" to approval.token,
            "topic" to approval.topic,
            "relay" to approval.relay,
        ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, Charsets.UTF_8.name())}" }
}
