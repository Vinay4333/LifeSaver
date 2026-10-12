package app.lifesaver.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lifesaver.policy.Purpose
import app.lifesaver.policy.UnlockState

class PartnerInfo(
    val fingerprint: String?,
    val contact: String?,
    /** While pairing or changing partner: the candidate's fingerprint, to compare out loud. */
    val pendingFingerprint: String?,
)

class UnlockActions(
    val onBeginPairing: (key: String, contact: String) -> Unit,
    val onRequestUnlock: () -> Unit,
    val onChangePartner: (key: String, contact: String) -> Unit,
    val onShare: () -> Unit,
    val onEmail: () -> Unit,
    val onCancel: () -> Unit,
    val onRelock: () -> Unit,
)

@Composable
fun UnlockSection(
    state: UnlockState,
    partner: PartnerInfo,
    message: String?,
    actions: UnlockActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Accountability partner", style = MaterialTheme.typography.titleMedium)
        when (state) {
            UnlockState.NoPartner -> PairingForm(actions)
            UnlockState.Idle -> IdleBody(partner, actions)
            is UnlockState.Pending -> PendingBody(state, partner, actions)
            is UnlockState.Open -> {
                Text("Unlocked by your partner.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = actions.onRelock) { Text("Lock now") }
            }
        }
        if (message != null) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PairingForm(actions: UnlockActions) {
    Text(
        "Your partner opens the approve page on their own device, creates a key there, and sends you " +
            "the public key. Never make the key yourself: whoever holds it is the partner.",
        style = MaterialTheme.typography.bodyMedium,
    )
    KeyForm(
        "Partner's public key",
        "Partner's email (optional, for the draft)",
        "Start pairing",
        actions.onBeginPairing,
    )
}

@Composable
private fun IdleBody(
    partner: PartnerInfo,
    actions: UnlockActions,
) {
    val contact = partner.contact?.let { " ($it)" }.orEmpty()
    Text("Partner key: ${partner.fingerprint}$contact", style = MaterialTheme.typography.bodyMedium)
    Button(onClick = actions.onRequestUnlock) { Text("Request unlock") }
    Text("Change partner (needs your current partner to sign)", style = MaterialTheme.typography.labelLarge)
    KeyForm(
        "New partner's public key",
        "New partner's email (optional)",
        "Ask to change partner",
        actions.onChangePartner,
    )
}

@Composable
private fun PendingBody(
    state: UnlockState.Pending,
    partner: PartnerInfo,
    actions: UnlockActions,
) {
    val what =
        when (state.purpose) {
            Purpose.Pair -> "pairing"
            Purpose.Unlock -> "unlock"
            Purpose.ChangePartner -> "partner change"
        }
    Text(
        "Waiting for your partner to sign the $what. Expires in ${formatMinutes(state.remainingMs)}.",
        style = MaterialTheme.typography.bodyMedium,
    )
    if (partner.pendingFingerprint != null) {
        Text(
            "Compare with your partner out loud or by text: ${partner.pendingFingerprint}",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Button(onClick = actions.onShare) { Text("Send the request") }
    OutlinedButton(onClick = actions.onEmail) { Text("Email the request") }
    OutlinedButton(onClick = actions.onCancel) { Text("Cancel request") }
}

@Composable
private fun KeyForm(
    keyLabel: String,
    contactLabel: String,
    action: String,
    onSubmit: (String, String) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    OutlinedTextField(
        value = key,
        onValueChange = { key = it },
        label = { Text(keyLabel) },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = contact,
        onValueChange = { contact = it },
        label = { Text(contactLabel) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(
        onClick = {
            onSubmit(key, contact)
            key = ""
            contact = ""
        },
        enabled = key.isNotBlank(),
    ) { Text(action) }
}
