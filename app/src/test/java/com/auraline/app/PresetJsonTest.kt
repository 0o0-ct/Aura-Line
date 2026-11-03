package com.auraline.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PresetJsonTest {

    private val requiredPresets = listOf(
        "flat.json",
        "bass_boost.json",
        "deep_bass.json",
        "balanced.json",
        "vocal.json",
        "acoustic.json",
        "classical.json",
        "jazz.json",
        "rock.json",
        "pop.json",
        "electronic.json",
        "hip_hop.json",
        "metal.json",
        "podcast.json",
        "cinema.json"
    )

    @Test
    fun testAll15PresetsExistAndAreValidJson() {
        val presetsDir = File("src/main/assets/presets")
        assertTrue("El directorio de presets en assets debe existir", presetsDir.exists() && presetsDir.isDirectory)

        assertEquals("Deben existir exactamente 15 preajustes de fábrica", 15, requiredPresets.size)

        for (presetName in requiredPresets) {
            val file = File(presetsDir, presetName)
            assertTrue("El archivo $presetName debe existir en assets/presets/", file.exists() && file.isFile)

            val jsonContent = file.readText()
            val json = JSONObject(jsonContent)

            assertTrue("Debe contener campo 'id'", json.has("id"))
            assertTrue("Debe contener campo 'name'", json.has("name"))
            assertTrue("Debe contener campo 'category'", json.has("category"))
            assertTrue("Debe contener campo 'description'", json.has("description"))
            assertTrue("Debe contener campo 'gains'", json.has("gains"))

            val preamp = json.optDouble("preampDb", 0.0)
            assertTrue("Preamp debe estar en rango seguro [-36, 36]", preamp in -36.0..36.0)

            val gainsObj = json.getJSONObject("gains")
            assertTrue("Debe contener al menos 20 bandas definidas", gainsObj.length() >= 20)

            val keys = gainsObj.keys()
            while (keys.hasNext()) {
                val freqKey = keys.next()
                val freq = freqKey.toDoubleOrNull()
                assertNotNull("La clave de frecuencia '$freqKey' debe ser un número", freq)
                assertTrue("Frecuencia $freq debe ser positiva", freq!! > 0.0)

                val gainVal = gainsObj.getDouble(freqKey)
                assertTrue("Ganancia de $gainVal dB en $freq Hz debe estar en rango [-36, 36]", gainVal in -36.0..36.0)
            }
        }
    }
}
