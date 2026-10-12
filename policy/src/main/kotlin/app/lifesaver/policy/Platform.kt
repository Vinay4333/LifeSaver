package app.lifesaver.policy

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings

/** Tiny string store so the engine can be unit-tested without Android. */
interface KeyValueStore {
    fun get(key: String): String?

    fun put(
        key: String,
        value: String?,
    )
}

class PrefsStore(
    private val prefs: SharedPreferences,
) : KeyValueStore {
    override fun get(key: String): String? = prefs.getString(key, null)

    override fun put(
        key: String,
        value: String?,
    ) {
        val editor = prefs.edit()
        if (value == null) editor.remove(key) else editor.putString(key, value)
        editor.commit()
    }
}

/** Device-on time only. Never the wall clock. */
interface MonotonicClock {
    fun elapsedRealtime(): Long

    /** Changes on every reboot. -1 when the platform does not expose it. */
    fun bootCount(): Int
}

class AndroidClock(
    private val context: Context,
) : MonotonicClock {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()

    override fun bootCount(): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
}
