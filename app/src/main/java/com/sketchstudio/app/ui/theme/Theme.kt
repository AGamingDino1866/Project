package com.sketchstudio.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class IosPalette(
    val background: Color,
    val secondaryBackground: Color,
    val tertiaryBackground: Color,
    val elevatedSurface: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val accent: Color,
    val isDark: Boolean
)

private val LightPalette = IosPalette(
    background = IosColors.BackgroundLight,
    secondaryBackground = Color.White,
    tertiaryBackground = IosColors.SystemGray6,
    elevatedSurface = IosColors.ElevatedSurfaceLight,
    label = IosColors.LabelLight,
    secondaryLabel = IosColors.SecondaryLabelLight,
    tertiaryLabel = IosColors.TertiaryLabelLight,
    separator = IosColors.SeparatorLight,
    accent = IosColors.SystemBlue,
    isDark = false
)

private val DarkPalette = IosPalette(
    background = IosColors.BackgroundDark,
    secondaryBackground = IosColors.SystemGray6Dark,
    tertiaryBackground = IosColors.SystemGray5Dark,
    elevatedSurface = IosColors.ElevatedSurfaceDark,
    label = IosColors.LabelDark,
    secondaryLabel = IosColors.SecondaryLabelDark,
    tertiaryLabel = IosColors.TertiaryLabelDark,
    separator = IosColors.SeparatorDark,
    accent = IosColors.SystemBlue,
    isDark = true
)

val LocalIosPalette = staticCompositionLocalOf { LightPalette }

private val LightColorScheme = lightColorScheme(
    primary = IosColors.SystemBlue,
    secondary = IosColors.SystemIndigo,
    tertiary = IosColors.SystemPink,
    background = IosColors.BackgroundLight,
    surface = Color.White,
    error = IosColors.SystemRed,
    onPrimary = Color.White,
    onBackground = IosColors.LabelLight,
    onSurface = IosColors.LabelLight
)

private val DarkColorScheme = darkColorScheme(
    primary = IosColors.SystemBlue,
    secondary = IosColors.SystemIndigo,
    tertiary = IosColors.SystemPink,
    background = IosColors.BackgroundDark,
    surface = IosColors.SystemGray6Dark,
    error = IosColors.SystemRed,
    onPrimary = Color.White,
    onBackground = IosColors.LabelDark,
    onSurface = IosColors.LabelDark
)

@Composable
fun SketchStudioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalIosPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = IosTypography,
            content = content
        )
    }
}
