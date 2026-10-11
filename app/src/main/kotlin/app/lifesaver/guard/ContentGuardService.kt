package app.lifesaver.guard

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import app.lifesaver.blocklist.NsfwTextMatcher

/**
 * Sends the user to the home screen when Reddit / Discord (or a browser on one of their sites)
 * shows an NSFW label. Reads visible text only. Nothing read is stored or logged; only a counter
 * and the match category are.
 */
class ContentGuardService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var scanPending = false
    private var pausedUntil = 0L
    private var lastPackage: String? = null

    private val scan = Runnable { scanNow() }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg !in GuardPackages.all) {
            return
        }
        lastPackage = pkg
        if (!scanPending && SystemClock.elapsedRealtime() >= pausedUntil) {
            scanPending = true
            handler.postDelayed(scan, SCAN_DELAY_MS)
        }
    }

    override fun onInterrupt() {
        handler.removeCallbacks(scan)
        scanPending = false
    }

    override fun onDestroy() {
        handler.removeCallbacks(scan)
        super.onDestroy()
    }

    private fun scanNow() {
        scanPending = false
        val pkg = lastPackage ?: return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) {
            return
        }
        val texts = ArrayList<String>()
        collect(root, texts, intArrayOf(0))
        val reason = NsfwTextMatcher.match(texts, GuardPackages.isBrowser(pkg)) ?: return
        Log.d(TAG, "close: $reason")
        GuardStats.recordClose(this)
        pausedUntil = SystemClock.elapsedRealtime() + PAUSE_MS
        Toast.makeText(this, "LifeSaver closed this screen", Toast.LENGTH_SHORT).show()
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun collect(
        node: AccessibilityNodeInfo,
        out: MutableList<String>,
        visited: IntArray,
    ) {
        if (visited[0]++ >= MAX_NODES || !node.isVisibleToUser) {
            return
        }
        node.text?.let { out.add(it.toString()) }
        node.contentDescription?.let { out.add(it.toString()) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collect(it, out, visited) }
        }
    }

    private companion object {
        const val TAG = "ContentGuard"
        const val SCAN_DELAY_MS = 350L
        const val PAUSE_MS = 1500L
        const val MAX_NODES = 1500
    }
}
