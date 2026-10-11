package app.lifesaver.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lifesaver.vpn.VpnPhase

@Composable
fun HomeScreen(
    versionName: String,
    phase: VpnPhase,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "LifeSaver", style = MaterialTheme.typography.headlineLarge)
        Text(text = statusText(phase), style = MaterialTheme.typography.titleMedium)
        Button(onClick = onStart, enabled = phase != VpnPhase.On && phase != VpnPhase.Starting) {
            Text("Start")
        }
        Button(onClick = onStop, enabled = phase == VpnPhase.On || phase == VpnPhase.Starting) {
            Text("Stop")
        }
        Text(text = versionName, style = MaterialTheme.typography.bodySmall)
    }
}

private fun statusText(phase: VpnPhase): String =
    when (phase) {
        VpnPhase.Off -> "Protection is off"
        VpnPhase.Starting -> "Starting protection"
        VpnPhase.On -> "Protection is on"
        VpnPhase.Error -> "Protection failed to start"
    }
