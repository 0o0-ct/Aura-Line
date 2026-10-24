package com.auraline.app.domain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.auraline.core.domain.*
import com.auraline.core.domain.AudioRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuraLineViewModel(
    private val repository: AudioRepository
) : ViewModel() {

    val state: StateFlow<EqualizerState> = repository.stateFlow
    val settings: StateFlow<AppSettings> = repository.settingsFlow

    fun toggleMotor(enabled: Boolean) {
        repository.toggleMotor(enabled)
    }

    fun setEffectsEnabled(enabled: Boolean) {
        repository.setEffectsEnabled(enabled)
    }

    fun setPreamp(db: Float) {
        repository.setPreamp(db)
    }

    fun setBandGain(bandId: Int, gainDb: Float) {
        repository.setBandGain(bandId, gainDb)
    }

    fun setTone(bassDb: Float, trebleDb: Float) {
        repository.setTone(bassDb, trebleDb)
    }

    fun setBalance(balance: Float) {
        repository.setBalance(balance)
    }

    fun setExpansion(expansion: Float) {
        repository.setExpansion(expansion)
    }

    fun setVolume(percent: Float) {
        repository.setVolume(percent)
    }

    fun setMono(mono: Boolean) {
        repository.setMono(mono)
    }

    fun setReverb(params: ReverbParams) {
        repository.setReverb(params)
    }

    fun setCompressor(params: CompressorParams) {
        repository.setCompressor(params)
    }

    fun resetAll() {
        repository.resetAll()
    }

    fun resetControls() {
        repository.resetControls()
    }

    fun savePreset(name: String) {
        repository.savePreset(name)
    }

    fun loadPreset(name: String) {
        repository.loadPreset(name)
    }

    fun deletePreset(name: String) {
        repository.deletePreset(name)
    }

    fun listPresets(): List<String> = repository.listPresets()

    fun setThemePalette(paletteKey: String, accentColor: Int) {
        viewModelScope.launch {
            repository.setThemePalette(paletteKey, accentColor)
        }
    }

    fun setNumBands(count: Int) {
        repository.setNumBands(count)
    }

    fun setAutoStart(enabled: Boolean) {
        repository.setAutoStart(enabled)
    }

    fun setHapticFeedback(enabled: Boolean) {
        repository.setHapticFeedback(enabled)
    }

    fun getSettings(): AppSettings = repository.getSettings()
    fun getState(): EqualizerState = repository.getState()
    fun saveSettings(settings: AppSettings) {
        repository.saveSettings(settings)
    }
}
