package app.lifesaver.vpn

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

enum class VpnPhase {
    Off,
    Starting,
    On,
    Error,
}

object VpnStatus {
    @Volatile
    var phase: VpnPhase = VpnPhase.Off
        private set

    private val listeners = CopyOnWriteArrayList<(VpnPhase) -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun set(next: VpnPhase) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            publish(next)
        } else {
            main.post { publish(next) }
        }
    }

    fun addListener(listener: (VpnPhase) -> Unit): () -> Unit {
        listeners.add(listener)
        listener(phase)
        return { listeners.remove(listener) }
    }

    private fun publish(next: VpnPhase) {
        phase = next
        listeners.forEach { it(next) }
    }
}
