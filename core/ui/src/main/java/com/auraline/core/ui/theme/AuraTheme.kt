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
    val danger: Color,
    val glassSurfaceStart: Color = Color(0x381F293A),
    val glassSurfaceEnd: Color = Color(0x1F131A26),
    val glassBorderTop: Color = Color(0x4DFFFFFF),
    val glassBorderBottom: Color = Color(0x0DFFFFFF),
    val neumorphicCavity: Color = Color(0xFF080C12),
    val neumorphicHighlight: Color = Color(0x26FFFFFF),
    val neumorphicShadow: Color = Color(0xCC000000),
    val ambientGlow: Color = accent.copy(alpha = 0.22f)
)

private fun darkColors(accent: Color) = AuraColors(
    accent = accent,
    background = Color(0xFF07090E),
    surface = Color(0xFF111722),
    surfaceElevated = Color(0xFF17202D),
    outline = Color(0x26FFFFFF),
    textPrimary = Color(0xFFF5F6F8),
    textSecondary = Color(0xFF8E9BAE),
    positive = Color(0xFF34D399),
    warning = Color(0xFFFBBF24),
    danger = Color(0xFFF87171),
    glassSurfaceStart = Color(0x38222E42),
    glassSurfaceEnd = Color(0x1C131A26),
    glassBorderTop = Color(0x4DFFFFFF),
    glassBorderBottom = Color(0x0DFFFFFF),
    neumorphicCavity = Color(0xFF080C13),
    neumorphicHighlight = Color(0x26FFFFFF),
    neumorphicShadow = Color(0xCC000000),
    ambientGlow = accent.copy(alpha = 0.24f)
)

private fun lightColors(accent: Color) = AuraColors(
    accent = accent,
    background = Color(0xFFF2F4F7),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFE9EEF4),
    outline = Color(0xFFD2D9E2),
    textPrimary = Color(0xFF10141D),
    textSecondary = Color(0xFF5F6E80),
    positive = Color(0xFF059669),
    warning = Color(0xFFD97706),
    danger = Color(0xFFDC2626),
    glassSurfaceStart = Color(0xD9FFFFFF),
    glassSurfaceEnd = Color(0xB3F0F4F9),
    glassBorderTop = Color(0x80FFFFFF),
    glassBorderBottom = Color(0x26CBD5E1),
    neumorphicCavity = Color(0xFFE2E7EE),
    neumorphicHighlight = Color(0xFFFFFFFF),
    neumorphicShadow = Color(0x26000000),
    ambientGlow = accent.copy(alpha = 0.15f)
)

val LocalAuraColors = staticCompositionLocalOf { darkColors(AuraPalettes.first().accent) }

object AuraTheme {
    val colors: AuraColors
        @Composable get() = LocalAuraColors.current
}

private val AuraTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp
    ),
    titleLarge = TextStyle(
        fontSize = 19.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.1).sp
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.0.sp
    )
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
            onPrimary = Color(0xFF030A09),
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
