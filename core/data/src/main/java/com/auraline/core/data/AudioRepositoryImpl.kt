package com.auraline.core.data

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.util.Log
import com.auraline.core.audio.AuraLineEngine
import com.auraline.core.domain.AudioCheckResult
import com.auraline.core.domain.AudioRoute
import com.auraline.core.domain.AudioRepository
import com.auraline.core.domain.AppSettings
import com.auraline.core.domain.AppTheme
import com.auraline.core.domain.CompressorParams
import com.auraline.core.domain.FactoryPresets
import com.auraline.core.domain.EngineStatus
import com.auraline.core.domain.EqualizerState
import com.auraline.core.domain.ReverbParams
import com.auraline.core.domain.StereoControls
import com.auraline.core.domain.ToneControls
import com.auraline.core.domain.VolumeControls
import com.auraline.core.domain.bandCenterHz
import com.auraline.core.domain.bandProbeHz
import com.auraline.core.domain.generateEqualizerBands
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

class AudioRepositoryImpl(context: Context) : AudioRepository {

    private val appContext = context.applicationContext

    private val statePrefs: SharedPreferences =
        appContext.getSharedPreferences("auraline_state", Context.MODE_PRIVATE)
    private val settingsPrefs: SharedPreferences =
        appContext.getSharedPreferences("auraline_settings", Context.MODE_PRIVATE)
    private val presetsPrefs: SharedPreferences =
        appContext.getSharedPreferences("auraline_presets", Context.MODE_PRIVATE)
    private val profilesPrefs: SharedPreferences =
        appContext.getSharedPreferences("auraline_profiles", Context.MODE_PRIVATE)

    private val runner = AudioCheckRunner(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(EqualizerState())
    override val stateFlow: StateFlow<EqualizerState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    override val settingsFlow: StateFlow<AppSettings> = _settings.asStateFlow()

    override val engineStatus: StateFlow<EngineStatus> = AuraLineEngine.status

    private var currentRoute = AudioRoute.UNKNOWN

    init {
        migrateLegacyPresets()
        val loadedSettings = loadSettings()
        _settings.value = loadedSettings
        val loadedState = syncVolumeFromSystem(loadState())
        _state.value = loadedState

        AuraLineEngine.initialize(appContext)
        AuraLineEngine.setState(loadedState)

        scope.launch {
            AuraLineEngine.status.collect { status ->
                if (status.route == currentRoute) return@collect
                val previous = currentRoute
                currentRoute = status.route
                if (!_settings.value.profilesByOutput || previous == AudioRoute.UNKNOWN) return@collect
                saveRouteProfile(previous)
                restoreRouteProfile(status.route)
            }
        }
    }


    /** Los preajustes antiguos se guardaban en texto plano y ya no se pueden restaurar. */
    private fun migrateLegacyPresets() {
        val editor = presetsPrefs.edit()
        var changed = false
        presetsPrefs.all.forEach { (key, value) ->
            val text = value as? String
            if (text == null || !text.trimStart().startsWith("{")) {
                editor.remove(key)
                changed = true
            }
        }
        if (changed) editor.apply()
    }

    // --- lectura / escritura de estado -------------------------------------

    override fun getState(): EqualizerState = _state.value

    override fun getSettings(): AppSettings = _settings.value

    override fun saveState(state: EqualizerState) {
        applyRuntime(state)
        persistState(state)
    }

    override fun saveSettings(settings: AppSettings) {
        _settings.value = settings
        settingsPrefs.edit()
            .putInt("num_bands", settings.numBands)
            .putFloat("gain_range", settings.gainRangeDb)
            .putString("motor_mode", settings.motorMode)
            .putBoolean("auto_start", settings.autoStart)
            .putBoolean("haptic", settings.hapticFeedback)
            .putBoolean("show_values", settings.showValues)
            .putString("spectrum", settings.spectrumAnalyzer)
            .putBoolean("profiles", settings.profilesByOutput)
            .putBoolean("auto_check", settings.autoCheck)
            .putString("language", settings.language)
            .putString("palette_key", settings.theme.paletteKey)
            .putInt("accent_color", settings.theme.accentColor)
            .putBoolean("theme_dark", settings.theme.isDark)
            .putBoolean("theme_follow", settings.theme.followSystem)
            .putBoolean("high_contrast", settings.theme.highContrast)
            .apply()
    }

    private fun applyRuntime(state: EqualizerState) {
        _state.value = state
        AuraLineEngine.setState(state)
    }

    private fun persistState(state: EqualizerState) {
        statePrefs.edit()
            .putInt("state_num_bands", state.numBands)
            .putFloat("state_preamp", state.preampDb)
            .putBoolean("state_motor", state.motorEnabled)
            .putBoolean("state_effects", state.effectsEnabled)
            .putBoolean("state_auto_preamp", state.autoPreamp)
            .putInt("state_route", state.selectedRoute)
            .putString("state_preset", state.presetName)
            .putFloat("state_sub", state.tone.subDb)
            .putFloat("state_bass", state.tone.bassDb)
            .putFloat("state_treble", state.tone.trebleDb)
            .putFloat("state_mid", state.tone.midDb)
            .putFloat("state_balance", state.stereo.balance)
            .putFloat("state_expansion", state.stereo.expansion)
            .putFloat("state_volume", state.volume.percent)
            .putFloat("state_reverb_decay", state.reverb.decay)
            .putFloat("state_reverb_mix", state.reverb.mix)
            .putFloat("state_reverb_damping", state.reverb.damping)
            .putFloat("state_reverb_size", state.reverb.size)
            .putFloat("state_comp_threshold", state.compressor.threshold)
            .putFloat("state_comp_ratio", state.compressor.ratio)
            .putFloat("state_comp_attack", state.compressor.attackMs)
            .putFloat("state_comp_release", state.compressor.releaseMs)
            .putFloat("state_comp_makeup", state.compressor.makeupGain)
            .putString("state_bands", state.bands.joinToString(",") { "${it.frequencyHz}:${it.gainDb}" })
            .apply()
    }

    private fun loadSettings(): AppSettings {
        val p = settingsPrefs
        return AppSettings(
            numBands = p.getInt("num_bands", 25).coerceIn(20, 25),
            gainRangeDb = p.getFloat("gain_range", 12f),
            motorMode = p.getString("motor_mode", "automatic") ?: "automatic",
            autoStart = p.getBoolean("auto_start", false),
            hapticFeedback = p.getBoolean("haptic", true),
            showValues = p.getBoolean("show_values", true),
            spectrumAnalyzer = p.getString("spectrum", "off") ?: "off",
            profilesByOutput = p.getBoolean("profiles", true),
            autoCheck = p.getBoolean("auto_check", false),
            language = p.getString("language", "system") ?: "system",
            theme = AppTheme(
                paletteKey = p.getString("palette_key", "aura") ?: "aura",
                accentColor = p.getInt("accent_color", 0xFF00E5CC.toInt()),
                isDark = p.getBoolean("theme_dark", true),
                followSystem = p.getBoolean("theme_follow", true),
                highContrast = p.getBoolean("high_contrast", false)
            )
        )
    }

    private fun loadState(): EqualizerState {
        val p = statePrefs
        val numBands = p.getInt("state_num_bands", 25).coerceIn(20, 25)
        val defaults = generateEqualizerBands(numBands)
        // Acepta el formato antiguo ("g,g,g") y el nuevo ("f:g,f:g").
        val stored = (p.getString("state_bands", "") ?: "")
            .split(",")
            .mapNotNull { token -> token.trim().substringAfterLast(':').toFloatOrNull() }
        val bands = defaults.mapIndexed { index, band -> band.copy(gainDb = stored.getOrElse(index) { 0f }) }

        return EqualizerState(
            numBands = numBands,
            gainRangeDb = _settings.value.gainRangeDb,
            preampDb = p.getFloat("state_preamp", 0f),
            bands = bands,
            motorEnabled = p.getBoolean("state_motor", false),
            effectsEnabled = p.getBoolean("state_effects", false),
            autoPreamp = p.getBoolean("state_auto_preamp", true),
            selectedRoute = p.getInt("state_route", 0),
            presetName = p.getString("state_preset", "Plano") ?: "Plano",
            tone = ToneControls(
                subDb = p.getFloat("state_sub", 0f),
                bassDb = p.getFloat("state_bass", 0f),
                trebleDb = p.getFloat("state_treble", 0f),
                midDb = p.getFloat("state_mid", 0f)
            ),
            stereo = StereoControls(
                balance = p.getFloat("state_balance", 0f),
                expansion = p.getFloat("state_expansion", 1f)
            ),
            volume = VolumeControls(percent = p.getFloat("state_volume", 100f)),
            reverb = ReverbParams(
                decay = p.getFloat("state_reverb_decay", 0.5f),
                mix = p.getFloat("state_reverb_mix", 0.25f),
                damping = p.getFloat("state_reverb_damping", 0.5f),
                size = p.getFloat("state_reverb_size", 0.5f)
            ),
            compressor = CompressorParams(
                threshold = p.getFloat("state_comp_threshold", -18f),
                ratio = p.getFloat("state_comp_ratio", 3f),
                attackMs = p.getFloat("state_comp_attack", 10f),
                releaseMs = p.getFloat("state_comp_release", 120f),
                makeupGain = p.getFloat("state_comp_makeup", 0f)
            )
        )
    }

    private fun syncVolumeFromSystem(state: EqualizerState): EqualizerState {
        if (!state.volume.linkedToSystem) return state
        return try {
            val manager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val percent = manager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max
            state.copy(volume = state.volume.copy(percent = percent))
        } catch (_: Throwable) {
            state
        }
    }

    // --- ajustes individuales ----------------------------------------------

    override fun toggleMotor(enabled: Boolean) = saveState(_state.value.copy(motorEnabled = enabled))

    override fun setEffectsEnabled(enabled: Boolean) = saveState(_state.value.copy(effectsEnabled = enabled))

    override fun setPreamp(db: Float) = saveState(_state.value.copy(preampDb = db))

    override fun setBandGain(bandId: Int, gainDb: Float) {
        val current = _state.value
        val bands = current.bands.map { band ->
            if (band.id == bandId) band.copy(gainDb = gainDb) else band
        }
        saveState(current.copy(bands = bands, presetName = "Personalizado"))
    }

    override fun setSubBass(db: Float) {
        val tone = _state.value.tone
        saveState(_state.value.copy(tone = tone.copy(subDb = db)))
    }

    override fun setTone(bassDb: Float, midDb: Float, trebleDb: Float) {
        val tone = _state.value.tone
        saveState(_state.value.copy(tone = tone.copy(bassDb = bassDb, midDb = midDb, trebleDb = trebleDb)))
    }

    override fun setToneFrequencies(bassHz: Float, trebleHz: Float) {
        val tone = _state.value.tone
        saveState(
            _state.value.copy(
                tone = tone.copy(
                    bassFrequencyHz = bassHz.coerceIn(40f, 400f),
                    trebleFrequencyHz = trebleHz.coerceIn(2000f, 16000f)
                )
            )
        )
    }

    override fun setBypass(enabled: Boolean) = saveState(_state.value.copy(bypass = enabled))

    override fun setGainRange(db: Float) {
        val range = db.coerceIn(6f, 24f)
        saveSettings(_settings.value.copy(gainRangeDb = range))
        val current = _state.value
        val clamped = current.bands.map { it.copy(gainDb = it.gainDb.coerceIn(-range, range)) }
        saveState(current.copy(gainRangeDb = range, bands = clamped))
    }

    override fun applyFactoryPreset(name: String): Boolean {
        val preset = FactoryPresets.firstOrNull { it.name == name } ?: return false
        val current = _state.value
        val bands = current.bands.ifEmpty { generateEqualizerBands(current.numBands) }
        val centers = bands.indices.map { bandCenterHz(bands, it) }
        val gains = FloatArray(bands.size)

        preset.gains.forEach { (frequency, gain) ->
            val index = centers.indices.minByOrNull { abs(centers[it] - frequency) } ?: return@forEach
            gains[index] = (gains[index] + gain).coerceIn(-current.gainRangeDb, current.gainRangeDb)
        }

        val tuned = bands.mapIndexed { index, band -> band.copy(gainDb = gains[index]) }
        saveState(current.copy(bands = tuned, presetName = preset.name))
        return true
    }

    override fun setBalance(balance: Float) =
        saveState(_state.value.copy(stereo = _state.value.stereo.copy(balance = balance)))

    override fun setExpansion(expansion: Float) =
        saveState(_state.value.copy(stereo = _state.value.stereo.copy(expansion = expansion)))

    override fun setVolume(percent: Float) {
        val current = _state.value
        saveState(current.copy(volume = current.volume.copy(percent = percent)))
        if (current.volume.linkedToSystem) {
            runCatching {
                val manager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val target = (percent / 100f * max).toInt().coerceIn(0, max)
                manager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            }
        }
    }

    override fun setReverb(params: ReverbParams) = saveState(_state.value.copy(reverb = params))

    override fun setCompressor(params: CompressorParams) = saveState(_state.value.copy(compressor = params))

    override fun resetControls() {
        val current = _state.value
        saveState(
            current.copy(
                tone = ToneControls(),
                stereo = StereoControls(),
                volume = VolumeControls(percent = current.volume.percent)
            )
        )
    }

    override fun resetAll() {
        // Restablecer la curva no debe apagar el motor ni tocar el volumen.
        val current = _state.value
        saveState(
            current.copy(
                bands = generateEqualizerBands(current.numBands),
                tone = ToneControls(
                    bassFrequencyHz = current.tone.bassFrequencyHz,
                    trebleFrequencyHz = current.tone.trebleFrequencyHz
                ),
                preampDb = 0f,
                bypass = false,
                presetName = "Plano"
            )
        )
    }

    override fun setNumBands(count: Int) {
        val bands = count.coerceIn(20, 25)
        saveSettings(_settings.value.copy(numBands = bands))
        saveState(_state.value.copy(numBands = bands, bands = generateEqualizerBands(bands)))
    }

    override fun setAutoStart(enabled: Boolean) = saveSettings(_settings.value.copy(autoStart = enabled))

    override fun setHapticFeedback(enabled: Boolean) =
        saveSettings(_settings.value.copy(hapticFeedback = enabled))

    override fun setThemePalette(paletteKey: String, accentColor: Int) {
        val settings = _settings.value
        saveSettings(settings.copy(theme = settings.theme.copy(paletteKey = paletteKey, accentColor = accentColor)))
    }

    // --- presets -----------------------------------------------------------

    override fun savePreset(name: String) {
        presetsPrefs.edit().putString(name, stateToJson(_state.value.copy(presetName = name))).apply()
    }

    override fun applyPreset(name: String): Boolean {
        val json = presetsPrefs.getString(name, null) ?: return false
        val restored = runCatching { stateFromJson(json) }.getOrNull() ?: return false
        saveState(restored.copy(presetName = name))
        return true
    }

    override fun deletePreset(name: String) {
        presetsPrefs.edit().remove(name).apply()
    }

    override fun listPresets(): List<String> = presetsPrefs.all.keys.sorted()

    private fun stateToJson(state: EqualizerState): String {
        val root = JSONObject()
        root.put("numBands", state.numBands)
        root.put("preamp", state.preampDb.toDouble())
        root.put("motor", state.motorEnabled)
        root.put("effects", state.effectsEnabled)
        root.put("bass", state.tone.bassDb.toDouble())
        root.put("treble", state.tone.trebleDb.toDouble())
        root.put("balance", state.stereo.balance.toDouble())
        root.put("expansion", state.stereo.expansion.toDouble())
        root.put("volume", state.volume.percent.toDouble())

        val bands = JSONArray()
        state.bands.forEach { band ->
            bands.put(JSONObject().put("f", band.frequencyHz.toDouble()).put("g", band.gainDb.toDouble()))
        }
        root.put("bands", bands)

        root.put("reverb", JSONObject()
            .put("decay", state.reverb.decay.toDouble())
            .put("mix", state.reverb.mix.toDouble())
            .put("damping", state.reverb.damping.toDouble())
            .put("size", state.reverb.size.toDouble()))

        root.put("compressor", JSONObject()
            .put("threshold", state.compressor.threshold.toDouble())
            .put("ratio", state.compressor.ratio.toDouble())
            .put("attack", state.compressor.attackMs.toDouble())
            .put("release", state.compressor.releaseMs.toDouble())
            .put("makeup", state.compressor.makeupGain.toDouble()))

        return root.toString()
    }

    private fun stateFromJson(text: String): EqualizerState {
        val root = JSONObject(text)
        val bandCount = root.optInt("numBands", 25).coerceIn(20, 25)
        val defaults = generateEqualizerBands(bandCount)
        val array = root.optJSONArray("bands")
        val bands = defaults.mapIndexed { index, fallback ->
            val entry = array?.optJSONObject(index)
            if (entry == null) fallback else fallback.copy(
                frequencyHz = entry.optDouble("f", fallback.frequencyHz.toDouble()).toFloat(),
                gainDb = entry.optDouble("g", 0.0).toFloat()
            )
        }
        val reverb = root.optJSONObject("reverb")
        val compressor = root.optJSONObject("compressor")

        return EqualizerState(
            numBands = bandCount,
            preampDb = root.optDouble("preamp", 0.0).toFloat(),
            bands = bands,
            motorEnabled = root.optBoolean("motor", false),
            effectsEnabled = root.optBoolean("effects", false),
            tone = ToneControls(
                bassDb = root.optDouble("bass", 0.0).toFloat(),
                trebleDb = root.optDouble("treble", 0.0).toFloat()
            ),
            stereo = StereoControls(
                balance = root.optDouble("balance", 0.0).toFloat(),
                expansion = root.optDouble("expansion", 1.0).toFloat()
            ),
            volume = VolumeControls(percent = root.optDouble("volume", 100.0).toFloat()),
            reverb = ReverbParams(
                decay = reverb?.optDouble("decay", 0.5)?.toFloat() ?: 0.5f,
                mix = reverb?.optDouble("mix", 0.25)?.toFloat() ?: 0.25f,
                damping = reverb?.optDouble("damping", 0.5)?.toFloat() ?: 0.5f,
                size = reverb?.optDouble("size", 0.5)?.toFloat() ?: 0.5f
            ),
            compressor = CompressorParams(
                threshold = compressor?.optDouble("threshold", -18.0)?.toFloat() ?: -18f,
                ratio = compressor?.optDouble("ratio", 3.0)?.toFloat() ?: 3f,
                attackMs = compressor?.optDouble("attack", 10.0)?.toFloat() ?: 10f,
                releaseMs = compressor?.optDouble("release", 120.0)?.toFloat() ?: 120f,
                makeupGain = compressor?.optDouble("makeup", 0.0)?.toFloat() ?: 0f
            )
        )
    }

    // --- perfiles por salida ----------------------------------------------

    private fun saveRouteProfile(route: AudioRoute) {
        val gains = _state.value.bands.joinToString(",") { it.gainDb.toString() }
        profilesPrefs.edit().putString("gains_${route.name}", gains).apply()
    }

    private fun restoreRouteProfile(route: AudioRoute) {
        val stored = profilesPrefs.getString("gains_${route.name}", null) ?: return
        val gains = stored.split(",").mapNotNull { it.toFloatOrNull() }
        if (gains.isEmpty()) return
        val current = _state.value
        val bands = current.bands.mapIndexed { index, band ->
            band.copy(gainDb = gains.getOrElse(index) { band.gainDb })
        }
        saveState(current.copy(bands = bands))
    }

    // --- comprobación acústica --------------------------------------------


    override suspend fun runAudioCheck(): AudioCheckResult {
        val route = AuraLineEngine.status.value.route
        if (route != AudioRoute.SPEAKER) {
            return AudioCheckResult(
                message = "Cambia la salida al altavoz para medir (ahora: ${route.label})."
            )
        }

        val original = _state.value
        val bands = original.bands.ifEmpty { generateEqualizerBands(original.numBands) }
        // Frecuencias donde el altavoz y el micrófono del móvil rinden bien.
        val target = bands.indices.minByOrNull { abs(bandCenterHz(bands, it) - 1600f) } ?: bands.lastIndex
        val lowIndex = bands.indices.minByOrNull { abs(bandCenterHz(bands, it) - 400f) } ?: 0
        val highTone = bandProbeHz(bands, target).toDouble().coerceIn(1200.0, 3000.0)
        val lowTone = bandProbeHz(bands, lowIndex).toDouble().coerceIn(300.0, 700.0)

        val testBands = bands.mapIndexed { index, band ->
            band.copy(gainDb = if (index == target) 18f else 0f)
        }
        val testState = original.copy(
            motorEnabled = true,
            effectsEnabled = false,
            preampDb = 0f,
            tone = ToneControls(),
            stereo = StereoControls(),
            bypass = false,
            bands = testBands
        )

        Log.i(
            "AuraLine",
            "Check: banda $target centro ${bandCenterHz(bands, target).toInt()} " +
                "corte ${bands[target].frequencyHz.toInt()} Hz · bypass original=${original.bypass} " +
                "motor original=${original.motorEnabled}"
        )

        Log.i(
            "AuraLine",
            "Atenuación: banda $target corte ${bands[target].frequencyHz.toInt()} Hz, " +
                "sondas ${lowTone.toInt()}/${highTone.toInt()} Hz, motor previo=${original.motorEnabled}"
        )

        return try {
            applyRuntime(original.copy(motorEnabled = false))
            AuraLineEngine.flush()
            val (lowReference, highReference) = runner.measureTwo(lowTone, highTone, PROBE_DURATION)

            applyRuntime(testState)
            AuraLineEngine.flush()
            val (lowBoosted, highBoosted) = runner.measureTwo(lowTone, highTone, PROBE_DURATION)

            // Cociente entre las dos frecuencias dentro de la MISMA grabación: el AGC del
            // micrófono afecta igual a ambas y desaparece al restar.
            val referenceRatio = highReference - lowReference
            val boostedRatio = highBoosted - lowBoosted
            val selectivity = boostedRatio - referenceRatio

            Log.i(
                "AuraLine",
                "Sonda " + "${lowTone.toInt()}/${highTone.toInt()} Hz: " +
                    "ref ${"%.1f".format(lowReference)}/${"%.1f".format(highReference)} dBFS, " +
                    "test ${"%.1f".format(lowBoosted)}/${"%.1f".format(highBoosted)} dBFS, " +
                    "selectividad ${"%.1f".format(selectivity)} dB (banda $target a +18 dB)"
            )

            val passed = selectivity >= 8f
            AudioCheckResult(
                referenceDb = highReference,
                processedDb = highBoosted,
                deltaDb = selectivity,
                passed = passed,
                message = if (passed) {
                    "La banda de ${highTone.toInt()} Hz sube ${"%.1f".format(selectivity)} dB respecto a la de ${lowTone.toInt()} Hz: cada banda mueve su rango."
                } else {
                    "${highTone.toInt()} Hz frente a ${lowTone.toInt()} Hz: ${"%+.1f".format(selectivity)} dB. Sube el volumen multimedia y repite."
                }
            )
        } catch (error: Throwable) {
            Log.w("AuraLine", "Medición fallida", error)
            AudioCheckResult(message = "No se pudo medir: ${error.message}")
        } finally {
            applyRuntime(original)
            AuraLineEngine.flush()
        }
    }

    private companion object {
        const val PROBE_DURATION = 2.2
    }

}
