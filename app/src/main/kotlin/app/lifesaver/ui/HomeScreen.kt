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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    versionName: String,
    state: ProtectionState,
    unlockRemainingMs: Long,
    listVersion: String,
    onStart: () -> Unit,
    unlock: @Composable () -> Unit,
    approve: @Composable () -> Unit,
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
        StatusCard(state, unlockRemainingMs)
        // No Stop button in any build. Protection is paused only by a partner-approved unlock (M5).
        if (state != ProtectionState.Protected &&
            state != ProtectionState.Unlocked &&
            state != ProtectionState.Starting
        ) {
            Button(onClick = onStart) { Text(startLabel(state)) }
        }
        unlock()
        guard()
        SettingsSummary(listVersion)
        blocklist()
        approve()
        Text(text = versionName, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun StatusCard(
    state: ProtectionState,
    unlockRemainingMs: Long,
) {
    val container: Color
    val content: Color
    when (state) {
        ProtectionState.Protected -> {
            container = Color(0xFF1F3D1B)
            content = Color(0xFFCDEFC4)
        }
        ProtectionState.Unlocked -> {
            container = Color(0xFF4A3B12)
            content = Color(0xFFF5E2A8)
        }
        else -> {
            container = MaterialTheme.colorScheme.errorContainer
            content = MaterialTheme.colorScheme.onErrorContainer
        }
    }
    Surface(color = container, contentColor = content, shape = MaterialTheme.shapes.large) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = statusTitle(state), style = MaterialTheme.typography.headlineSmall)
            Text(text = statusDetail(state, unlockRemainingMs), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SettingsSummary(listVersion: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Settings (read-only)", style = MaterialTheme.typography.titleMedium)
        Text(
            "Unlock: your partner approves by email link, then protection pauses for 15 minutes. No fallback.",
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
        ProtectionState.Unlocked -> "Unlocked"
        ProtectionState.Starting -> "Starting"
        ProtectionState.NeedsConsent -> "Waiting for VPN permission"
        ProtectionState.Down -> "Protection is down"
        ProtectionState.Error -> "Protection failed to start"
    }

private fun statusDetail(
    state: ProtectionState,
    unlockRemainingMs: Long,
): String =
    when (state) {
        ProtectionState.Protected -> "Blocked sites do not load."
        ProtectionState.Unlocked -> "Protection is paused. It comes back on in ${formatMinutes(unlockRemainingMs)}."
        ProtectionState.Starting -> "Setting up the VPN."
        ProtectionState.NeedsConsent -> "Android needs your OK before LifeSaver can filter DNS."
        ProtectionState.Down -> "Nothing is being blocked right now."
        ProtectionState.Error -> "Try again. If it keeps failing, check that no other VPN is active."
    }

fun formatMinutes(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val minutes = total / 60
    return if (minutes >= 60) {
        "%d:%02d:%02d".format(minutes / 60, minutes % 60, total % 60)
    } else {
        "%d:%02d".format(minutes, total % 60)
    }
}
