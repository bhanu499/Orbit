package com.orbit.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalOrbitColors = staticCompositionLocalOf { DarkOrbitColors }
val LocalOrbitSpacing = staticCompositionLocalOf { OrbitSpacingScale() }

object OrbitTheme {
    val colors: OrbitColorPalette
        @Composable
        @ReadOnlyComposable
        get() = LocalOrbitColors.current

    val spacing: OrbitSpacingScale
        @Composable
        @ReadOnlyComposable
        get() = LocalOrbitSpacing.current
}

@Composable
fun OrbitTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkOrbitColors else LightOrbitColors
    val m3Scheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.accentPrimary,
            secondary = palette.accentSecondary,
            background = palette.background,
            surface = palette.surface,
            surfaceVariant = palette.surfaceElevated,
            onPrimary = palette.textPrimary,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border,
            error = palette.statusError
        )
    } else {
        lightColorScheme(
            primary = palette.accentPrimary,
            secondary = palette.accentSecondary,
            background = palette.background,
            surface = palette.surface,
            surfaceVariant = palette.surfaceElevated,
            onPrimary = palette.userBubbleText,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.border,
            error = palette.statusError
        )
    }

    CompositionLocalProvider(
        LocalOrbitColors provides palette,
        LocalOrbitSpacing provides OrbitSpacingScale()
    ) {
        MaterialTheme(
            colorScheme = m3Scheme,
            typography = OrbitTypography,
            shapes = OrbitShapes,
            content = content
        )
    }
}
