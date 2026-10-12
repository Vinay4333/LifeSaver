package app.lifesaver

import android.content.Context
import app.lifesaver.blocklist.Blocklist
import app.lifesaver.blocklist.Overrides

/** Applies an override change and republishes the active list. */
class OverrideEditor(
    private val context: Context,
) {
    fun addDeny(raw: String): Boolean = edit { it.addDeny(raw) }

    fun addAllow(raw: String): Boolean = edit { it.addAllow(raw) }

    fun removeDeny(name: String) {
        edit { it.removeDeny(name) }
    }

    fun removeAllow(name: String) {
        edit { it.removeAllow(name) }
    }

    private fun <T> edit(change: (Overrides) -> T): T {
        val result = change(Overrides(context))
        Blocklist.applyOverrides(context)
        return result
    }
}
