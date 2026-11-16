package io.github.peol13.unicum.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.github.peol13.unicum.data.ThemeMode
import io.github.peol13.unicum.data.UserSettings

private val UnicumDarkColorScheme = darkColorScheme(
    primary = DarkUnicumColors.primary,
    onPrimary = DarkUnicumColors.onPrimary,
    secondary = DarkUnicumColors.secondary,
    onSecondary = DarkUnicumColors.onPrimary,
    tertiary = DarkUnicumColors.accent,
    background = DarkUnicumColors.background,
    onBackground = DarkUnicumColors.text,
    surface = DarkUnicumColors.surface,
    onSurface = DarkUnicumColors.text,
    surfaceVariant = DarkUnicumColors.surface,
    onSurfaceVariant = DarkUnicumColors.text.copy(alpha = .7f),
    outline = DarkUnicumColors.accent
)

private val UnicumLightColorScheme = lightColorScheme(
    primary = LightUnicumColors.primary,
    onPrimary = LightUnicumColors.onPrimary,
    secondary = LightUnicumColors.secondary,
    onSecondary = LightUnicumColors.text,
    tertiary = LightUnicumColors.accent,
    background = LightUnicumColors.background,
    onBackground = LightUnicumColors.text,
    surface = LightUnicumColors.background,
    onSurface = LightUnicumColors.text,
    surfaceVariant = LightUnicumColors.surface,
    onSurfaceVariant = LightUnicumColors.text.copy(alpha = .7f),
    outline = LightUnicumColors.accent
)

/** true se in questo momento va usato il tema scuro (impostazione dell'utente o sistema). */
@Composable
fun useDarkTheme(): Boolean = when (UserSettings.themeMode) {
    ThemeMode.SISTEMA -> isSystemInDarkTheme()
    ThemeMode.CHIARO -> false
    ThemeMode.SCURO -> true
}

@Composable
fun UnicumTheme(
    darkTheme: Boolean = useDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalUnicumColors provides if (darkTheme) DarkUnicumColors else LightUnicumColors
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) UnicumDarkColorScheme else UnicumLightColorScheme,
            typography = Typography,
            content = content
        )
    }
}
