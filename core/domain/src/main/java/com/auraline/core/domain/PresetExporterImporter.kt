package com.auraline.core.domain

import java.util.Locale

/**
 * Importador y exportador de preajustes de ecualización.
 * Soporta exportación/importación en formato JSON nativo de AuraLine y
 * análisis de archivos de texto AutoEq (ecualización paramétrica).
 */
object PresetExporterImporter {

    fun exportPresetToJson(name: String, state: EqualizerState): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"name\": \"$name\",\n")
        sb.append("  \"version\": \"4.1.0\",\n")
        sb.append(String.format(Locale.US, "  \"preampDb\": %.2f,\n", state.preampDb))
        sb.append("  \"numBands\": ${state.numBands},\n")
        sb.append("  \"bands\": [\n")
        state.bands.forEachIndexed { i, b ->
            sb.append(String.format(Locale.US, "    {\"freq\": %.1f, \"gain\": %.2f, \"q\": %.2f}", b.frequencyHz, b.gainDb, b.q))
            if (i < state.bands.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ],\n")
        sb.append("  \"tone\": {\n")
        sb.append(String.format(Locale.US, "    \"subDb\": %.2f,\n", state.tone.subDb))
        sb.append(String.format(Locale.US, "    \"bassDb\": %.2f,\n", state.tone.bassDb))
        sb.append(String.format(Locale.US, "    \"midDb\": %.2f,\n", state.tone.midDb))
        sb.append(String.format(Locale.US, "    \"trebleDb\": %.2f\n", state.tone.trebleDb))
        sb.append("  }\n")
        sb.append("}")
        return sb.toString()
    }

    fun importPresetFromJson(jsonString: String): UserPreset {
        val nameMatch = "\"name\":\\s*\"([^\"]+)\"".toRegex().find(jsonString)
        val name = nameMatch?.groupValues?.get(1) ?: "Preajuste Importado"

        val preampMatch = "\"preampDb\":\\s*([-+]?\\d*\\.?\\d+)".toRegex().find(jsonString)
        val preampDb = preampMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f

        val numBandsMatch = "\"numBands\":\\s*(\\d+)".toRegex().find(jsonString)
        val numBands = numBandsMatch?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(20, 25) ?: 25

        val bandsList = mutableListOf<EqBand>()
        val bandMatches = "\\{\"freq\":\\s*([\\d.]+),\\s*\"gain\":\\s*([-+]?[\\d.]+),\\s*\"q\":\\s*([\\d.]+)\\}".toRegex().findAll(jsonString)
        var idx = 0
        for (m in bandMatches) {
            val freq = m.groupValues[1].toFloatOrNull() ?: 1000f
            val gain = m.groupValues[2].toFloatOrNull() ?: 0f
            val q = m.groupValues[3].toFloatOrNull() ?: 1.41f
            bandsList.add(EqBand(id = idx++, frequencyHz = freq, gainDb = gain, q = q))
        }

        val subMatch = "\"subDb\":\\s*([-+]?\\d*\\.?\\d+)".toRegex().find(jsonString)
        val bassMatch = "\"bassDb\":\\s*([-+]?\\d*\\.?\\d+)".toRegex().find(jsonString)
        val midMatch = "\"midDb\":\\s*([-+]?\\d*\\.?\\d+)".toRegex().find(jsonString)
        val trebleMatch = "\"trebleDb\":\\s*([-+]?\\d*\\.?\\d+)".toRegex().find(jsonString)

        val toneControls = ToneControls(
            subDb = subMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f,
            bassDb = bassMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f,
            midDb = midMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f,
            trebleDb = trebleMatch?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        )

        val state = EqualizerState(
            numBands = numBands,
            preampDb = preampDb,
            bands = if (bandsList.isNotEmpty()) bandsList else generateEqualizerBands(numBands),
            tone = toneControls,
            presetName = name
        )

        return UserPreset(name = name, state = state)
    }

    /**
     * Parsea un archivo de texto en formato AutoEq (ej. EqualizerAPO / GraphicEQ):
     * "Preamp: -3.5 dB"
     * "Filter 1: ON PK Fc 60 Hz Gain 4.5 dB Q 1.41"
     */
    fun parseAutoEqText(text: String, targetBands: List<EqBand>): Map<Float, Float> {
        val gains = mutableMapOf<Float, Float>()
        val lines = text.lines()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("GraphicEQ:", ignoreCase = true)) {
                // Formato GraphicEQ: GraphicEQ: 20 -3.5; 50 1.2; 100 2.5
                val pairs = trimmed.substringAfter(":").split(";")
                for (pair in pairs) {
                    val parts = pair.trim().split("\\s+".toRegex())
                    if (parts.size >= 2) {
                        val freq = parts[0].toFloatOrNull()
                        val gain = parts[1].toFloatOrNull()
                        if (freq != null && gain != null) {
                            gains[freq] = gain
                        }
                    }
                }
            } else if (trimmed.contains("Fc", ignoreCase = true) && trimmed.contains("Gain", ignoreCase = true)) {
                // Formato Paramétrico: Filter 1: ON PK Fc 60 Hz Gain 4.5 dB Q 1.41
                val fcIndex = trimmed.indexOf("Fc", ignoreCase = true)
                val gainIndex = trimmed.indexOf("Gain", ignoreCase = true)
                if (fcIndex != -1 && gainIndex != -1) {
                    val fcSub = trimmed.substring(fcIndex + 2).trim().split("\\s+".toRegex())
                    val gainSub = trimmed.substring(gainIndex + 4).trim().split("\\s+".toRegex())

                    val freq = fcSub.firstOrNull()?.toFloatOrNull()
                    val gain = gainSub.firstOrNull()?.toFloatOrNull()

                    if (freq != null && gain != null) {
                        gains[freq] = gain
                    }
                }
            }
        }

        // Mapear los valores detectados de AutoEq a las 25 bandas objetivo
        val result = mutableMapOf<Float, Float>()
        targetBands.forEach { band ->
            val closestFreq = gains.keys.minByOrNull { kotlin.math.abs(it - band.frequencyHz) }
            val gain = if (closestFreq != null && kotlin.math.abs(closestFreq - band.frequencyHz) < band.frequencyHz * 0.4f) {
                gains[closestFreq] ?: 0f
            } else {
                0f
            }
            result[band.frequencyHz] = gain
        }

        return result
    }
}
