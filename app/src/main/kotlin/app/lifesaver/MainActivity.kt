package app.lifesaver

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
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
import app.lifesaver.guard.GuardStats
import app.lifesaver.guard.GuardStatus
import app.lifesaver.policy.Approval
import app.lifesaver.policy.ApproveLink
import app.lifesaver.policy.ApproveResult
import app.lifesaver.policy.RequestResult
import app.lifesaver.policy.Unlock
import app.lifesaver.policy.UnlockState
import app.lifesaver.ui.ApproveActions
import app.lifesaver.ui.ApproveSection
import app.lifesaver.ui.BlocklistSection
import app.lifesaver.ui.GuardSection
import app.lifesaver.ui.HomeScreen
import app.lifesaver.ui.LifeSaverTheme
import app.lifesaver.ui.PartnerInfo
import app.lifesaver.ui.UnlockActions
import app.lifesaver.ui.UnlockSection
import app.lifesaver.ui.protectionState
import app.lifesaver.vpn.BlockerVpnService
import app.lifesaver.vpn.VpnStatus
import kotlinx.coroutines.delay
import java.io.IOException
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private val engine by lazy { Unlock.engine(this) }
    private val partnerKeys by lazy { Unlock.partnerKeys(this) }
    private val overrides by lazy { OverrideEditor(this) }

    private var phase by mutableStateOf(VpnStatus.phase)
    private var index by mutableStateOf(Blocklist.current)
    private var loading by mutableStateOf(true)
    private var hasConsent by mutableStateOf(true)
    private var guardEnabled by mutableStateOf(false)
    private var guardCloses by mutableStateOf(0)
    private var unlockState by mutableStateOf<UnlockState>(UnlockState.NoPartner)
    private var partner by mutableStateOf(PartnerInfo(null, null, null))
    private var unlockMessage by mutableStateOf<String?>(null)
    private var myPublicKey by mutableStateOf<String?>(null)
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
                refreshUnlock()
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
            unlock = { UnlockSection(unlockState, partner, unlockMessage, unlockActions) },
            approve = {
                ApproveSection(
                    myPublicKey = myPublicKey,
                    myFingerprint = myPublicKey?.let { partnerKeys.fingerprint() },
                    incoming = incoming,
                    busy = approveBusy,
                    message = approveMessage,
                    actions = approveActions,
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
                    onAddDeny = overrides::addDeny,
                    onAddAllow = overrides::addAllow,
                    onRemoveDeny = overrides::removeDeny,
                    onRemoveAllow = overrides::removeAllow,
                )
            },
        )
    }

    private fun refreshUnlock() {
        unlockState = engine.state()
        partner = PartnerInfo(engine.partnerFingerprint(), engine.partnerContact(), engine.pendingFingerprint())
        myPublicKey = partnerKeys.publicKey()
    }

    private val unlockActions =
        UnlockActions(
            onBeginPairing = { key, contact -> setMessage(pairingText(engine.beginPairing(key.trim(), contact))) },
            onRequestUnlock = { setMessage(requestText(engine.requestUnlock())) },
            onChangePartner = { key, contact ->
                setMessage(pairingText(engine.requestChangePartner(key.trim(), contact)))
            },
            onShare = ::shareRequest,
            onEmail = ::emailRequest,
            onCancel = { engine.cancel() },
            onRelock = { engine.relock() },
        )

    private val approveActions =
        ApproveActions(
            onCreateKey = {
                partnerKeys.create()
                refreshUnlock()
            },
            onCopyKey = { copy("Partner public key", partnerKeys.publicKey().orEmpty()) },
            onCheckLink = ::checkLink,
            onApprove = ::approve,
            onDismiss = { incoming = null },
        )

    private fun setMessage(text: String) {
        unlockMessage = text
        refreshUnlock()
    }

    private fun pairingText(result: RequestResult): String =
        when (result) {
            RequestResult.Created -> "Started. Compare the fingerprint with your partner, then send the request."
            RequestResult.NotAllowed -> "Not possible right now: a request is already open."
            RequestResult.InvalidKey -> "That is not a valid partner key (or it is your current partner's key)."
        }

    private fun requestText(result: RequestResult): String =
        when (result) {
            RequestResult.Created -> "Request created. Send it to your partner; nothing is sent until you do."
            else -> pairingText(result)
        }

    private fun shareRequest() {
        val share = engine.share() ?: return
        val send =
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, share.subject)
                .putExtra(Intent.EXTRA_TEXT, share.text)
        startActivity(Intent.createChooser(send, "Send the request"))
    }

    private fun emailRequest() {
        val share = engine.share() ?: return
        val mail =
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                .putExtra(Intent.EXTRA_EMAIL, arrayOf(share.contact.orEmpty()))
                .putExtra(Intent.EXTRA_SUBJECT, share.subject)
                .putExtra(Intent.EXTRA_TEXT, share.text)
        if (mail.resolveActivity(packageManager) != null) {
            startActivity(mail)
        } else {
            unlockMessage = "No email app found. Use \"Send the request\" instead."
        }
    }

    private fun copy(
        label: String,
        text: String,
    ) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
        approveMessage = "Copied."
    }

    private fun handleLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "lifesaver") {
            checkLink(data.toString())
        }
    }

    private fun checkLink(text: String) {
        incoming = ApproveLink.parse(text)
        approveMessage = if (incoming == null) "That is not a LifeSaver approval link." else null
    }

    private fun approve(approval: Approval) {
        approveBusy = true
        approveMessage = "Signing and sending..."
        thread(name = "approve") {
            val result = partnerKeys.approve(approval)
            runOnUiThread {
                approveBusy = false
                if (result == ApproveResult.Sent) incoming = null
                approveMessage = approveText(result)
            }
        }
    }

    private fun approveText(result: ApproveResult): String =
        when (result) {
            ApproveResult.Sent -> "Approved. Their phone picks it up within a few seconds."
            ApproveResult.NoKey -> "Create your partner key first."
            ApproveResult.WrongKey -> "This request is for a different key than yours. Do not approve."
            ApproveResult.SendFailed -> "Could not reach the relay. Try again."
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
