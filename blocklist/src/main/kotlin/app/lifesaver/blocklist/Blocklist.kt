package app.lifesaver.blocklist

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Process-wide holder for the active [BlocklistIndex]. Readers take [current] (a volatile
 * reference) with no locking; writers build a new index and publish it.
 */
object Blocklist {
    private const val BUNDLED_ASSET = "porn-hosts.txt"

    @Volatile
    var current: BlocklistIndex = BlocklistIndex.EMPTY
        private set

    @Volatile
    private var loaded = false

    private val listeners = CopyOnWriteArrayList<(BlocklistIndex) -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    /** Loads the bundled list once. Call from a background thread. No-op after the first success. */
    @Synchronized
    fun ensureLoaded(context: Context) {
        if (!loaded) {
            reloadBundled(context)
        }
    }

    /** Re-reads the bundled asset and reapplies overrides. Call from a background thread. */
    @Synchronized
    fun reloadBundled(context: Context) {
        val app = context.applicationContext
        val parsed =
            app.assets.open(BUNDLED_ASSET).bufferedReader().use { reader ->
                HostsParser.parse(reader.lineSequence())
            }
        val overrides = Overrides(app)
        publish(BlocklistIndex(parsed.domains, parsed.version, overrides.allow, overrides.deny))
        loaded = true
    }

    /** Re-applies overrides to the already loaded base list. */
    @Synchronized
    fun applyOverrides(context: Context) {
        val overrides = Overrides(context)
        publish(current.withOverrides(overrides.allow, overrides.deny))
    }

    fun addListener(listener: (BlocklistIndex) -> Unit): () -> Unit {
        listeners.add(listener)
        listener(current)
        return { listeners.remove(listener) }
    }

    private fun publish(next: BlocklistIndex) {
        current = next
        main.post { listeners.forEach { it(next) } }
    }
}
