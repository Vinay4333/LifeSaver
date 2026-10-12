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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import app.lifesaver.blocklist.Blocklist
import app.lifesaver.blocklist.Overrides
import app.lifesaver.guard.GuardStats
import app.lifesaver.guard.GuardStatus
import app.lifesaver.policy.Approval
import app.lifesaver.policy.ApproveLink
import app.lifesaver.policy.RequestResult
import app.lifesaver.policy.Unlock
import app.lifesaver.policy.UnlockState
import app.lifesaver.ui.ApproveSection
import app.lifesaver.ui.BlocklistSection
import app.lifesaver.ui.GuardSection
import app.lifesaver.ui.HomeScreen
import app.lifesaver.ui.LifeSaverTheme
import app.lifesaver.ui.UnlockSection
import app.lifesaver.ui.protectionState
import app.lifesaver.vpn.BlockerVpnService
import app.lifesaver.vpn.VpnStatus
import kotlinx.coroutines.delay
import java.io.IOException
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private val engine by lazy { Unlock.engine(this) }

    private var phase by mutableStateOf(VpnStatus.phase)
    private var index by mutableStateOf(Blocklist.current)
    private var loading by mutableStateOf(true)
    private var hasConsent by mutableStateOf(true)
    private var guardEnabled by mutableStateOf(false)
    private var guardCloses by mutableStateOf(0)
    private var unlockState by mutableStateOf<UnlockState>(UnlockState.NoPartner)
    private var partnerEmail by mutableStateOf<String?>(null)
    private var unlockBusy by mutableStateOf(false)
    private var unlockMessage by mutableStateOf<String?>(null)
    private var incoming by mutableStateOf<Approval?>(null)
    private var approveBusy by mutableStateOf(false)
    private var approveMessage by mutableStateOf<String?>(null)

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
        handleLink(intent)
        setContent {
            LifeSaverTheme {
                Surface(modifier = Modifier.fillMaxSize()) { Screen() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    override fun onResume() {
        super.onResume()
        hasConsent = VpnService.prepare(this) == null
        guardEnabled = GuardStatus.isEnabled(this)
        guardCloses = GuardStats.closes(this)
    }

    @Composable
    private fun Screen() {
        DisposableEffect(Unit) {
            val removeStatus = VpnStatus.addListener { phase = it }
            val removeList = Blocklist.addListener { index = it }
            onDispose {
                removeStatus()
                removeList()
            }
        }
        LaunchedEffect(Unit) {
            while (true) {
                unlockState = engine.state()
                partnerEmail = engine.partnerEmail()
                delay(TICK_MS)
            }
        }
        val open = unlockState as? UnlockState.Open
        HomeScreen(
            versionName = versionLabel(),
            state = protectionState(phase, hasConsent, unlocked = open != null),
            unlockRemainingMs = open?.remainingMs ?: 0,
            listVersion = index.version,
            onStart = ::startProtection,
            unlock = {
                UnlockSection(
                    state = unlockState,
                    partnerEmail = partnerEmail,
                    busy = unlockBusy,
                    message = unlockMessage,
                    onSetupPartner = ::setupPartner,
                    onRequest = { request { engine.requestUnlock() } },
                    onCancel = { engine.cancel() },
                    onRelock = { engine.relock() },
                    onChangePartner = { email -> request { engine.requestChangePartner(email) } },
                )
            },
            approve = {
                ApproveSection(
                    incoming = incoming,
                    busy = approveBusy,
                    message = approveMessage,
                    onCheckLink = ::checkLink,
                    onApprove = ::approve,
                    onDismiss = { incoming = null },
                )
            },
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
                    // Allow / remove weaken protection: debug builds, and only inside an unlock window.
                    debug = isDebuggable() && open != null,
                    onReload = { loadList(reload = true) },
                    onAddDeny = ::addDeny,
                    onAddAllow = ::addAllow,
                    onRemoveDeny = ::removeDeny,
                    onRemoveAllow = ::removeAllow,
                )
            },
        )
    }

    private fun setupPartner(email: String) {
        unlockMessage = if (engine.setupPartner(email)) "Partner saved." else "Enter a valid email address."
    }

    private fun request(action: () -> RequestResult) {
        unlockBusy = true
        unlockMessage = "Sending the request..."
        thread(name = "unlock-request") {
            val result = action()
            runOnUiThread {
                unlockBusy = false
                unlockMessage =
                    when (result) {
                        RequestResult.Sent -> "Request sent. Your partner has to approve it."
                        RequestResult.NotAllowed -> "A request is already open, or no partner is set."
                        RequestResult.InvalidEmail -> "Enter a valid email address."
                        RequestResult.SendFailed -> "Could not send the email. Check your connection and try again."
                    }
            }
        }
    }

    private fun handleLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "lifesaver") {
            incoming = ApproveLink.parse(data.toString())
            approveMessage = if (incoming == null) "That approval link is not valid." else null
        }
    }

    private fun checkLink(text: String) {
        incoming = ApproveLink.parse(text)
        approveMessage = if (incoming == null) "That is not a LifeSaver approval link." else null
    }

    private fun approve(approval: Approval) {
        approveBusy = true
        approveMessage = "Sending your approval..."
        thread(name = "approve") {
            val ok = ApproveLink.approve(approval)
            runOnUiThread {
                approveBusy = false
                if (ok) incoming = null
                approveMessage =
                    if (ok) {
                        "Approved. Their phone unlocks within a few seconds."
                    } else {
                        "Could not reach the relay. Try again."
                    }
            }
        }
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
        const val TICK_MS = 1000L
    }
}
