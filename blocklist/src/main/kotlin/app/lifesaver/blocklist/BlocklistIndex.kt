package app.lifesaver.blocklist

/**
 * Immutable snapshot: a shared base set plus small user overrides.
 *
 * Labels are checked from most specific to least specific. At each level an allow entry beats a
 * block entry, and the first level with any entry decides. So `allow a.example.com` unblocks that
 * subtree of a blocked `example.com`, and `block bad.example.com` stays blocked under
 * `allow example.com`.
 *
 * [resolvers] are known DoH / DoT hostnames. They are checked first and no allow entry can lift
 * them, so an app that wants encrypted DNS falls back to the system resolver (this app).
 */
class BlocklistIndex(
    private val base: Set<String>,
    val version: String,
    val allow: Set<String> = emptySet(),
    val deny: Set<String> = emptySet(),
    private val resolvers: Set<String> = emptySet(),
) {
    val baseCount: Int get() = base.size
    val resolverCount: Int get() = resolvers.size

    fun withOverrides(
        allow: Set<String>,
        deny: Set<String>,
    ): BlocklistIndex = BlocklistIndex(base, version, allow, deny, resolvers)

    fun isBlocked(raw: String): Boolean {
        var name = HostsParser.normalize(raw)
        if (isResolver(name)) {
            return true
        }
        while (name.isNotEmpty()) {
            if (name in allow) {
                return false
            }
            if (name in deny || name in base) {
                return true
            }
            val dot = name.indexOf('.')
            if (dot < 0) {
                return false
            }
            name = name.substring(dot + 1)
        }
        return false
    }

    private fun isResolver(normalized: String): Boolean {
        var name = normalized
        while (name.isNotEmpty()) {
            if (name in resolvers) {
                return true
            }
            val dot = name.indexOf('.')
            if (dot < 0) {
                return false
            }
            name = name.substring(dot + 1)
        }
        return false
    }

    fun blocksAny(names: List<String>): Boolean = names.any(::isBlocked)

    companion object {
        val EMPTY = BlocklistIndex(emptySet(), HostsParser.UNKNOWN_VERSION)
    }
}
