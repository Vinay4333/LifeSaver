package app.lifesaver.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
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
import app.lifesaver.policy.Approval
import app.lifesaver.policy.Purpose

/**
 * For the partner, when they use this app instead of the web page. Holds the partner's own key and
 * signs requests with it. Use this on the partner's phone, never on the phone being protected.
 */
@Composable
fun ApproveSection(
    myPublicKey: String?,
    myFingerprint: String?,
    incoming: Approval?,
    busy: Boolean,
    message: String?,
    actions: ApproveActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("If you are someone's partner", style = MaterialTheme.typography.titleMedium)
        PartnerKeyBlock(myPublicKey, myFingerprint, actions)
        if (incoming == null) {
            LinkField(actions.onCheckLink)
        } else {
            IncomingCard(incoming, busy, actions)
        }
        if (message != null) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PartnerKeyBlock(
    publicKey: String?,
    fingerprint: String?,
    actions: ApproveActions,
) {
    if (publicKey == null) {
        Text(
            "Create your partner key here, then send the public key to the person you support. " +
                "Do this on your own phone.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = actions.onCreateKey) { Text("Create my partner key") }
    } else {
        Text("Your fingerprint: $fingerprint", style = MaterialTheme.typography.bodyMedium)
        SelectionContainer { Text(publicKey, style = MaterialTheme.typography.bodySmall) }
        OutlinedButton(onClick = actions.onCopyKey) { Text("Copy my public key") }
    }
}

@Composable
private fun LinkField(onCheckLink: (String) -> Unit) {
    var link by remember { mutableStateOf("") }
    Text("Paste the link they sent you to review it.", style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(
        value = link,
        onValueChange = { link = it },
        label = { Text("Approval link") },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(onClick = { onCheckLink(link) }, enabled = link.isNotBlank()) { Text("Review link") }
}

@Composable
private fun IncomingCard(
    incoming: Approval,
    busy: Boolean,
    actions: ApproveActions,
) {
    val what =
        when (incoming.purpose) {
            Purpose.Pair -> "confirm you are their accountability partner"
            Purpose.Unlock -> "pause their protection for 15 minutes"
            Purpose.ChangePartner -> "change their accountability partner"
        }
    Text("Someone is asking you to $what. Approve only if you agree.", style = MaterialTheme.typography.bodyMedium)
    Text("For key: ${incoming.expectedFingerprint}", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { actions.onApprove(incoming) }, enabled = !busy) { Text("Approve") }
        OutlinedButton(onClick = actions.onDismiss) { Text("Do not approve") }
    }
}
