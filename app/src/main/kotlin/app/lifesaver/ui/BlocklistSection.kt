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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lifesaver.blocklist.BlocklistIndex

/**
 * List status plus override editing. Allow entries weaken protection, so they (and removing a
 * block entry) are offered in debug builds only.
 */
@Composable
fun BlocklistSection(
    index: BlocklistIndex,
    loading: Boolean,
    debug: Boolean,
    onReload: () -> Unit,
    onAddDeny: (String) -> Boolean,
    onAddAllow: (String) -> Boolean,
    onRemoveDeny: (String) -> Unit,
    onRemoveAllow: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Block list", style = MaterialTheme.typography.titleMedium)
        Text(
            text =
                if (loading) {
                    "Loading list"
                } else {
                    "${index.baseCount} domains, version ${index.version}\n" +
                        "${index.resolverCount} encrypted-DNS hosts blocked"
                },
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = onReload, enabled = !loading) { Text("Reload bundled list") }

        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                invalid = false
            },
            label = { Text("Domain, for example site.example") },
            isError = invalid,
            supportingText = { if (invalid) Text("Not a valid domain") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { if (onAddDeny(input)) input = "" else invalid = true }) {
                Text("Block")
            }
            if (debug) {
                OutlinedButton(onClick = { if (onAddAllow(input)) input = "" else invalid = true }) {
                    Text("Allow")
                }
            }
        }
        OverrideRows("Blocked by you", index.deny, if (debug) onRemoveDeny else null)
        if (debug) {
            OverrideRows("Allowed (debug)", index.allow, onRemoveAllow)
        }
    }
}

@Composable
private fun OverrideRows(
    title: String,
    names: Set<String>,
    onRemove: ((String) -> Unit)?,
) {
    if (names.isEmpty()) {
        return
    }
    Text(title, style = MaterialTheme.typography.labelLarge)
    names.sorted().forEach { name ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            if (onRemove != null) {
                TextButton(onClick = { onRemove(name) }) { Text("Remove") }
            }
        }
    }
}
