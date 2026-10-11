package app.lifesaver.vpn

/**
 * Exact and parent-label match. `www.example.com` matches a list entry of `example.com`.
 * `notexample.com` does not.
 */
class DomainBlocklist(
    names: Set<String>,
) {
    private val blocked: Set<String> = names.map(::normalize).filter { it.isNotEmpty() }.toSet()

    fun isBlocked(raw: String): Boolean {
        var name = normalize(raw)
        if (name.isEmpty()) {
            return false
        }
        while (true) {
            if (name in blocked) {
                return true
            }
            val dot = name.indexOf('.')
            if (dot < 0 || dot == name.lastIndex) {
                return false
            }
            name = name.substring(dot + 1)
        }
    }

    fun blocksAny(names: List<String>): Boolean = names.any(::isBlocked)

    companion object {
        fun normalize(raw: String): String = raw.trim().lowercase().removeSuffix(".")

        /** Tiny M1 list. A real list replaces this in M2. */
        val hardcoded: DomainBlocklist =
            DomainBlocklist(
                setOf(
                    "example.com",
                    "pornhub.com",
                    "xvideos.com",
                ),
            )
    }
}
