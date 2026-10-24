package com.auraline.app.domain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auraline.core.domain.AppSettings
import com.auraline.core.domain.AudioCheckResult
import com.auraline.core.domain.AudioRepository
import com.auraline.core.domain.CompressorParams
import com.auraline.core.domain.GateParams
import com.auraline.core.domain.ModuleState
import com.auraline.core.domain.EngineStatus
import com.auraline.core.domain.EqualizerState
import com.auraline.core.domain.ReverbParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuraLineViewModel(private val repository: AudioRepository) : ViewModel() {

    val state: StateFlow<EqualizerState> = repository.stateFlow
    val settings: StateFlow<AppSettings> = repository.settingsFlow
    val engine: StateFlow<EngineStatus> = repository.engineStatus

    private val _presets = MutableStateFlow(repository.listPresets())
    val presets: StateFlow<List<String>> = _presets.asStateFlow()

    private val _checkResult = MutableStateFlow<AudioCheckResult?>(null)
    val checkResult: StateFlow<AudioCheckResult?> = _checkResult.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    fun toggleMotor(enabled: Boolean) = repository.toggleMotor(enabled)
    fun setEffectsEnabled(enabled: Boolean) = repository.setEffectsEnabled(enabled)
    fun setPreamp(db: Float) = repository.setPreamp(db)
    fun setBandGain(bandId: Int, gainDb: Float) = repository.setBandGain(bandId, gainDb)
    fun setTone(bassDb: Float, midDb: Float, trebleDb: Float) = repository.setTone(bassDb, midDb, trebleDb)
    fun setSubBass(db: Float) = repository.setSubBass(db)
    fun setToneFrequencies(bassHz: Float, trebleHz: Float) = repository.setToneFrequencies(bassHz, trebleHz)
    fun setBypass(enabled: Boolean) = repository.setBypass(enabled)
    fun setGainRange(db: Float) = repository.setGainRange(db)
    fun applyFactoryPreset(name: String) = repository.applyFactoryPreset(name)
    fun setBalance(balance: Float) = repository.setBalance(balance)
    fun setExpansion(expansion: Float) = repository.setExpansion(expansion)
    fun setVolume(percent: Float) = repository.setVolume(percent)
    fun setReverb(params: ReverbParams) = repository.setReverb(params)
    fun applyReverbPreset(name: String) = repository.applyReverbPreset(name)
    fun setCompressor(params: CompressorParams) = repository.setCompressor(params)
    fun setGate(params: GateParams) = repository.setGate(params)
    fun setModules(modules: ModuleState) = repository.setModules(modules)
    fun applyUsageProfile(key: String) = repository.applyUsageProfile(key)

    fun setAutoPreamp(enabled: Boolean) =
        repository.saveState(repository.getState().copy(autoPreamp = enabled))
    fun resetAll() = repository.resetAll()
    fun resetControls() = repository.resetControls()

    fun setNumBands(count: Int) = repository.setNumBands(count)
    fun setHapticFeedback(enabled: Boolean) = repository.setHapticFeedback(enabled)
    fun setPalette(key: String, color: Int) = repository.setThemePalette(key, color)

    fun setProfilesByOutput(enabled: Boolean) =
        repository.saveSettings(repository.getSettings().copy(profilesByOutput = enabled))

    fun setAutoStart(enabled: Boolean) = repository.setAutoStart(enabled)

    fun setAutoCheck(enabled: Boolean) =
        repository.saveSettings(repository.getSettings().copy(autoCheck = enabled))

    fun savePreset(name: String) {
        val clean = name.trim().ifEmpty { "Preajuste" }
        repository.savePreset(clean)
        _presets.value = repository.listPresets()
    }

    fun applyPreset(name: String) = repository.applyPreset(name)

    fun deletePreset(name: String) {
        repository.deletePreset(name)
        _presets.value = repository.listPresets()
    }

    fun runAudioCheck() {
        if (_checking.value) return
        viewModelScope.launch {
            _checking.value = true
            _checkResult.value = repository.runAudioCheck()
            _checking.value = false
        }
    }

    fun dismissCheck() {
        _checkResult.value = null
    }
}
