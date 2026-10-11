package app.lifesaver.blocklist

class ParsedList(
    val domains: Set<String>,
    val version: String,
)

/** Parses hosts-format lines (`0.0.0.0 example.com`) and bare-domain lines. */
object HostsParser {
    const val UNKNOWN_VERSION = "unknown"
    private const val MAX_NAME = 253
    private const val MAX_LABEL = 63

    private val sinkAddresses = setOf("0.0.0.0", "127.0.0.1", "::", "::1")
    private val ignoredNames = setOf("localhost", "localhost.localdomain", "local", "broadcasthost", "ip6-localhost")
    private val labelPattern = Regex("^[a-z0-9_]([a-z0-9_-]*[a-z0-9_])?$")
    private val datePattern = Regex("^#\\s*Date:\\s*(.+?)\\s*(\\(UTC\\))?$", RegexOption.IGNORE_CASE)
    private val whitespace = Regex("\\s+")

    fun parse(lines: Sequence<String>): ParsedList {
        val domains = HashSet<String>()
        var version: String? = null
        for (line in lines) {
            if (version == null) {
                version = datePattern.find(line.trim())?.groupValues?.get(1)
            }
            addLine(line, domains)
        }
        return ParsedList(domains, version ?: UNKNOWN_VERSION)
    }

    fun normalize(raw: String): String = raw.trim().lowercase().removeSuffix(".")

    fun isValidDomain(name: String): Boolean {
        if (name.length > MAX_NAME || '.' !in name) {
            return false
        }
        return name.split('.').all { it.length in 1..MAX_LABEL && labelPattern.matches(it) }
    }

    private fun addLine(
        line: String,
        into: MutableSet<String>,
    ) {
        val content = line.substringBefore('#').trim()
        if (content.isEmpty()) {
            return
        }
        val fields = content.split(whitespace)
        val names = if (fields.first() in sinkAddresses) fields.drop(1) else fields
        for (field in names) {
            val name = normalize(field)
            if (name !in ignoredNames && isValidDomain(name)) {
                into.add(name)
            }
        }
    }
}
