package app.lifesaver

import android.app.Application
import android.os.Build
import app.lifesaver.policy.Unlock

class LifeSaverApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Unlock.configure(
            relayBase = BuildConfig.NTFY_BASE,
            approveBase = BuildConfig.APPROVE_BASE,
            label = "LifeSaver on ${Build.MODEL}",
        )
    }
}
