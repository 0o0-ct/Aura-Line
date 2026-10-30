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
    fun setSubBass(db: Float)
    fun setTone(bassDb: Float, midDb: Float, trebleDb: Float)
    fun setToneFrequencies(bassHz: Float, trebleHz: Float)
    fun setBalance(balance: Float)
    fun setExpansion(expansion: Float)
    fun setVolume(percent: Float)
    fun setBypass(enabled: Boolean)
    fun setGainRange(db: Float)
    fun applyFactoryPreset(name: String): Boolean
    /**
     * Foco aditivo de una fuente grave: suma la receta de la fuente sobre la curva actual
     * sin borrarla (a diferencia de [applyFactoryPreset], que la sustituye entera).
     *
     * @param scale multiplicador de la receta (1 = la receta tal cual).
     */
    fun focusLowSource(key: String, scale: Float = 1f): Boolean
    fun setReverb(params: ReverbParams)
    fun applyReverbPreset(name: String)
    fun setCompressor(params: CompressorParams)
    fun setGate(params: GateParams)
    fun setModules(modules: ModuleState)
    fun applyUsageProfile(key: String)

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

    /**
     * Comprueba con el micrófono que una banda concreta se mueve y que las lejanas no.
     * Se mide como cociente entre dos tonos simultáneos: el procesado espectral del
     * micrófono del teléfono invalida las medidas absolutas de atenuación.
     */
    suspend fun runAudioCheck(): AudioCheckResult
}
