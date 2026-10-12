package app.lifesaver.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import app.lifesaver.policy.Approval
import app.lifesaver.policy.Purpose

/**
 * For the partner: paste the link from the email, or arrive here from a lifesaver:// link, then
 * press Approve. Works on any phone with this app installed.
 */
@Composable
fun ApproveSection(
    incoming: Approval?,
    busy: Boolean,
    message: String?,
    onCheckLink: (String) -> Unit,
    onApprove: (Approval) -> Unit,
    onDismiss: () -> Unit,
) {
    var link by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Approve someone's request", style = MaterialTheme.typography.titleMedium)
        if (incoming == null) {
            Text(
                "Are you someone's accountability partner? Paste the link from their email.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                label = { Text("Approval link") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = { onCheckLink(link) }, enabled = link.isNotBlank()) { Text("Review link") }
        } else {
            val what =
                if (incoming.purpose == Purpose.Unlock) {
                    "pause their protection for 15 minutes"
                } else {
                    "change their accountability partner"
                }
            Text("Someone is asking to $what. Approve only if you agree.", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onApprove(incoming) }, enabled = !busy) { Text("Approve") }
                OutlinedButton(onClick = onDismiss) { Text("Do not approve") }
            }
        }
        if (message != null) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
