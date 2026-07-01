package com.lostf1sh.pixelplayeross.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.lostf1sh.pixelplayeross.presentation.viewmodel.ColorSchemePair
import androidx.core.graphics.ColorUtils

val LocalPixelPlayerDarkTheme = staticCompositionLocalOf { false }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Suppress("DEPRECATION")
@Composable
fun PixelPlayerStatusBarStyle(
    color: Color,
    useDarkIcons: Boolean = ColorUtils.calculateLuminance(color.toArgb()) > 0.55,
    navigationColor: Color? = null,
    useDarkNavigationIcons: Boolean = navigationColor
        ?.let { ColorUtils.calculateLuminance(it.toArgb()) > 0.55 }
        ?: useDarkIcons
) {
    val view = LocalView.current
    if (view.isInEditMode) return

    val updateNavigationBar = navigationColor != null
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
        }

        WindowCompat.getInsetsController(window, view).run {
            isAppearanceLightStatusBars = useDarkIcons

            if (updateNavigationBar) {
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                isAppearanceLightNavigationBars = useDarkNavigationIcons
            }
        }
    }
}

// YouTube Music look: near-black surfaces, red accent. The Material 3 Expressive
// components (wavy sliders, loading indicators) keep the PixelPlayer identity on top.
val DarkColorScheme = darkColorScheme(
    primary = YtmRed,
    onPrimary = PixelPlayerWhite,
    primaryContainer = YtmRedContainer,
    onPrimaryContainer = YtmOnSurface,
    secondary = YtmOnSurfaceVariant,
    onSecondary = PixelPlayerBlack,
    tertiary = YtmRed,
    onTertiary = PixelPlayerWhite,
    background = YtmBackground,
    onBackground = YtmOnSurface,
    surface = YtmSurface,
    onSurface = YtmOnSurface,
    surfaceVariant = YtmSurfaceVariant,
    onSurfaceVariant = YtmOnSurfaceVariant,
    surfaceContainerLowest = YtmSurfaceContainerLowest,
    surfaceContainerLow = YtmSurfaceContainerLow,
    surfaceContainer = YtmSurfaceContainer,
    surfaceContainerHigh = YtmSurfaceContainerHigh,
    surfaceContainerHighest = YtmSurfaceContainerHighest,
    outline = YtmOutline,
    outlineVariant = YtmSurfaceVariant,
    error = Color(0xFFFF5252),
    onError = PixelPlayerWhite
)

val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = PixelPlayerWhite,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = PixelPlayerPink,
    onSecondary = PixelPlayerWhite,
    secondaryContainer = PixelPlayerPink.copy(alpha = 0.15f),
    onSecondaryContainer = PixelPlayerPink.copy(alpha = 0.85f),
    tertiary = PixelPlayerOrange,
    onTertiary = PixelPlayerBlack,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutline.copy(alpha = 0.6f),
    surfaceTint = LightPrimary,
    error = Color(0xFFD32F2F),
    onError = PixelPlayerWhite
)

@Composable
fun PixelPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorSchemePairOverride: ColorSchemePair? = null,
    content: @Composable () -> Unit
) {
    val finalColorScheme = when {
        // The album-art palette (applied inside the player sheet) still wins when present.
        colorSchemePairOverride != null ->
            if (darkTheme) colorSchemePairOverride.dark else colorSchemePairOverride.light
        // Default to the YouTube Music near-black + red scheme. Material You dynamic color is
        // no longer the automatic default (the app now has its own strong brand identity).
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    PixelPlayerStatusBarStyle(
        color = finalColorScheme.background,
        navigationColor = finalColorScheme.background
    )

    CompositionLocalProvider(LocalPixelPlayerDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = finalColorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
