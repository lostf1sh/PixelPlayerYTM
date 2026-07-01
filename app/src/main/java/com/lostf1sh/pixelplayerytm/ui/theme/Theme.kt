package com.lostf1sh.pixelplayerytm.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val YtmDarkColorScheme = darkColorScheme(
    primary = RedPrimary,
    onPrimary = Black0,
    primaryContainer = RedContainer,
    onPrimaryContainer = OnRedContainer,
    inversePrimary = RedDim,
    secondary = TextSecondary,
    onSecondary = Black0,
    secondaryContainer = Surface3,
    onSecondaryContainer = TextPrimary,
    tertiary = RedPrimary,
    onTertiary = Black0,
    tertiaryContainer = RedContainer,
    onTertiaryContainer = OnRedContainer,
    background = NearBlack,
    onBackground = TextPrimary,
    surface = NearBlack,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = Black0,
    surfaceContainerLow = Surface1,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface3,
    inverseSurface = TextPrimary,
    inverseOnSurface = NearBlack,
    outline = OutlineDark,
    outlineVariant = Surface3,
    error = RedPrimary,
    onError = Black0,
    errorContainer = RedContainer,
    onErrorContainer = OnRedContainer,
)

@Composable
fun PixelPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = YtmDarkColorScheme,
        typography = AppTypography,
        content = content,
    )
}
