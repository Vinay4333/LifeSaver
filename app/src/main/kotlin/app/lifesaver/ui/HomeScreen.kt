package app.lifesaver.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private const val COOLDOWN_HOURS = 48

@Composable
fun HomeScreen(
    versionName: String,
    state: ProtectionState,
    debug: Boolean,
    listVersion: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    guard: @Composable () -> Unit,
    blocklist: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "LifeSaver", style = MaterialTheme.typography.headlineLarge)
        StatusCard(state)
        if (state != ProtectionState.Protected && state != ProtectionState.Starting) {
            Button(onClick = onStart) { Text(startLabel(state)) }
        }
        // Stop exists only in debug builds. A hardened build has no one-tap off (M5 adds the delayed unlock).
        if (debug && (state == ProtectionState.Protected || state == ProtectionState.Starting)) {
            OutlinedButton(onClick = onStop) { Text("Stop (debug)") }
        }
        guard()
        SettingsSummary(listVersion)
        blocklist()
        Text(text = versionName, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StatusCard(state: ProtectionState) {
    val ok = state == ProtectionState.Protected
    val container = if (ok) Color(0xFF1F3D1B) else MaterialTheme.colorScheme.errorContainer
    val content = if (ok) Color(0xFFCDEFC4) else MaterialTheme.colorScheme.onErrorContainer
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.large) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = statusTitle(state), style = MaterialTheme.typography.headlineSmall)
            Text(text = statusDetail(state), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SettingsSummary(listVersion: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Settings (read-only)", style = MaterialTheme.typography.titleMedium)
        Text(
            "Unlock cooldown: $COOLDOWN_HOURS hours of device-on time. Not active yet.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("Block list version: $listVersion", style = MaterialTheme.typography.bodyMedium)
    }
}

private fun startLabel(state: ProtectionState): String =
    if (state == ProtectionState.NeedsConsent) "Allow VPN and turn on" else "Turn on protection"

private fun statusTitle(state: ProtectionState): String =
    when (state) {
        ProtectionState.Protected -> "Protected"
        ProtectionState.Starting -> "Starting"
        ProtectionState.NeedsConsent -> "Waiting for VPN permission"
        ProtectionState.Down -> "Protection is down"
        ProtectionState.Error -> "Protection failed to start"
    }

private fun statusDetail(state: ProtectionState): String =
    when (state) {
        ProtectionState.Protected -> "Blocked sites do not load."
        ProtectionState.Starting -> "Setting up the VPN."
        ProtectionState.NeedsConsent -> "Android needs your OK before LifeSaver can filter DNS."
        ProtectionState.Down -> "Nothing is being blocked right now."
        ProtectionState.Error -> "Try again. If it keeps failing, check that no other VPN is active."
    }
