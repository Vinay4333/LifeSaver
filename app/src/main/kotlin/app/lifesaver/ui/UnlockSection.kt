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

@Composable
fun UnlockSection(
    state: UnlockState,
    partnerEmail: String?,
    busy: Boolean,
    message: String?,
    onSetupPartner: (String) -> Unit,
    onRequest: () -> Unit,
    onCancel: () -> Unit,
    onRelock: () -> Unit,
    onChangePartner: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Accountability partner", style = MaterialTheme.typography.titleMedium)
        when (state) {
            UnlockState.NoPartner -> {
                Text(
                    "Enter your partner's email. They approve every unlock. " +
                        "Set once; changing it later needs their approval.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                EmailRow("Partner email", "Save partner", enabled = !busy, onSubmit = onSetupPartner)
            }
            UnlockState.Idle -> {
                Text("Partner: $partnerEmail", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onRequest, enabled = !busy) { Text("Request unlock") }
                EmailRow("New partner email", "Ask to change partner", enabled = !busy, onSubmit = onChangePartner)
            }
            is UnlockState.Pending -> {
                val what = if (state.purpose == Purpose.Unlock) "unlock" else "partner change"
                Text(
                    "Waiting for $partnerEmail to approve the $what. Expires in ${formatMinutes(state.remainingMs)}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = onCancel) { Text("Cancel request") }
            }
            is UnlockState.Open -> {
                Text("Unlocked by your partner.", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onRelock) { Text("Lock now") }
            }
        }
        if (message != null) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun EmailRow(
    label: String,
    action: String,
    enabled: Boolean,
    onSubmit: (String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(
        onClick = {
            onSubmit(email)
            email = ""
        },
        enabled = enabled && email.isNotBlank(),
    ) { Text(action) }
}
