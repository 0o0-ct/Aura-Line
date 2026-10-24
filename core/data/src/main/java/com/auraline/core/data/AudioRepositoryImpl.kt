package com.auraline.core.data

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Build
import android.util.Log
import com.auraline.core.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AudioRepositoryImpl(
    private val context: Context
) : AudioRepository {

    private val statePrefs: SharedPreferences =
        context.getSharedPreferences("auraline_state", Context.MODE_PRIVATE)
    private val settingsPrefs: SharedPreferences =
        context.getSharedPreferences("auraline_settings", Context.MODE_PRIVATE)
    private val presetsPrefs: SharedPreferences =
        context.getSharedPreferences("auraline_presets", Context.MODE_PRIVATE)

    private var globalDp: DynamicsProcessing? = null
    private var globalEq: Equalizer? = null
    private var globalBass: BassBoost? = null
    private var globalVirt: Virtualizer? = null
    private var globalReverb: EnvironmentalReverb? = null

    private val _state = MutableStateFlow(EqualizerState())
    override val stateFlow: StateFlow<EqualizerState> = _state

    private val _settings = MutableStateFlow(AppSettings())
    override val settingsFlow: StateFlow<AppSettings> = _settings

    init {
        _settings.value = loadSettings()
        val loaded = loadState()
        val synced = try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val sysPct = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) * 100f / max
            if (loaded.volume.linkedToSystem) loaded.copy(volume = loaded.volume.copy(percent = sysPct)) else loaded
        } catch (_: Exception) { loaded }
        _state.value = synced
        com.auraline.core.audio.GlobalAudioEffectManager.setState(synced)
    }

    private fun loadSettings(): AppSettings {
        val p = settingsPrefs
        return AppSettings(
            numBands = p.getInt("num_bands", 25),
            gainRangeDb = p.getFloat("gain_range", 12f),
            motorMode = p.getString("motor_mode", "automatic")!!,
            autoStart = p.getBoolean("auto_start", false),
            hapticFeedback = p.getBoolean("haptic", true),
            showValues = p.getBoolean("show_values", true),
            spectrumAnalyzer = p.getString("spectrum", "off")!!,
            profilesByOutput = p.getBoolean("profiles", true),
            language = p.getString("language", "system")!!,
            theme = AppTheme(
                paletteKey = p.getString("palette_key", "aura")!!,
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
        val defaultBands = generateEqualizerBands(numBands)
        val gainsStr = p.getString("state_bands", "") ?: ""
        val gains = gainsStr.split(",").mapNotNull { it.toFloatOrNull() }
        val bands = defaultBands.mapIndexed { i, band ->
            band.copy(gainDb = gains.getOrElse(i) { 0f })
        }
        return EqualizerState(
            numBands = numBands,
            preampDb = p.getFloat("state_preamp", 0f),
            bands = bands,
            motorEnabled = p.getBoolean("state_motor", false),
            effectsEnabled = p.getBoolean("state_effects", false),
            autoPreamp = p.getBoolean("state_auto_preamp", true),
            selectedRoute = p.getInt("state_route", 0),
            presetName = p.getString("state_preset", "Plano")!!,
            tone = ToneControls(
                bassDb = p.getFloat("state_bass", 0f),
                trebleDb = p.getFloat("state_treble", 0f)
            ),
            stereo = StereoControls(
                balance = p.getFloat("state_balance", 0f),
                expansion = p.getFloat("state_expansion", 1f)
            ),
            volume = VolumeControls(
                percent = p.getFloat("state_volume", 100f)
            )
        )
    }

    private fun applyAudioEffects(state: EqualizerState) {
        try {
            if (!state.motorEnabled) {
                globalDp?.enabled = false
                globalDp?.release()
                globalDp = null
                globalEq?.enabled = false
                globalEq?.release()
                globalEq = null
                globalBass?.enabled = false
                globalBass?.release()
                globalBass = null
                globalVirt?.enabled = false
                globalVirt?.release()
                globalVirt = null
                globalReverb?.enabled = false
                globalReverb?.release()
                globalReverb = null
                return
            }

            val numBands = state.numBands.coerceIn(20, 25)
            val defaultBands = generateEqualizerBands(numBands)
            val bands = if (state.bands.isNotEmpty()) {
                state.bands.mapIndexed { i, b ->
                    if (b.frequencyHz <= 0f) b.copy(frequencyHz = defaultBands.getOrElse(i) { defaultBands.last() }.frequencyHz) else b
                }
            } else {
                defaultBands
            }

            // --- 1. DynamicsProcessing API ---
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    if (globalDp == null) {
                        val preEq = DynamicsProcessing.Eq(true, true, numBands)
                        for (b in 0 until numBands.coerceAtMost(bands.size)) {
                            val freq = bands[b].frequencyHz
                            val dpBand = DynamicsProcessing.EqBand(true, freq, 0f)
                            preEq.setBand(b, dpBand)
                        }

                        val builder = DynamicsProcessing.Config.Builder(
                            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                            2, // 2 channels
                            true, // preEq
                            numBands,
                            false,
                            0,
                            false,
                            0,
                            true // limiter
                        )
                        builder.setPreEqByChannelIndex(0, preEq)
                        builder.setPreEqByChannelIndex(1, preEq)

                        globalDp = DynamicsProcessing(0, 0, builder.build())
                        globalDp?.enabled = true
                        Log.d("AuraLine", "DynamicsProcessing session0 initialized ($numBands bands)")
                    }

                    val dp = globalDp
                    if (dp != null) {
                        dp.enabled = true

                        // Master Preamp Input Gain
                        val preampGain = state.preampDb * 1.5f
                        val balance = state.stereo.balance / 100f
                        val leftIn = preampGain + (if (balance > 0f) -balance * 6f else 0f)
                        val rightIn = preampGain + (if (balance < 0f) balance * 6f else 0f)

                        dp.setInputGainbyChannel(0, leftIn)
                        dp.setInputGainbyChannel(1, rightIn)

                        // Set PreEQ Bands (25 Bands + Graves/Agudos Shelving Boost/Cut)
                        for (i in 0 until numBands.coerceAtMost(bands.size)) {
                            val band = bands[i]
                            val freq = band.frequencyHz

                            // Graves Low-Shelf Boost/Cut (frequencies <= 350Hz)
                            val bassShelf = if (freq <= 350f) state.tone.bassDb * 1.5f else 0f

                            // Agudos High-Shelf Boost/Cut (frequencies >= 2500Hz)
                            val trebleShelf = if (freq >= 2500f) state.tone.trebleDb * 1.5f else 0f

                            val totalGainDb = (band.gainDb * 1.5f) + bassShelf + trebleShelf
                            val dpBand = DynamicsProcessing.EqBand(true, freq, totalGainDb)
                            dp.setPreEqBandAllChannelsTo(i, dpBand)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AuraLine", "DynamicsProcessing session0 error: $e")
                }
            }

            // --- 2. Fallback Equalizer API ---
            if (globalEq == null) {
                try {
                    globalEq = Equalizer(0, 0)
                    globalEq?.enabled = true
                    Log.d("AuraLine", "Equalizer session0 initialized, bands=${globalEq?.numberOfBands}")
                } catch (e: Exception) {
                    Log.e("AuraLine", "Equalizer session0 error: $e")
                }
            }

            val eq = globalEq
            if (eq != null) {
                eq.enabled = true
                val numDevBands = eq.numberOfBands
                for (i in 0 until numDevBands) {
                    val centerHz = eq.getCenterFreq(i.toShort()) / 1000f
                    val nearby = bands.filter { kotlin.math.abs(it.frequencyHz - centerHz) < centerHz * 0.6f + 50f }
                    val baseGain = if (nearby.isNotEmpty()) nearby.map { it.gainDb }.average().toFloat() else 0f
                    val bassShelf = if (centerHz <= 350f) state.tone.bassDb * 1.5f else 0f
                    val trebleShelf = if (centerHz >= 2500f) state.tone.trebleDb * 1.5f else 0f
                    val totalGain = (baseGain * 1.5f) + bassShelf + trebleShelf + (state.preampDb * 1.5f)
                    val level = (totalGain * 100f).toInt().coerceIn(-1500, 1500).toShort()
                    try { eq.setBandLevel(i.toShort(), level) } catch (_: Exception) { }
                }
            }

            // --- 3. Hardware BassBoost ---
            try {
                if (globalBass == null) {
                    globalBass = BassBoost(0, 0)
                }
                val bass = globalBass
                if (bass != null) {
                    val strength = ((state.tone.bassDb + 12f) / 24f * 1000f).toInt().coerceIn(0, 1000).toShort()
                    bass.setStrength(strength)
                    bass.enabled = state.tone.bassDb > -11f
                }
            } catch (e: Exception) {
                Log.e("AuraLine", "BassBoost error: $e")
            }

            // --- 4. Hardware Virtualizer ---
            try {
                if (globalVirt == null) {
                    globalVirt = Virtualizer(0, 0)
                }
                val virt = globalVirt
                if (virt != null) {
                    val strength = ((state.tone.trebleDb + 12f) / 24f * 1000f).toInt().coerceIn(0, 1000).toShort()
                    virt.setStrength(strength)
                    virt.enabled = state.tone.trebleDb > -11f
                }
            } catch (e: Exception) {
                Log.e("AuraLine", "Virtualizer error: $e")
            }

            // --- 5. Environmental Reverb ---
            if (state.effectsEnabled) {
                if (globalReverb == null) {
                    try {
                        globalReverb = EnvironmentalReverb(0, 0)
                        globalReverb?.enabled = true
                    } catch (_: Exception) { }
                }
                globalReverb?.apply {
                    try { reverbLevel = (state.reverb.mix * 1000).toInt().toShort() } catch (_: Exception) { }
                    try { decayTime = (state.reverb.decay * 5000).toInt() } catch (_: Exception) { }
                    enabled = true
                }
            } else {
                globalReverb?.enabled = false
                globalReverb?.release()
                globalReverb = null
            }
        } catch (e: Exception) {
            Log.e("AuraLine", "applyAudioEffects error: $e")
        }
    }

    override fun getState(): EqualizerState = _state.value

    override fun saveState(state: EqualizerState) {
        _state.value = state
        applyAudioEffects(state)
        com.auraline.core.audio.GlobalAudioEffectManager.setState(state)
        val editor = statePrefs.edit()
        editor.putInt("state_num_bands", state.numBands)
        editor.putFloat("state_preamp", state.preampDb)
        editor.putBoolean("state_motor", state.motorEnabled)
        editor.putBoolean("state_effects", state.effectsEnabled)
        editor.putBoolean("state_auto_preamp", state.autoPreamp)
        editor.putInt("state_route", state.selectedRoute)
        editor.putString("state_preset", state.presetName)
        editor.putFloat("state_bass", state.tone.bassDb)
        editor.putFloat("state_treble", state.tone.trebleDb)
        editor.putFloat("state_balance", state.stereo.balance)
        editor.putFloat("state_expansion", state.stereo.expansion)
        editor.putFloat("state_volume", state.volume.percent)
        editor.putString("state_bands", state.bands.joinToString(",") { it.gainDb.toString() })
        editor.apply()
    }

    override fun getSettings(): AppSettings = _settings.value

    override fun saveSettings(settings: AppSettings) {
        _settings.value = settings
        val editor = settingsPrefs.edit()
        editor.putInt("num_bands", settings.numBands)
        editor.putFloat("gain_range", settings.gainRangeDb)
        editor.putString("motor_mode", settings.motorMode)
        editor.putBoolean("auto_start", settings.autoStart)
        editor.putBoolean("haptic", settings.hapticFeedback)
        editor.putBoolean("show_values", settings.showValues)
        editor.putString("spectrum", settings.spectrumAnalyzer)
        editor.putBoolean("profiles", settings.profilesByOutput)
        editor.putString("language", settings.language)
        editor.putString("palette_key", settings.theme.paletteKey)
        editor.putInt("accent_color", settings.theme.accentColor)
        editor.putBoolean("theme_dark", settings.theme.isDark)
        editor.putBoolean("theme_follow", settings.theme.followSystem)
        editor.putBoolean("high_contrast", settings.theme.highContrast)
        editor.apply()
    }

    override fun toggleMotor(enabled: Boolean) {
        val current = _state.value
        saveState(current.copy(motorEnabled = enabled))
    }

    override fun setPreamp(db: Float) {
        val current = _state.value
        saveState(current.copy(preampDb = db))
    }

    override fun setBandGain(bandId: Int, gainDb: Float) {
        val current = _state.value
        val defaultBands = generateEqualizerBands(current.numBands)
        val bands = if (current.bands.isEmpty()) defaultBands else current.bands
        val newBands = bands.mapIndexed { i, band ->
            if (band.id == bandId) {
                val freq = if (band.frequencyHz > 0f) band.frequencyHz else defaultBands.getOrElse(i) { defaultBands.last() }.frequencyHz
                band.copy(gainDb = gainDb, frequencyHz = freq)
            } else {
                if (band.frequencyHz <= 0f) band.copy(frequencyHz = defaultBands.getOrElse(i) { defaultBands.last() }.frequencyHz) else band
            }
        }
        saveState(current.copy(bands = newBands))
    }

    override fun setTone(bassDb: Float, trebleDb: Float) {
        val current = _state.value
        saveState(current.copy(tone = ToneControls(bassDb, trebleDb)))
    }

    override fun setBalance(balance: Float) {
        val current = _state.value
        saveState(current.copy(stereo = current.stereo.copy(balance = balance)))
    }

    override fun setExpansion(expansion: Float) {
        val current = _state.value
        saveState(current.copy(stereo = current.stereo.copy(expansion = expansion)))
    }

    override fun setVolume(percent: Float) {
        val current = _state.value
        saveState(current.copy(volume = VolumeControls(percent, current.volume.linkedToSystem)))
        if (current.volume.linkedToSystem) {
            try {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                val target = (percent / 100f * max).toInt().coerceIn(0, max)
                am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, target, 0)
            } catch (_: Exception) { }
        }
    }

    override fun setMono(mono: Boolean) {
        val current = _state.value
        saveState(current.copy(stereo = current.stereo.copy(mono = mono)))
    }

    override fun setReverb(params: ReverbParams) {
        val current = _state.value
        saveState(current.copy(reverb = params))
    }

    override fun setCompressor(params: CompressorParams) {
        val current = _state.value
        saveState(current.copy(compressor = params))
    }

    override fun resetControls() {
        val current = _state.value
        saveState(current.copy(
            stereo = StereoControls(),
            volume = VolumeControls(percent = 100f)
        ))
    }

    override fun resetAll() {
        saveState(EqualizerState(numBands = _state.value.numBands, bands = generateEqualizerBands(_state.value.numBands)))
    }

    override fun savePreset(name: String) {
        val state = _state.value
        presetsPrefs.edit().putString(name, serializeState(state)).apply()
    }

    override fun loadPreset(name: String): EqualizerState {
        val json = presetsPrefs.getString(name, null) ?: return EqualizerState()
        return deserializeState(json)
    }

    override fun deletePreset(name: String) {
        presetsPrefs.edit().remove(name).apply()
    }

    override fun listPresets(): List<String> {
        return presetsPrefs.all.keys.toList()
    }

    override fun setNumBands(count: Int) {
        val c = count.coerceIn(20, 25)
        val currentSettings = _settings.value
        saveSettings(currentSettings.copy(numBands = c))
        val current = _state.value
        saveState(current.copy(numBands = c, bands = generateEqualizerBands(c)))
    }

    override fun setAutoStart(enabled: Boolean) {
        val current = _settings.value
        saveSettings(current.copy(autoStart = enabled))
    }

    override fun setHapticFeedback(enabled: Boolean) {
        val current = _settings.value
        saveSettings(current.copy(hapticFeedback = enabled))
    }

    override fun setEffectsEnabled(enabled: Boolean) {
        val current = _state.value
        saveState(current.copy(effectsEnabled = enabled))
    }

    override fun setThemePalette(paletteKey: String, accentColor: Int) {
        val current = _settings.value
        saveSettings(current.copy(theme = current.theme.copy(paletteKey = paletteKey, accentColor = accentColor)))
    }

    private fun serializeState(state: EqualizerState): String {
        return buildString {
            append("numBands=${state.numBands}|")
            append("preamp=${state.preampDb}|")
            append("motor=${state.motorEnabled}|")
            append("bass=${state.tone.bassDb}|")
            append("treble=${state.tone.trebleDb}|")
            append("balance=${state.stereo.balance}|")
            append("expansion=${state.stereo.expansion}|")
            append("volume=${state.volume.percent}|")
            append("route=${state.selectedRoute}|")
            append("preset=${state.presetName}")
            append("|bands=")
            state.bands.forEachIndexed { i, band ->
                append("${band.id}:${band.gainDb}")
                if (i < state.bands.size - 1) append(",")
            }
        }
    }

    private fun deserializeState(json: String): EqualizerState {
        val parts = json.split("|").toMutableList()
        var idx = 0
        fun nextPart(): String = parts[idx++].let { it }
        fun nextFloat(): Float = nextPart().toFloatOrNull() ?: 0f
        fun nextInt(): Int = nextPart().toIntOrNull() ?: 0
        fun nextBool(): Boolean = nextPart() == "true"

        val numBands = nextInt().coerceIn(20, 25)
        val preamp = nextFloat()
        val motor = nextBool()
        val bass = nextFloat()
        val treble = nextFloat()
        val balance = nextFloat()
        val expansion = nextFloat()
        val volume = nextFloat()
        val route = nextInt()
        val preset = nextPart()

        val defaultBands = generateEqualizerBands(numBands)
        val bandsPart = if (idx < parts.size) parts[idx] else "bands="
        val bands = bandsPart.removePrefix("bands=").split(",").filter { it.isNotEmpty() }.mapIndexed { i, pair ->
            val kv = pair.split(":")
            val bandId = kv[0].toIntOrNull() ?: i
            val gain = kv[1].toFloatOrNull() ?: 0f
            val freq = defaultBands.getOrNull(i)?.frequencyHz ?: defaultBands.last().frequencyHz
            EqBand(
                id = bandId,
                frequencyHz = freq,
                gainDb = gain
            )
        }

        return EqualizerState(
            numBands = numBands,
            preampDb = preamp,
            motorEnabled = motor,
            bands = if (bands.isNotEmpty()) bands else defaultBands,
            tone = ToneControls(bassDb = bass, trebleDb = treble),
            stereo = StereoControls(balance = balance, expansion = expansion),
            volume = VolumeControls(percent = volume),
            selectedRoute = route,
            presetName = preset
        )
    }
}
