package app.lifesaver

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import app.lifesaver.blocklist.Blocklist
import app.lifesaver.blocklist.Overrides
import app.lifesaver.guard.GuardStats
import app.lifesaver.guard.GuardStatus
import app.lifesaver.ui.BlocklistSection
import app.lifesaver.ui.GuardSection
import app.lifesaver.ui.HomeScreen
import app.lifesaver.ui.LifeSaverTheme
import app.lifesaver.ui.protectionState
import app.lifesaver.vpn.BlockerVpnService
import app.lifesaver.vpn.VpnStatus
import java.io.IOException
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private var phase by mutableStateOf(VpnStatus.phase)
    private var index by mutableStateOf(Blocklist.current)
    private var loading by mutableStateOf(true)
    private var hasConsent by mutableStateOf(true)
    private var guardEnabled by mutableStateOf(false)
    private var guardCloses by mutableStateOf(0)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            launchVpnConsent()
        }

    private val vpnConsent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            hasConsent = VpnService.prepare(this) == null
            if (result.resultCode == RESULT_OK) {
                startVpnService()
            }
        }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        loadList(reload = false)
        setContent {
            LifeSaverTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DisposableEffect(Unit) {
                        val removeStatus = VpnStatus.addListener { phase = it }
                        val removeList = Blocklist.addListener { index = it }
                        onDispose {
                            removeStatus()
                            removeList()
                        }
                    }
                    HomeScreen(
                        versionName = versionLabel(),
                        state = protectionState(phase, hasConsent),
                        debug = isDebuggable(),
                        listVersion = index.version,
                        onStart = ::startProtection,
                        onStop = ::stopProtection,
                        guard = {
                            GuardSection(
                                enabled = guardEnabled,
                                closes = guardCloses,
                                onOpenSettings = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                            )
                        },
                        blocklist = {
                            BlocklistSection(
                                index = index,
                                loading = loading,
                                debug = isDebuggable(),
                                onReload = { loadList(reload = true) },
                                onAddDeny = ::addDeny,
                                onAddAllow = ::addAllow,
                                onRemoveDeny = ::removeDeny,
                                onRemoveAllow = ::removeAllow,
                            )
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasConsent = VpnService.prepare(this) == null
        guardEnabled = GuardStatus.isEnabled(this)
        guardCloses = GuardStats.closes(this)
    }

    private fun loadList(reload: Boolean) {
        loading = true
        thread(name = "blocklist-load") {
            try {
                if (reload) Blocklist.reloadBundled(this) else Blocklist.ensureLoaded(this)
            } catch (e: IOException) {
                Log.e(TAG, "blocklist load failed: ${e.javaClass.simpleName}")
            }
            runOnUiThread { loading = false }
        }
    }

    private fun addDeny(raw: String): Boolean = edit { it.addDeny(raw) }

    private fun addAllow(raw: String): Boolean = edit { it.addAllow(raw) }

    private fun removeDeny(name: String) {
        edit { it.removeDeny(name) }
    }

    private fun removeAllow(name: String) {
        edit { it.removeAllow(name) }
    }

    private fun <T> edit(change: (Overrides) -> T): T {
        val result = change(Overrides(this))
        Blocklist.applyOverrides(this)
        return result
    }

    private fun isDebuggable(): Boolean = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private fun startProtection() {
        val needsNotification =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
        if (needsNotification) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            launchVpnConsent()
        }
    }

    private fun launchVpnConsent() {
        val prepare = VpnService.prepare(this)
        if (prepare == null) {
            startVpnService()
        } else {
            vpnConsent.launch(prepare)
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, BlockerVpnService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopProtection() {
        val intent = Intent(this, BlockerVpnService::class.java).setAction(BlockerVpnService.ACTION_STOP)
        startService(intent)
    }

    private fun versionLabel(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager
                .getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                .versionName
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName
        } ?: "0.1.0"

    private companion object {
        const val TAG = "LifeSaver"
    }
}
