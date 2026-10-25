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
    val colors: List<Color>
) {
    val accent: Color get() = colors.firstOrNull() ?: Color(0xFF00E5CC)
}

val AuraPalettes = listOf(
    AuraPalette("p1", "Océano Cían", listOf(Color(0xFFA7EBF2), Color(0xFF54ACBF), Color(0xFF26658C), Color(0xFF023859), Color(0xFF011C40))),
    AuraPalette("p2", "Turquesa Profundo", listOf(Color(0xFF6DA5C0), Color(0xFF0F969C), Color(0xFF0C7075), Color(0xFF072E33), Color(0xFF05161A))),
    AuraPalette("p3", "Lavanda Malva", listOf(Color(0xFFC57E81), Color(0xFFA58F9E), Color(0xFF6C5E82), Color(0xFF3F5B8B), Color(0xFF2E355A))),
    AuraPalette("p4", "Esmeralda Menta", listOf(Color(0xFF7AB37C), Color(0xFF4E977A), Color(0xFF418B7E), Color(0xFF062223), Color(0xFF041516))),
    AuraPalette("p5", "Bosque Salvia", listOf(Color(0xFFAEC3B0), Color(0xFF6B9071), Color(0xFF375534), Color(0xFF0F2A1D), Color(0xFF081810))),
    AuraPalette("p6", "Azul Pizarra", listOf(Color(0xFFB4C5D8), Color(0xFF98A9BE), Color(0xFF728495), Color(0xFF4A5B6A), Color(0xFF263A47))),
    AuraPalette("p7", "Coral Atardecer", listOf(Color(0xFFEAAC8B), Color(0xFFE88C7D), Color(0xFFE56B6F), Color(0xFFB56576), Color(0xFF355070))),
    AuraPalette("p8", "Ciruela Cósmico", listOf(Color(0xFFE2CAD8), Color(0xFFC18DB4), Color(0xFF87A7D0), Color(0xFF27425D), Color(0xFF0E1B48))),
    AuraPalette("p9", "Púrpura Real", listOf(Color(0xFFE7DBEF), Color(0xFFA56ABD), Color(0xFF6E3482), Color(0xFF49225B), Color(0xFF1D0928))),
    AuraPalette("p10", "Hielo Ártico", listOf(Color(0xFFE1E5E9), Color(0xFF92B4CE), Color(0xFF6C8CA9), Color(0xFF1E3A5F), Color(0xFF0E1C24))),
    AuraPalette("p11", "Grafito Oscuro", listOf(Color(0xFFB4C5D8), Color(0xFF728495), Color(0xFF4A5B6A), Color(0xFF263A47), Color(0xFF121D24))),
    AuraPalette("p12", "Amatista", listOf(Color(0xFFDCD7D5), Color(0xFFBA96C1), Color(0xFF9C8CB9), Color(0xFF6C5F8D), Color(0xFF4B3F6E))),
    AuraPalette("p13", "Verde Neón", listOf(Color(0xFFBBE053), Color(0xFF65DC91), Color(0xFF4A6D10), Color(0xFF1E472A), Color(0xFF0A1F11))),
    AuraPalette("p14", "Azales Acero", listOf(Color(0xFFB1C9EF), Color(0xFF8AAEE0), Color(0xFF638ECB), Color(0xFF395886), Color(0xFF1A2A42))),
    AuraPalette("p15", "Ámbar Dorado", listOf(Color(0xFFFFB32B), Color(0xFFF47F6B), Color(0xFFBB5098), Color(0xFF7A5197), Color(0xFF5344A9))),
    AuraPalette("p16", "Sol Naranja", listOf(Color(0xFFFFB701), Color(0xFFFC8500), Color(0xFF8ECAE6), Color(0xFF209EBB), Color(0xFF023047))),
    AuraPalette("p17", "Oliva Tierra", listOf(Color(0xFFA0BDB4), Color(0xFF557752), Color(0xFF435F42), Color(0xFF5B4E3F), Color(0xFF222B21))),
    AuraPalette("p18", "Frambuesa", listOf(Color(0xFFFA7876), Color(0xFFEA4F88), Color(0xFFC0369D), Color(0xFF872CA2), Color(0xFF4B2991))),
    AuraPalette("p19", "Lima Eléctrica", listOf(Color(0xFFC1FF1C), Color(0xFF78D23D), Color(0xFF3AA346), Color(0xFF007542), Color(0xFF003820))),
    AuraPalette("p20", "Azul Eléctrico", listOf(Color(0xFF56E1E9), Color(0xFFBB63FF), Color(0xFF5B58EB), Color(0xFF112C70), Color(0xFF0A2353))),
    AuraPalette("p21", "Rosa Pastel", listOf(Color(0xFFFF99BE), Color(0xFFFFC2D9), Color(0xFFC2E1FD), Color(0xFF97CBFB), Color(0xFF4E8BC4))),
    AuraPalette("p22", "Dulce Algodón", listOf(Color(0xFFFFAFCC), Color(0xFFFFC8DD), Color(0xFFCDB4DB), Color(0xFFBDE0FE), Color(0xFFA2D2FF))),
    AuraPalette("p23", "Lila Ensueño", listOf(Color(0xFFA594F9), Color(0xFFCDC1FF), Color(0xFFE5D9F2), Color(0xFF7371FC), Color(0xFF2E2B80))),
    AuraPalette("p24", "Rosa Suave", listOf(Color(0xFFE27396), Color(0xFFEA9AB2), Color(0xFFEFCFE3), Color(0xFFB3DEE2), Color(0xFF2A3A3D))),
    AuraPalette("p25", "Ciber Turquesa", listOf(Color(0xFFB0FFFF), Color(0xFF8FFAF1), Color(0xFF6FF4E3), Color(0xFF2DE9C7), Color(0xFF0D473D))),
    AuraPalette("p26", "Cian Profundo", listOf(Color(0xFF12C5DA), Color(0xFF0EB3C8), Color(0xFF09A1B6), Color(0xFF058EA3), Color(0xFF007C91))),
    AuraPalette("p27", "Índigo Resplandor", listOf(Color(0xFFA7ABFF), Color(0xFF8B94F0), Color(0xFF6F7EE1), Color(0xFF5267D2), Color(0xFF3650C3))),
    AuraPalette("p28", "Cuádruple Vibrante", listOf(Color(0xFFFFCC33), Color(0xFFEA0770), Color(0xFF00A565), Color(0xFF035EAD), Color(0xFF022646))),
    AuraPalette("p29", "Bruma Magenta", listOf(Color(0xFFF1BFE2), Color(0xFFF49BD5), Color(0xFFCFC0BD), Color(0xFF379FAC), Color(0xFF095761))),
    AuraPalette("p30", "Brisa Marina", listOf(Color(0xFF98FF98), Color(0xFFADD8E6), Color(0xFFB39DDB), Color(0xFF4682B4), Color(0xFF008080)))
)

fun paletteFor(key: String): AuraPalette =
    AuraPalettes.firstOrNull { it.key == key }
        ?: AuraPalettes.firstOrNull { it.key == "p$key" }
        ?: AuraPalettes.firstOrNull { it.label.equals(key, ignoreCase = true) }
        ?: AuraPalettes.first()

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
    val glassSurfaceStart: Color,
    val glassSurfaceEnd: Color,
    val glassBorderTop: Color,
    val glassBorderBottom: Color,
    val neumorphicCavity: Color,
    val neumorphicHighlight: Color,
    val neumorphicShadow: Color,
    val ambientGlow: Color = accent.copy(alpha = 0.22f)
)

private fun neumorphicLightColors(accent: Color): AuraColors {
    // Fondo claro con un ligerísimo tono azulado/gris (Soft Neumorphic Light)
    val bgColor = Color(0xFFE5E9F0)
    return AuraColors(
        accent = accent,
        background = bgColor,
        surface = bgColor, // Neumorphism usa el mismo color de fondo para la superficie
        surfaceElevated = Color(0xFFEDF1F8), // Un poco más claro
        outline = Color(0xFFD3D8E0),
        textPrimary = Color(0xFF2E3440),
        textSecondary = Color(0xFF4C566A),
        positive = Color(0xFF34D399),
        warning = Color(0xFFFBBF24),
        danger = Color(0xFFF87171),
        glassSurfaceStart = Color(0x99FFFFFF),
        glassSurfaceEnd = Color(0x33FFFFFF),
        glassBorderTop = Color(0xB3FFFFFF),
        glassBorderBottom = Color(0x33FFFFFF),
        neumorphicCavity = Color(0xFFD0D7E3), // Sombra interna para los huecos
        neumorphicHighlight = Color(0xFFFFFFFF), // Brillo blanco puro (sombra clara superior izquierda)
        neumorphicShadow = Color(0xFFB8C2D5), // Sombra oscura inferior derecha (gris azulado)
        ambientGlow = accent.copy(alpha = 0.24f)
    )
}

val LocalAuraColors = staticCompositionLocalOf { neumorphicLightColors(AuraPalettes.first().accent) }

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
    paletteKey: String = "",
    darkTheme: Boolean = true, // We ignore darkTheme for now to force the light neumorphic style from the image
    content: @Composable () -> Unit
) {
    val activeAccent = if (paletteKey.isNotEmpty()) paletteFor(paletteKey).accent else accentColor
    val colors = neumorphicLightColors(activeAccent)

    val scheme = lightColorScheme(
        primary = activeAccent,
        onPrimary = Color.White,
        secondary = activeAccent,
        background = colors.background,
        onBackground = colors.textPrimary,
        surface = colors.surface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceElevated,
        onSurfaceVariant = colors.textSecondary,
        outline = colors.outline,
        error = colors.danger
    )

    CompositionLocalProvider(LocalAuraColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = AuraTypography, content = content)
    }
}
