package app.lifesaver.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun GuardSection(
    enabled: Boolean,
    closes: Int,
    onOpenSettings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Content guard (Reddit, Discord)", style = MaterialTheme.typography.titleMedium)
        Text(
            text = if (enabled) "On. Screens closed so far: $closes" else "Off. Turn it on in Accessibility settings.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = onOpenSettings) { Text("Open accessibility settings") }
    }
}
