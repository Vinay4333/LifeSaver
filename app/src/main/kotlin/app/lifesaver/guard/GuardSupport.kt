package app.lifesaver.guard

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object GuardPackages {
    val apps: Set<String> = setOf("com.reddit.frontpage", "com.discord")

    val browsers: Set<String> =
        setOf(
            "com.android.chrome",
            "com.sec.android.app.sbrowser",
            "org.mozilla.firefox",
            "com.brave.browser",
            "com.microsoft.emmx",
            "com.opera.browser",
        )

    val all: Set<String> = apps + browsers

    fun isBrowser(pkg: String): Boolean = pkg in browsers
}

object GuardStats {
    private const val FILE = "guard"
    private const val KEY_CLOSES = "closes"

    fun recordClose(context: Context) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_CLOSES, prefs.getInt(KEY_CLOSES, 0) + 1).apply()
    }

    fun closes(context: Context): Int = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(KEY_CLOSES, 0)
}

object GuardStatus {
    fun isEnabled(context: Context): Boolean {
        val enabled =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
        val ours = ComponentName(context, ContentGuardService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
    }
}
