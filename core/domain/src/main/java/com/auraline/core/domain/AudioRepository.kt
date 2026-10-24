package com.auraline.core.domain

import kotlinx.coroutines.flow.StateFlow

interface AudioRepository {
    fun getState(): EqualizerState
    fun saveState(state: EqualizerState)
    fun getSettings(): AppSettings
    fun saveSettings(settings: AppSettings)
    val stateFlow: StateFlow<EqualizerState>
    val settingsFlow: StateFlow<AppSettings>
    fun toggleMotor(enabled: Boolean)
    fun setEffectsEnabled(enabled: Boolean)
    fun setPreamp(db: Float)
    fun setBandGain(bandId: Int, gainDb: Float)
    fun setTone(bassDb: Float, trebleDb: Float)
    fun setBalance(balance: Float)
    fun setExpansion(expansion: Float)
    fun setVolume(percent: Float)
    fun setMono(mono: Boolean)
    fun setReverb(params: ReverbParams)
    fun setCompressor(params: CompressorParams)
    fun resetAll()
    fun resetControls()
    fun savePreset(name: String)
    fun loadPreset(name: String): EqualizerState
    fun deletePreset(name: String)
    fun listPresets(): List<String>
    fun setThemePalette(paletteKey: String, accentColor: Int)
    fun setNumBands(count: Int)
    fun setAutoStart(enabled: Boolean)
    fun setHapticFeedback(enabled: Boolean)
}
