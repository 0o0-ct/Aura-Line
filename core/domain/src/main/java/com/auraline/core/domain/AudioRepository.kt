package com.auraline.core.domain

import kotlinx.coroutines.flow.StateFlow

interface AudioRepository {

    val stateFlow: StateFlow<EqualizerState>
    val settingsFlow: StateFlow<AppSettings>
    val engineStatus: StateFlow<EngineStatus>

    fun getState(): EqualizerState
    fun getSettings(): AppSettings

    fun saveState(state: EqualizerState)
    fun saveSettings(settings: AppSettings)

    fun toggleMotor(enabled: Boolean)
    fun setEffectsEnabled(enabled: Boolean)
    fun setPreamp(db: Float)
    fun setBandGain(bandId: Int, gainDb: Float)
    fun setTone(bassDb: Float, trebleDb: Float)
    fun setBalance(balance: Float)
    fun setExpansion(expansion: Float)
    fun setVolume(percent: Float)
    fun setReverb(params: ReverbParams)
    fun setCompressor(params: CompressorParams)

    fun resetAll()
    fun resetControls()

    fun savePreset(name: String)
    fun applyPreset(name: String): Boolean
    fun deletePreset(name: String)
    fun listPresets(): List<String>

    fun setThemePalette(paletteKey: String, accentColor: Int)
    fun setNumBands(count: Int)
    fun setAutoStart(enabled: Boolean)
    fun setHapticFeedback(enabled: Boolean)

    /** Comprueba con el micrófono que el motor altera de verdad la señal de salida. */
    suspend fun runAudioCheck(): AudioCheckResult
}
