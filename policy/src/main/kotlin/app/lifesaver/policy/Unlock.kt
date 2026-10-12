package app.lifesaver.policy

import android.content.Context

/** Process-wide access to the [UnlockEngine]. [configure] runs once from `Application.onCreate`. */
object Unlock {
    private const val PREFS = "unlock"
    private const val PARTNER_PREFS = "partner"

    private var relayBase = "https://ntfy.sh"
    private var approveBase = ""
    private var label = "LifeSaver"

    @Volatile
    private var engine: UnlockEngine? = null

    fun configure(
        relayBase: String,
        approveBase: String,
        label: String,
    ) {
        this.relayBase = relayBase
        this.approveBase = approveBase
        this.label = label
    }

    fun engine(context: Context): UnlockEngine =
        engine ?: synchronized(this) {
            engine ?: build(context.applicationContext).also { engine = it }
        }

    fun isOpen(context: Context): Boolean = engine(context).isOpen()

    /** The partner's own key store, for approving in this app. Separate file from the unlock state. */
    fun partnerKeys(context: Context): PartnerKeys =
        PartnerKeys(PrefsStore(context.applicationContext.getSharedPreferences(PARTNER_PREFS, Context.MODE_PRIVATE)))

    private fun build(context: Context): UnlockEngine =
        UnlockEngine(
            store = PrefsStore(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)),
            clock = AndroidClock(context),
            relay = NtfyRelay(relayBase),
            config = UnlockConfig(approveBase = approveBase, relayBase = relayBase, label = label),
        )
}
