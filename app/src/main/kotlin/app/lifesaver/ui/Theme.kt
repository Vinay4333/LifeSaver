package app.lifesaver.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val colors =
    darkColorScheme(
        primary = Color(0xFF8BD17C),
        onPrimary = Color(0xFF0E1A0C),
        background = Color(0xFF121417),
        onBackground = Color(0xFFE6E8EA),
        surface = Color(0xFF121417),
        onSurface = Color(0xFFE6E8EA),
    )

@Composable
fun LifeSaverTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
