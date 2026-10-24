package com.auraline.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class AuraPalette(
    val key: String,
    val label: String,
    val accent: Color
)

val AuraPalettes = listOf(
    AuraPalette("aura", "Aura", Color(0xFF00E5CC)),
    AuraPalette("sunset", "Sunset", Color(0xFFFF8A4C)),
    AuraPalette("forest", "Forest", Color(0xFF4FE08A)),
    AuraPalette("midnight", "Midnight", Color(0xFF9B7BFF)),
    AuraPalette("coral", "Coral", Color(0xFFFF6B81)),
    AuraPalette("ocean", "Ocean", Color(0xFF4D9DFF))
)

fun paletteFor(key: String): AuraPalette = AuraPalettes.firstOrNull { it.key == key } ?: AuraPalettes.first()

@Immutable
data class AuraColors(
    val accent: Color,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val positive: Color,
    val warning: Color,
    val danger: Color
)

private fun darkColors(accent: Color) = AuraColors(
    accent = accent,
    background = Color(0xFF080B10),
    surface = Color(0xFF11161D),
    surfaceElevated = Color(0xFF171E27),
    outline = Color(0xFF25303C),
    textPrimary = Color(0xFFEAF1F8),
    textSecondary = Color(0xFF8CA0B4),
    positive = Color(0xFF37E39B),
    warning = Color(0xFFFFC24B),
    danger = Color(0xFFFF6B6B)
)

private fun lightColors(accent: Color) = AuraColors(
    accent = accent,
    background = Color(0xFFF4F6F9),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFEDF1F6),
    outline = Color(0xFFD3DBE5),
    textPrimary = Color(0xFF11161D),
    textSecondary = Color(0xFF5A6B7D),
    positive = Color(0xFF0E9C63),
    warning = Color(0xFFB57A00),
    danger = Color(0xFFC73B3B)
)

val LocalAuraColors = staticCompositionLocalOf { darkColors(AuraPalettes.first().accent) }

object AuraTheme {
    val colors: AuraColors
        @Composable get() = LocalAuraColors.current
}

private val AuraTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp)
)

@Composable
fun AuraLineTheme(
    accentColor: Color = AuraPalettes.first().accent,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) darkColors(accentColor) else lightColors(accentColor)
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = accentColor,
            onPrimary = Color(0xFF04100E),
            secondary = accentColor,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceElevated,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.outline,
            error = colors.danger
        )
    } else {
        lightColorScheme(
            primary = accentColor,
            onPrimary = Color.White,
            secondary = accentColor,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceElevated,
            onSurfaceVariant = colors.textSecondary,
            outline = colors.outline,
            error = colors.danger
        )
    }

    CompositionLocalProvider(LocalAuraColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = AuraTypography, content = content)
    }
}
