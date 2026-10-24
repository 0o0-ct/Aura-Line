package com.auraline.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val BackgroundDark = Color(0xFF161A1E)
val SurfaceDark = Color(0xFF1B2026)
val LightShadowDark = Color(0xFF232A32)
val DarkShadowDark = Color(0xFF0C0E11)
val CyanNeon = Color(0xFF00E5CC)
val CyanNeonDark = Color(0xFF00B3A0)
val AmberNeon = Color(0xFFFFB703)
val OrangeNeon = Color(0xFFFF7043)
val TextPrimary = Color(0xFFF0F4F8)
val TextSecondary = Color(0xFF8A99AD)
val CardBackground = Color(0xFF1E242C)

@Immutable
data class NeumorphicColors(
    val background: Color = BackgroundDark,
    val surface: Color = SurfaceDark,
    val lightShadow: Color = LightShadowDark,
    val darkShadow: Color = DarkShadowDark,
    val accent: Color = CyanNeon,
    val textPrimary: Color = TextPrimary,
    val textSecondary: Color = TextSecondary
)

val LocalNeumorphicColors = staticCompositionLocalOf { NeumorphicColors() }

private val DarkColorScheme = darkColorScheme(
    primary = CyanNeon,
    secondary = CyanNeonDark,
    background = BackgroundDark,
    surface = SurfaceDark,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

@Composable
fun NeumorphicTheme(
    accentColor: Color = CyanNeon,
    content: @Composable () -> Unit
) {
    val neumorphicColors = NeumorphicColors(
        background = BackgroundDark,
        surface = SurfaceDark,
        lightShadow = LightShadowDark,
        darkShadow = DarkShadowDark,
        accent = accentColor,
        textPrimary = TextPrimary,
        textSecondary = TextSecondary
    )

    CompositionLocalProvider(LocalNeumorphicColors provides neumorphicColors) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            content = content
        )
    }
}
