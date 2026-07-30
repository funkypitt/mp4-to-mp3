package app.mp4tomp3.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Amber = Color(0xFFB55A00)
private val AmberLight = Color(0xFFFFB77A)

private val LightScheme = lightColorScheme(
    primary = Amber,
    onPrimary = Color.White,
    secondary = Color(0xFF755846),
)

private val DarkScheme = darkColorScheme(
    primary = AmberLight,
    onPrimary = Color(0xFF4A2800),
    secondary = Color(0xFFE4BFA8),
)

@Composable
fun Mp4ToMp3Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = colors, content = content)
}
