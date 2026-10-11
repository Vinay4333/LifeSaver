package app.lifesaver.blocklist

import android.content.Context

/** User allow / deny entries. Tiny, so plain SharedPreferences is enough. */
class Overrides(
    context: Context,
) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    val allow: Set<String> get() = read(KEY_ALLOW)
    val deny: Set<String> get() = read(KEY_DENY)

    /** Returns false when the name is not a valid domain. */
    fun addAllow(raw: String): Boolean = add(KEY_ALLOW, raw)

    fun addDeny(raw: String): Boolean = add(KEY_DENY, raw)

    fun removeAllow(name: String) = remove(KEY_ALLOW, name)

    fun removeDeny(name: String) = remove(KEY_DENY, name)

    private fun add(
        key: String,
        raw: String,
    ): Boolean {
        val name = HostsParser.normalize(raw)
        if (!HostsParser.isValidDomain(name)) {
            return false
        }
        prefs.edit().putStringSet(key, read(key) + name).commit()
        return true
    }

    private fun remove(
        key: String,
        name: String,
    ) {
        prefs.edit().putStringSet(key, read(key) - name).commit()
    }

    private fun read(key: String): Set<String> = prefs.getStringSet(key, null)?.toSet() ?: emptySet()

    private companion object {
        const val FILE = "blocklist_overrides"
        const val KEY_ALLOW = "allow"
        const val KEY_DENY = "deny"
    }
}
