package app.lifesaver

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
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
import app.lifesaver.ui.HomeScreen
import app.lifesaver.ui.LifeSaverTheme
import app.lifesaver.vpn.BlockerVpnService
import app.lifesaver.vpn.VpnStatus

class MainActivity : ComponentActivity() {
    private var phase by mutableStateOf(VpnStatus.phase)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            launchVpnConsent()
        }

    private val vpnConsent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                startVpnService()
            }
        }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LifeSaverTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DisposableEffect(Unit) {
                        val remove = VpnStatus.addListener { phase = it }
                        onDispose(remove)
                    }
                    HomeScreen(
                        versionName = versionLabel(),
                        phase = phase,
                        onStart = ::startProtection,
                        onStop = ::stopProtection,
                    )
                }
            }
        }
    }

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
}
