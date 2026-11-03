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


private fun Color.calcLuminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

private fun blendColors(c1: Color, c2: Color, ratio: Float): Color {
    val inv = 1f - ratio
    return Color(
        red = c1.red * inv + c2.red * ratio,
        green = c1.green * inv + c2.green * ratio,
        blue = c1.blue * inv + c2.blue * ratio,
        alpha = c1.alpha * inv + c2.alpha * ratio
    )
}

private fun normalizePaletteColors(input: List<Color>): List<Color> {
    if (input.isEmpty()) return listOf(Color(0xFF00E5CC))
    val base = input.toMutableList()
    if (base.size >= 6) return base
    val sorted = base.sortedBy { it.calcLuminance() }
    val result = base.toMutableList()
    var guard = 0
    while (result.size < 6 && guard < 24) {
        guard++
        val i = (guard - 1) % (sorted.size.coerceAtLeast(1))
        val j = (i + 1).coerceAtMost(sorted.lastIndex)
        val blended = blendColors(sorted[i], sorted[j], 0.5f)
        if (blended !in result) result.add(blended)
        if (result.size < 6) {
            val tint = if (sorted.first().calcLuminance() < 0.5f) {
                blendColors(sorted.last(), Color.White, 0.35f)
            } else {
                blendColors(sorted.first(), Color.Black, 0.35f)
            }
            if (tint !in result) result.add(tint)
        }
    }
    return result
}



data class AuraPalette(
    val key: String,
    val label: String,
    val rawColors: List<Color>,
    val colors: List<Color> = normalizePaletteColors(rawColors)
) {
    val accent: Color get() = colors.firstOrNull() ?: Color(0xFF00E5CC)
}

val AuraPalettes = listOf(
    AuraPalette("p1", "1: Océano Cían", listOf(Color(0xFFA7EBF2), Color(0xFF54ACBF), Color(0xFF26658C), Color(0xFF023859), Color(0xFF011C40), Color(0xFF548399))),
    AuraPalette("p2", "2: Turquesa Profundo", listOf(Color(0xFF05161A), Color(0xFF072E33), Color(0xFF0C7075), Color(0xFF0F969C), Color(0xFF6DA5C0), Color(0xFF294D61))),
    AuraPalette("p3", "3: Malva Coral", listOf(Color(0xFF2E355A), Color(0xFF6C5E82), Color(0xFFA58F9E), Color(0xFFC57E81), Color(0xFF96A4C5), Color(0xFF3F5B8B))),
    AuraPalette("p4", "4: Esmeralda Menta", listOf(Color(0xFF062223), Color(0xFF418B7E), Color(0xFF4E977A), Color(0xFF7AB37C), Color(0xFFF7F4D1), Color(0xFF7E8B7A))),
    AuraPalette("p5", "5: Bosque Salvia", listOf(Color(0xFF0F2A1D), Color(0xFF375534), Color(0xFF6B9071), Color(0xFFAEC3B0), Color(0xFFE3EED4), Color(0xFF798C78))),
    AuraPalette("p6", "6: Pizarra Nórdico", listOf(Color(0xFF263A47), Color(0xFF4A5B6A), Color(0xFF728495), Color(0xFF98A9BE), Color(0xFFB4C5D8), Color(0xFF6D7F8F))),
    AuraPalette("p7", "7: Coral Atardecer", listOf(Color(0xFF355070), Color(0xFF6D597A), Color(0xFFB56576), Color(0xFFE56B6F), Color(0xFFE88C7D), Color(0xFFEAAC8B))),
    AuraPalette("p8", "8: Ciruela Cósmico", listOf(Color(0xFF0E1B48), Color(0xFFC18DB4), Color(0xFFE2CAD8), Color(0xFF87A7D0), Color(0xFF27425D), Color(0xFF0E1F2F))),
    AuraPalette("p9", "9: Púrpura Real", listOf(Color(0xFF49225B), Color(0xFF6E3482), Color(0xFFA56ABD), Color(0xFFE7DBEF), Color(0xFFF5EBFA), Color(0xFF9F86AA))),
    AuraPalette("p10", "10: Hielo Ártico", listOf(Color(0xFF0E1C24), Color(0xFF1E3A5F), Color(0xFF6C8CA9), Color(0xFF92B4CE), Color(0xFFE1E5E9), Color(0xFF778086))),
    AuraPalette("p11", "11: Grafito Titanio", listOf(Color(0xFF263A47), Color(0xFF4A5B6A), Color(0xFF728495), Color(0xFF98A9BE), Color(0xFFB4C5D8), Color(0xFF6D7F8F))),
    AuraPalette("p12", "12: Amatista Suave", listOf(Color(0xFF4B3F6E), Color(0xFF6C5F8D), Color(0xFF9C8CB9), Color(0xFFBA96C1), Color(0xFFDCD7D5), Color(0xFF938BA1))),
    AuraPalette("p13", "13: Verde Neón", listOf(Color(0xFF1E472A), Color(0xFF4A6D10), Color(0xFFBBE053), Color(0xFF65DC91), Color(0xFFB2ECC7), Color(0xFFEBF4F1))),
    AuraPalette("p14", "14: Azul Acero", listOf(Color(0xFFF0F3FA), Color(0xFFD5DEEF), Color(0xFFB1C9EF), Color(0xFF8AAEE0), Color(0xFF638ECB), Color(0xFF395886))),
    AuraPalette("p15", "15: Ámbar Dorado", listOf(Color(0xFF5344A9), Color(0xFF7A5197), Color(0xFFBB5098), Color(0xFFF47F6B), Color(0xFFFFB32B), Color(0xFFF5C63C))),
    AuraPalette("p16", "16: Sol Poniente", listOf(Color(0xFF8ECAE6), Color(0xFF209EBB), Color(0xFF023047), Color(0xFFFFB701), Color(0xFFFC8500), Color(0xFFC5A773))),
    AuraPalette("p17", "17: Oliva Tierra", listOf(Color(0xFF5B4E3F), Color(0xFFD9E2E0), Color(0xFFA0BDB4), Color(0xFF557752), Color(0xFF435F42), Color(0xFF4F5640))),
    AuraPalette("p18", "18: Frambuesa", listOf(Color(0xFF4B2991), Color(0xFF872CA2), Color(0xFFC0369D), Color(0xFFEA4F88), Color(0xFFFA7876), Color(0xFFF6A97A))),
    AuraPalette("p19", "19: Lima Eléctrica", listOf(Color(0xFF007542), Color(0xFF1E8C45), Color(0xFF3AA346), Color(0xFF58BB43), Color(0xFF78D23D), Color(0xFF9BE931), Color(0xFFC1FF1C))),
    AuraPalette("p20", "20: Azul Eléctrico", listOf(Color(0xFF112C70), Color(0xFF5B58EB), Color(0xFFBB63FF), Color(0xFF56E1E9), Color(0xFF0A2353), Color(0xFF0D2761))),
    AuraPalette("p21", "21: Rosa Pastel", listOf(Color(0xFF4E8BC4), Color(0xFF97CBFB), Color(0xFFC2E1FD), Color(0xFFFFC2D9), Color(0xFFFF99BE), Color(0xFFFF6DA2))),
    AuraPalette("p22", "22: Algodón Dulce", listOf(Color(0xFFCDB4DB), Color(0xFFFFC8DD), Color(0xFFFFAFCC), Color(0xFFBDE0FE), Color(0xFFA2D2FF), Color(0xFFB7C3ED))),
    AuraPalette("p23", "23: Lila Ensueño", listOf(Color(0xFFE5D9F2), Color(0xFFF5EFFF), Color(0xFFCDC1FF), Color(0xFFA594F9), Color(0xFF7371FC), Color(0xFFACA5F7))),
    AuraPalette("p24", "24: Rosa Ceniza", listOf(Color(0xFFE27396), Color(0xFFEA9AB2), Color(0xFFEFCFE3), Color(0xFFEAF2D7), Color(0xFFB3DEE2), Color(0xFFCAA8BC))),
    AuraPalette("p25", "25: Ciber Turquesa", listOf(Color(0xFF2DE9C7), Color(0xFF4EEFD5), Color(0xFF6FF4E3), Color(0xFF8FFAF1), Color(0xFFB0FFFF), Color(0xFF6EF4E3))),
    AuraPalette("p26", "26: Cian Gradiente", listOf(Color(0xFF12C5DA), Color(0xFF0EB3C8), Color(0xFF09A1B6), Color(0xFF058EA3), Color(0xFF007C91), Color(0xFF09A0B5))),
    AuraPalette("p27", "27: Índigo Resplandor", listOf(Color(0xFFA7ABFF), Color(0xFF8B94F0), Color(0xFF6F7EE1), Color(0xFF5267D2), Color(0xFF3650C3), Color(0xFF6E7DE1))),
    AuraPalette("p28", "28: Cuádruple Dinámico", listOf(Color(0xFF035EAD), Color(0xFFEA0770), Color(0xFFFFCC33), Color(0xFF00A565), Color(0xFF018189), Color(0xFF026F9B))),
    AuraPalette("p29", "29: Bruma Magenta", listOf(Color(0xFF095761), Color(0xFF379FAC), Color(0xFFF49BD5), Color(0xFFF1BFE2), Color(0xFFCFC0BD), Color(0xFF6C8B8F))),
    AuraPalette("p30", "30: Brisa Marina", listOf(Color(0xFFADD8E6), Color(0xFF008080), Color(0xFF98FF98), Color(0xFF4682B4), Color(0xFFB39DDB), Color(0xFFB0BAE0)))
)

fun paletteFor(key: String): AuraPalette =
    AuraPalettes.firstOrNull { it.key == key }
        ?: AuraPalettes.firstOrNull { it.key == "p$key" }
        ?: AuraPalettes.firstOrNull { it.key.removePrefix("p") == key.removePrefix("p") }
        ?: AuraPalettes.firstOrNull { it.label.contains(key, ignoreCase = true) }
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

fun buildMonochromaticTheme(palette: AuraPalette): AuraColors {
    val rawColors = palette.colors
    val sorted = rawColors.sortedBy { it.calcLuminance() }

    val darkest = sorted.first()
    val brightest = sorted.last()
    val mid = sorted[sorted.size / 2]

    // Base de fondo (Canvas):
    // Usamos el tono más profundo de la paleta mezclado armónicamente con una base oscura
    // para garantizar confort visual, saturación elegante y profundidad 3D
    val bgBlend = if (darkest.calcLuminance() < 0.14f) 0.35f else 0.70f
    val background = blendColors(darkest, Color(0xFF090D14), bgBlend)

    // Superficie para tarjetas Neumórficas (con tinte visible y rico de la paleta):
    val cardBase = if (sorted.size >= 2) sorted[1] else darkest
    val surface = blendColors(cardBase, background, 0.40f)

    // Superficie elevada (botones convexos, cuerpo de faders y perillas):
    val elevBase = if (sorted.size >= 3) sorted[2] else cardBase
    val surfaceElevated = blendColors(elevBase, surface, 0.45f)

    // Acento de control y curvas:
    val accent = if (brightest.calcLuminance() > 0.88f && sorted.size > 2) {
        sorted[sorted.size - 2]
    } else {
        brightest
    }

    // Textos con nitidez absoluta y tinte suave de la paleta:
    val textPrimary = blendColors(brightest, Color(0xFFF6F8FC), 0.70f)
    val textSecondary = blendColors(mid, textPrimary, 0.35f).copy(alpha = 0.80f)

    // Neumorfismo 3D:
    // Highlight superior izquierdo: brillo en la dirección de la luz con el matiz del acento
    val neumorphicHighlight = blendColors(accent, Color.White, 0.65f).copy(alpha = 0.24f)
    // Sombra inferior derecha: tinte sutil del tono más oscuro para que la sombra
    // acompañe a la paleta en lugar de ser un negro puro ajeno.
    val neumorphicShadow = blendColors(darkest, Color(0xFF000000), 0.55f).copy(alpha = 0.88f)
    // Cavidad cóncava para hendiduras y ranuras:
    val neumorphicCavity = blendColors(background, Color(0xFF000000), 0.70f)

    // Gradientes de cristal translúcido para tarjetas:
    val glassStart = blendColors(surface, accent, 0.08f).copy(alpha = 0.90f)
    val glassEnd = blendColors(background, surface, 0.30f).copy(alpha = 0.85f)

    return AuraColors(
        accent = accent,
        background = background,
        surface = surface,
        surfaceElevated = surfaceElevated,
        outline = mid.copy(alpha = 0.28f),
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        positive = Color(0xFF34D399),
        warning = Color(0xFFFBBF24),
        danger = Color(0xFFF87171),
        glassSurfaceStart = glassStart,
        glassSurfaceEnd = glassEnd,
        glassBorderTop = blendColors(accent, Color.White, 0.45f).copy(alpha = 0.32f),
        glassBorderBottom = Color(0x0FFFFFFF),
        neumorphicCavity = neumorphicCavity,
        neumorphicHighlight = neumorphicHighlight,
        neumorphicShadow = neumorphicShadow,
        ambientGlow = accent.copy(alpha = 0.28f)
    )
}

val LocalAuraColors = staticCompositionLocalOf { buildMonochromaticTheme(AuraPalettes.first()) }

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
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val palette = paletteFor(paletteKey)
    val colors = buildMonochromaticTheme(palette)

    val scheme = darkColorScheme(
        primary = colors.accent,
        onPrimary = Color.Black,
        secondary = colors.accent,
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