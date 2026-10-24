package com.auraline.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class MonoPalette(
    val id: Int,
    val name: String,
    val colors: List<Color>
) {
    val primary: Color get() = colors.getOrElse(0) { Color(0xFF00E5CC) }
    val secondary: Color get() = colors.getOrElse(1) { Color(0xFF54ACBF) }
    val tertiary: Color get() = colors.getOrElse(2) { Color(0xFF26658C) }
    val background: Color get() = colors.lastOrNull()?.copy(alpha = 0.95f) ?: Color(0xFF0C0F12)
    val surface: Color get() = colors.getOrElse(colors.size - 2) { Color(0xFF161A1E) }
}

val allMonoPalettes = listOf(
    MonoPalette(1, "Océano Cían", listOf(Color(0xFFA7EBF2), Color(0xFF54ACBF), Color(0xFF26658C), Color(0xFF023859), Color(0xFF011C40))),
    MonoPalette(2, "Turquesa Profundo", listOf(Color(0xFF6DA5C0), Color(0xFF0F969C), Color(0xFF0C7075), Color(0xFF072E33), Color(0xFF05161A))),
    MonoPalette(3, "Lavanda Malva", listOf(Color(0xFFC57E81), Color(0xFFA58F9E), Color(0xFF6C5E82), Color(0xFF3F5B8B), Color(0xFF2E355A))),
    MonoPalette(4, "Esmeralda Menta", listOf(Color(0xFF7AB37C), Color(0xFF4E977A), Color(0xFF418B7E), Color(0xFF062223), Color(0xFF041516))),
    MonoPalette(5, "Bosque Salvia", listOf(Color(0xFFAEC3B0), Color(0xFF6B9071), Color(0xFF375534), Color(0xFF0F2A1D), Color(0xFF081810))),
    MonoPalette(6, "Azul Pizarra", listOf(Color(0xFFB4C5D8), Color(0xFF98A9BE), Color(0xFF728495), Color(0xFF4A5B6A), Color(0xFF263A47))),
    MonoPalette(7, "CoralAtardecer", listOf(Color(0xFFEAAC8B), Color(0xFFE88C7D), Color(0xFFE56B6F), Color(0xFFB56576), Color(0xFF355070))),
    MonoPalette(8, "Ciruela Cósmico", listOf(Color(0xFFE2CAD8), Color(0xFFC18DB4), Color(0xFF87A7D0), Color(0xFF27425D), Color(0xFF0E1B48))),
    MonoPalette(9, "Púrpura Real", listOf(Color(0xFFE7DBEF), Color(0xFFA56ABD), Color(0xFF6E3482), Color(0xFF49225B), Color(0xFF1D0928))),
    MonoPalette(10, "Hielo Ártico", listOf(Color(0xFFE1E5E9), Color(0xFF92B4CE), Color(0xFF6C8CA9), Color(0xFF1E3A5F), Color(0xFF0E1C24))),
    MonoPalette(11, "Grafito Oscuro", listOf(Color(0xFFB4C5D8), Color(0xFF728495), Color(0xFF4A5B6A), Color(0xFF263A47), Color(0xFF121D24))),
    MonoPalette(12, "Amatista", listOf(Color(0xFFDCD7D5), Color(0xFFBA96C1), Color(0xFF9C8CB9), Color(0xFF6C5F8D), Color(0xFF4B3F6E))),
    MonoPalette(13, "Verde Neón", listOf(Color(0xFFBBE053), Color(0xFF65DC91), Color(0xFF4A6D10), Color(0xFF1E472A), Color(0xFF0A1F11))),
    MonoPalette(14, "Azales Acero", listOf(Color(0xFFB1C9EF), Color(0xFF8AAEE0), Color(0xFF638ECB), Color(0xFF395886), Color(0xFF1A2A42))),
    MonoPalette(15, "Ámbar Dorado", listOf(Color(0xFFFFB32B), Color(0xFFF47F6B), Color(0xFFBB5098), Color(0xFF7A5197), Color(0xFF5344A9))),
    MonoPalette(16, "Sol Naranja", listOf(Color(0xFFFFB701), Color(0xFFFC8500), Color(0xFF8ECAE6), Color(0xFF209EBB), Color(0xFF023047))),
    MonoPalette(17, "Oliva Tierra", listOf(Color(0xFFA0BDB4), Color(0xFF557752), Color(0xFF435F42), Color(0xFF5B4E3F), Color(0xFF222B21))),
    MonoPalette(18, "Frambuesa", listOf(Color(0xFFFA7876), Color(0xFFEA4F88), Color(0xFFC0369D), Color(0xFF872CA2), Color(0xFF4B2991))),
    MonoPalette(19, "Lima Eléctrica", listOf(Color(0xFFC1FF1C), Color(0xFF78D23D), Color(0xFF3AA346), Color(0xFF007542), Color(0xFF003820))),
    MonoPalette(20, "Azul Eléctrico", listOf(Color(0xFF56E1E9), Color(0xFFBB63FF), Color(0xFF5B58EB), Color(0xFF112C70), Color(0xFF0A2353))),
    MonoPalette(21, "Rosa Pastel", listOf(Color(0xFFFF99BE), Color(0xFFFFC2D9), Color(0xFFC2E1FD), Color(0xFF97CBFB), Color(0xFF4E8BC4))),
    MonoPalette(22, "Dulce Algodón", listOf(Color(0xFFFFAFCC), Color(0xFFFFC8DD), Color(0xFFCDB4DB), Color(0xFFBDE0FE), Color(0xFFA2D2FF))),
    MonoPalette(23, "Lila Ensueño", listOf(Color(0xFFA594F9), Color(0xFFCDC1FF), Color(0xFFE5D9F2), Color(0xFF7371FC), Color(0xFF2E2B80))),
    MonoPalette(24, "Rosa Suave", listOf(Color(0xFFE27396), Color(0xFFEA9AB2), Color(0xFFEFCFE3), Color(0xFFB3DEE2), Color(0xFF2A3A3D))),
    MonoPalette(25, "Ciber Turquesa", listOf(Color(0xFFB0FFFF), Color(0xFF8FFAF1), Color(0xFF6FF4E3), Color(0xFF2DE9C7), Color(0xFF0D473D))),
    MonoPalette(26, "Cian Profundo", listOf(Color(0xFF12C5DA), Color(0xFF0EB3C8), Color(0xFF09A1B6), Color(0xFF058EA3), Color(0xFF007C91))),
    MonoPalette(27, "Índigo Resplandor", listOf(Color(0xFFA7ABFF), Color(0xFF8B94F0), Color(0xFF6F7EE1), Color(0xFF5267D2), Color(0xFF3650C3))),
    MonoPalette(28, "Cuádruple Vibrante", listOf(Color(0xFFFFCC33), Color(0xFFEA0770), Color(0xFF00A565), Color(0xFF035EAD), Color(0xFF022646))),
    MonoPalette(29, "Bruma Magenta", listOf(Color(0xFFF1BFE2), Color(0xFFF49BD5), Color(0xFFCFC0BD), Color(0xFF379FAC), Color(0xFF095761))),
    MonoPalette(30, "Brisa Marina", listOf(Color(0xFF98FF98), Color(0xFFADD8E6), Color(0xFFB39DDB), Color(0xFF4682B4), Color(0xFF008080)))
)

val LocalMonoPalette = staticCompositionLocalOf { allMonoPalettes[0] }

@Composable
fun PixelTheme(
    paletteId: Int = 1,
    content: @Composable () -> Unit
) {
    val palette = allMonoPalettes.find { it.id == paletteId } ?: allMonoPalettes[0]

    val colorScheme = darkColorScheme(
        primary = palette.primary,
        onPrimary = Color.Black,
        primaryContainer = palette.primary.copy(alpha = 0.25f),
        onPrimaryContainer = palette.primary,
        secondary = palette.secondary,
        onSecondary = Color.White,
        secondaryContainer = palette.secondary.copy(alpha = 0.2f),
        onSecondaryContainer = palette.secondary,
        tertiary = palette.tertiary,
        background = palette.background,
        onBackground = Color(0xFFF0F4F8),
        surface = palette.surface,
        onSurface = Color(0xFFF0F4F8),
        surfaceVariant = palette.surface.copy(alpha = 0.8f),
        onSurfaceVariant = Color(0xFFB0C4DE),
        outline = palette.primary.copy(alpha = 0.5f),
        outlineVariant = palette.primary.copy(alpha = 0.2f)
    )

    CompositionLocalProvider(LocalMonoPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
