package jp.infinity.krate.katachecker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = KarateRed,
    onPrimary = KarateSurface,
    primaryContainer = KarateRed,
    secondary = KarateRedDark,
    background = KarateSurface,
    surface = KarateSurface,
    onBackground = KarateOnSurface,
    onSurface = KarateOnSurface,
)

private val DarkColors = darkColorScheme(
    primary = KarateRed,
    onPrimary = KarateSurface,
    secondary = KarateRedDark,
    background = KarateOnSurface,
    surface = KarateOnSurface,
    onBackground = KarateSurface,
    onSurface = KarateSurface,
)

@Composable
fun KarateKataTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
