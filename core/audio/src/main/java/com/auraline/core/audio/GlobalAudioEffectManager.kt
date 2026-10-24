package com.auraline.core.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Build
import android.util.Log
import com.auraline.core.domain.EqualizerState

object GlobalAudioEffectManager {

    private val dpSessions = mutableMapOf<Int, DynamicsProcessing>()
    private val eqSessions = mutableMapOf<Int, Equalizer>()
    private val bassSessions = mutableMapOf<Int, BassBoost>()
    private val virtSessions = mutableMapOf<Int, Virtualizer>()

    @Volatile
    private var currentState: EqualizerState = EqualizerState()

    fun setState(state: EqualizerState) {
        currentState = state
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dpSessions.values.forEach { applyToDp(it, state) }
        }
        eqSessions.values.forEach { applyToEqualizer(it, state) }
        bassSessions.values.forEach { applyToBass(it, state) }
        virtSessions.values.forEach { applyToVirt(it, state) }
    }

    fun onSessionOpened(sessionId: Int) {
        if (sessionId == 0) return
        Log.d("AuraLine", "onSessionOpened: $sessionId")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!dpSessions.containsKey(sessionId)) {
                try {
                    val numBands = currentState.numBands.coerceIn(20, 25)
                    val bands = currentState.bands
                    val preEq = DynamicsProcessing.Eq(true, true, numBands)
                    for (b in 0 until numBands.coerceAtMost(bands.size)) {
                        val freq = bands[b].frequencyHz
                        val dpBand = DynamicsProcessing.EqBand(true, freq, 0f)
                        preEq.setBand(b, dpBand)
                    }

                    val config = DynamicsProcessing.Config.Builder(
                        DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                        2,
                        true,
                        numBands,
                        false,
                        0,
                        false,
                        0,
                        true
                    )
                    config.setPreEqByChannelIndex(0, preEq)
                    config.setPreEqByChannelIndex(1, preEq)

                    val limiter = DynamicsProcessing.Limiter(true, true, 0, 10f, 100f, 10f, -0.1f, 0f)
                    config.setLimiterByChannelIndex(0, limiter)
                    config.setLimiterByChannelIndex(1, limiter)

                    val dp = DynamicsProcessing(0, sessionId, config.build())
                    dp.enabled = true
                    dpSessions[sessionId] = dp
                    applyToDp(dp, currentState)
                    Log.d("AuraLine", "DynamicsProcessing attached to session $sessionId ($numBands custom bands)")
                } catch (e: Exception) {
                    Log.e("AuraLine", "Failed to attach DynamicsProcessing to session $sessionId: $e")
                }
            }
        }

        if (!eqSessions.containsKey(sessionId)) {
            try {
                val eq = Equalizer(0, sessionId)
                eq.enabled = true
                eqSessions[sessionId] = eq
                applyToEqualizer(eq, currentState)
                Log.d("AuraLine", "Equalizer attached to session $sessionId")
            } catch (e: Exception) {
                Log.e("AuraLine", "Failed to attach Equalizer to session $sessionId: $e")
            }
        }

        if (!bassSessions.containsKey(sessionId)) {
            try {
                val bass = BassBoost(0, sessionId)
                bass.enabled = true
                bassSessions[sessionId] = bass
                applyToBass(bass, currentState)
            } catch (e: Exception) {
                Log.e("AuraLine", "Failed to attach BassBoost to session $sessionId: $e")
            }
        }

        if (!virtSessions.containsKey(sessionId)) {
            try {
                val virt = Virtualizer(0, sessionId)
                virt.enabled = true
                virtSessions[sessionId] = virt
                applyToVirt(virt, currentState)
            } catch (e: Exception) {
                Log.e("AuraLine", "Failed to attach Virtualizer to session $sessionId: $e")
            }
        }
    }

    fun onSessionClosed(sessionId: Int) {
        Log.d("AuraLine", "onSessionClosed: $sessionId")
        dpSessions.remove(sessionId)?.let {
            try { it.enabled = false; it.release() } catch (_: Exception) { }
        }
        eqSessions.remove(sessionId)?.let {
            try { it.enabled = false; it.release() } catch (_: Exception) { }
        }
        bassSessions.remove(sessionId)?.let {
            try { it.enabled = false; it.release() } catch (_: Exception) { }
        }
        virtSessions.remove(sessionId)?.let {
            try { it.enabled = false; it.release() } catch (_: Exception) { }
        }
    }

    private fun applyToDp(dp: DynamicsProcessing, state: EqualizerState) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        try {
            if (!state.motorEnabled) {
                dp.enabled = false
                return
            }
            dp.enabled = true

            // Preamp + Balance Input Gain
            val preamp = state.preampDb * 1.5f
            val balance = state.stereo.balance / 100f
            val leftInput = preamp + (if (balance > 0f) -balance * 6f else 0f)
            val rightInput = preamp + (if (balance < 0f) balance * 6f else 0f)

            dp.setInputGainbyChannel(0, leftInput)
            dp.setInputGainbyChannel(1, rightInput)

            val numBands = state.numBands.coerceIn(20, 25)
            val bands = state.bands

            for (i in 0 until numBands.coerceAtMost(bands.size)) {
                val band = bands[i]
                val freq = band.frequencyHz

                // Graves Low-Shelf Boost/Cut (freq <= 350Hz)
                val bassShelf = if (freq <= 350f) state.tone.bassDb * 1.5f else 0f

                // Agudos High-Shelf Boost/Cut (freq >= 2500Hz)
                val trebleShelf = if (freq >= 2500f) state.tone.trebleDb * 1.5f else 0f

                val totalGainDb = (band.gainDb * 1.5f) + bassShelf + trebleShelf
                val dpBand = DynamicsProcessing.EqBand(true, freq, totalGainDb)
                dp.setPreEqBandAllChannelsTo(i, dpBand)
            }
        } catch (e: Exception) {
            Log.e("AuraLine", "applyToDp error: $e")
        }
    }

    private fun applyToEqualizer(eq: Equalizer, state: EqualizerState) {
        if (!state.motorEnabled) {
            try { eq.enabled = false } catch (_: Exception) { }
            return
        }
        try { eq.enabled = true } catch (_: Exception) { }
        val numDevBands = eq.numberOfBands
        for (i in 0 until numDevBands) {
            val centerHz = eq.getCenterFreq(i.toShort()) / 1000f
            val nearby = state.bands.filter { kotlin.math.abs(it.frequencyHz - centerHz) < centerHz * 0.6f + 50f }
            val baseGain = if (nearby.isNotEmpty()) nearby.map { it.gainDb }.average().toFloat() else 0f
            val bassShelf = if (centerHz <= 350f) state.tone.bassDb * 1.5f else 0f
            val trebleShelf = if (centerHz >= 2500f) state.tone.trebleDb * 1.5f else 0f
            val totalGain = (baseGain * 1.5f) + bassShelf + trebleShelf + (state.preampDb * 1.5f)
            val level = (totalGain * 100f).toInt().coerceIn(-1500, 1500).toShort()
            try { eq.setBandLevel(i.toShort(), level) } catch (_: Exception) { }
        }
    }

    private fun applyToBass(bass: BassBoost, state: EqualizerState) {
        if (!state.motorEnabled) {
            try { bass.enabled = false } catch (_: Exception) { }
            return
        }
        try {
            val strength = ((state.tone.bassDb + 12f) / 24f * 1000f).toInt().coerceIn(0, 1000).toShort()
            bass.setStrength(strength)
            bass.enabled = state.tone.bassDb > -11f
        } catch (_: Exception) { }
    }

    private fun applyToVirt(virt: Virtualizer, state: EqualizerState) {
        if (!state.motorEnabled) {
            try { virt.enabled = false } catch (_: Exception) { }
            return
        }
        try {
            val strength = ((state.tone.trebleDb + 12f) / 24f * 1000f).toInt().coerceIn(0, 1000).toShort()
            virt.setStrength(strength)
            virt.enabled = state.tone.trebleDb > -11f
        } catch (_: Exception) { }
    }
}
