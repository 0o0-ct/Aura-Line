package com.auraline.core.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.auraline.core.domain.AudioRoute
import com.auraline.core.domain.AudioSession
import com.auraline.core.domain.EffectStage
import com.auraline.core.domain.EngineStatus
import com.auraline.core.domain.EqualizerState
import com.auraline.core.domain.generateEqualizerBands
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Motor de audio de AuraLine.
 *
 * Un único propietario de los efectos [android.media.audiofx.AudioEffect]: construye la cadena
 * una vez, la reutiliza mientras el motor está encendido y sólo actualiza parámetros en cada
 * cambio de la UI. Todo el trabajo sobre efectos ocurre en un hilo dedicado porque las llamadas
 * al servidor de audio son IPC síncrona.
 */
object AuraLineEngine {

    private const val TAG = "AuraLine"
    private const val GLOBAL_SESSION = 0
    private const val MBC_BANDS = 3

    /** Objetivo de nivel de salida: deja margen para que el códec no sature. */
    private const val TARGET_HEADROOM_DB = 3.0f

    /** Rango máximo de atenuación de headroom para proteger salidas digitales (Bluetooth A2DP/LE). */
    private const val MAX_HEADROOM_DB = 36f

    /** Tope por banda del PreEq y por región del MBC (permite más de 24 dB sin clipping). */
    private const val MAX_BAND_GAIN_DB = 36f

    /** Separación mínima entre los cruces Graves/Medios y Medios/Agudos (Hz). */
    private const val CROSS_MIN_GAP_HZ = 800f

    /** Tercer corte del MBC: separa medios-altos de agudos para que Agudos tenga su propia banda. */
    private const val TREBLE_SPLIT_HZ = 9_000f

    /** Energía aproximada que añade la reverb al nivel medio, por unidad de mezcla. */
    private const val REVERB_ENERGY_DB = 3f

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "auraline-engine").apply { priority = Thread.NORM_PRIORITY + 1 }
    }

    private val _status = MutableStateFlow(EngineStatus())
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    private var appContext: Context? = null
    private var deviceCallback: AudioDeviceCallback? = null

    @Volatile
    private var desired: EqualizerState = EqualizerState()

    private var processing: DynamicsProcessing? = null
    private var fallbackEq: Equalizer? = null
    private var reverb: EnvironmentalReverb? = null
    private var virtualizer: Virtualizer? = null
    /** Cadena por sesión de reproductor (DP en API 28+, Equalizer por debajo). LRU: máximo 8. */
    private val sessionChain = LinkedHashMap<Int, android.media.audiofx.AudioEffect>()
    private val maxSessions = 8

    private var lastError: String? = null
    private val DEBUG_LOG_BANDS = false
    private var builtBandCount = 0
    private val scheduled = java.util.concurrent.atomic.AtomicBoolean(false)

    fun initialize(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        registerDeviceCallback(app)
        publish()
    }

    /**
     * Aplica un estado completo. Los cambios en ráfaga (arrastrar un fader) se agrupan en una
     * sola aplicación para no encolar una llamada IPC por fotograma.
     */
    fun setState(state: EqualizerState) {
        desired = state
        if (!scheduled.compareAndSet(false, true)) return
        executor.execute {
            scheduled.set(false)
            try {
                if (state.motorEnabled) applyEngine(state) else releaseChain()
            } catch (error: Throwable) {
                lastError = error.message ?: error.javaClass.simpleName
                Log.e(TAG, "No se pudo aplicar el estado: ${error.message}")
            }
            publish()
        }
    }

    /** Bloquea hasta que la cola de audio esté vacía (usado por la comprobación acústica). */
    fun flush(timeoutMs: Long = 2_000) {
        val latch = java.util.concurrent.CountDownLatch(1)
        executor.execute { latch.countDown() }
        runCatching { latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS) }
    }

    fun onSessionOpened(sessionId: Int) {
        if (sessionId == GLOBAL_SESSION) return
        executor.execute {
            if (!desired.motorEnabled) return@execute
            if (sessionChain.containsKey(sessionId)) return@execute
            // Una pista nueva puede haber soltado la cadena global: se reaplica.
            if (processing != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                applyEngine(desired)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // DynamicsProcessing por sesión: es el camino fiable en Android 9+.
                try {
                    val dp = createDynamicsProcessing(desired, sessionId)
                    applyToDynamicsProcessing(dp, desired)
                    sessionChain[sessionId] = dp
                    trimSessionCache()
                    Log.i(TAG, "Sesión $sessionId enganchada con DynamicsProcessing")
                } catch (error: Throwable) {
                    lastError = "DP sesión $sessionId: ${error.message}"
                    Log.w(TAG, lastError!!)
                }
            } else {
                try {
                    val eq = Equalizer(0, sessionId)
                    eq.enabled = true
                    applyToEqualizer(eq, desired)
                    sessionChain[sessionId] = eq
                    trimSessionCache()
                    Log.i(TAG, "Sesión $sessionId enganchada con Equalizer (API < 28)")
                } catch (error: Throwable) {
                    lastError = "Equalizer sesión $sessionId: ${error.message}"
                    Log.w(TAG, lastError!!)
                }
            }
            publish()
        }
    }

    fun onSessionClosed(sessionId: Int) {
        executor.execute {
            sessionChain.remove(sessionId)?.let { effect ->
                runCatching { effect.enabled = false; effect.release() }
            }
            Log.i(TAG, "Sesión $sessionId liberada")
            publish()
        }
    }

    fun shutdown() {
        executor.execute {
            releaseChain()
            sessionChain.values.forEach { effect -> runCatching { effect.enabled = false; effect.release() } }
            sessionChain.clear()
            publish()
        }
    }

    // --- cadena global -----------------------------------------------------

    private fun applyEngine(state: EqualizerState) {
        // A/B compara la cadena completa: con bypass no queda ni EQ ni efectos.
        if (state.bypass) {
            runCatching { reverb?.enabled = false; reverb?.release() }
            reverb = null
            runCatching { virtualizer?.enabled = false; virtualizer?.release() }
            virtualizer = null
        }

        val bandCount = state.numBands.coerceIn(20, 25)
        // El número de bandas de un DynamicsProcessing no se puede cambiar en caliente:
        // si el usuario ajusta las bandas hay que reconstruir la cadena.
        if (processing != null && builtBandCount != bandCount) {
            Log.i(TAG, "Bandas $builtBandCount → $bandCount: reconstruyendo la cadena")
            runCatching { processing?.enabled = false; processing?.release() }
            processing = null
        }

        if (processing == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            processing = runCatching { createDynamicsProcessing(state) }
                .onFailure { lastError = "DynamicsProcessing: ${it.message}" }
                .getOrNull()
            if (processing != null) builtBandCount = bandCount
        }
        if (processing == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && fallbackEq == null) {
            Log.w(TAG, "DynamicsProcessing no disponible en sesión 0; se degrada al Equalizer del sistema")
        }

        val dp = processing
        if (dp != null) {
            dp.enabled = true
            applyToDynamicsProcessing(dp, state)
            // El ecualizador de 5 bandas del dispositivo quedaría duplicado sobre el DP.
            fallbackEq?.let { runCatching { it.release() } }
            fallbackEq = null
        } else {
            if (fallbackEq == null) {
                fallbackEq = runCatching { Equalizer(GLOBAL_SESSION, GLOBAL_SESSION) }
                    .onFailure { lastError = "Equalizer: ${it.message}" }
                    .getOrNull()
            }
            fallbackEq?.let { eq ->
                eq.enabled = true
                applyToEqualizer(eq, state)
            }
        }

        if (!state.bypass && state.modules.width) applyVirtualizer(state) else {
            runCatching { virtualizer?.enabled = false; virtualizer?.release() }
            virtualizer = null
        }
        if (!state.bypass && state.modules.reverb) applyReverb(state) else {
            runCatching { reverb?.enabled = false; reverb?.release() }
            reverb = null
        }
        if (state.motorEnabled) AuraLineSpectrum.start() else AuraLineSpectrum.stop()
        sessionChain.values.forEach { effect ->
            when (effect) {
                is DynamicsProcessing -> runCatching { applyToDynamicsProcessing(effect, state) }
                    .onFailure { Log.w(TAG, "Reaplicar DP de sesión: ${it.message}") }
                is Equalizer -> runCatching { applyToEqualizer(effect, state) }
                    .onFailure { Log.w(TAG, "Reaplicar EQ de sesión: ${it.message}") }
            }
        }
    }

    /** Cache LRU: si un reproductor deja sesiones huérfanas, se liberan las más antiguas. */
    private fun trimSessionCache() {
        while (sessionChain.size > maxSessions) {
            val oldest = sessionChain.keys.firstOrNull() ?: break
            sessionChain.remove(oldest)?.let { effect ->
                runCatching { effect.enabled = false; effect.release() }
                Log.i(TAG, "Sesión $oldest desalojada por límite de cache")
            }
        }
    }

    private fun createDynamicsProcessing(state: EqualizerState, session: Int = GLOBAL_SESSION): DynamicsProcessing {
        val bandCount = state.numBands.coerceIn(20, 25)
        val frequencies = bandFrequencies(state, bandCount)

        val preEq = DynamicsProcessing.Eq(true, true, bandCount)
        for (band in 0 until bandCount) {
            preEq.setBand(band, DynamicsProcessing.EqBand(true, frequencies[band], 0f))
        }

        val mbc = DynamicsProcessing.Mbc(true, true, MBC_BANDS)
        for (band in 0 until MBC_BANDS) {
            mbc.setBand(band, compressorBand(mbcCutoffs(state)[band], state, band))
        }

        val limiter = DynamicsProcessing.Limiter(
            state.modules.limiter,
            true, 0, 5f, 60f, 10f, -2f, 0f
        )

        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2,
            true, bandCount,
            true, MBC_BANDS,
            false, 0,
            true
        )
        config.setPreEqByChannelIndex(0, preEq)
        config.setPreEqByChannelIndex(1, preEq)
        config.setMbcByChannelIndex(0, mbc)
        config.setMbcByChannelIndex(1, mbc)
        config.setLimiterByChannelIndex(0, limiter)
        config.setLimiterByChannelIndex(1, limiter)

        return DynamicsProcessing(0, session, config.build()).apply {
            enabled = true
            attachWatchdog(this)
            Log.i(TAG, "Cadena lista (sesión $session): $bandCount bandas + MBC + limitador (cortes ${frequencies.first().toInt()}–${frequencies.last().toInt()} Hz)")
        }
    }

    /**
     * Si el sistema desactiva el efecto (otra app toma el control, cambio de salida, nueva
     * pista), se vuelve a activar y se reaplican los parámetros para que el motor no se caiga.
     */
    private fun attachWatchdog(effect: DynamicsProcessing) {
        runCatching {
            effect.setEnableStatusListener { _, enabled ->
                if (!enabled && desired.motorEnabled) {
                    Log.w(TAG, "El sistema desactivó el efecto; reenganchando")
                    executor.execute {
                        runCatching {
                            effect.enabled = true
                            applyToDynamicsProcessing(effect, desired)
                        }.onFailure {
                            runCatching { processing?.release() }
                            processing = null
                            builtBandCount = 0
                            applyEngine(desired)
                        }
                    }
                }
            }
        }
    }

    /**
     * El MBC no debe comprimir ni expandir cuando los módulos están apagados: se configura en
     * ratio 1:1 con umbral/gate fuera de rango para que la banda quede pasante y el audio de
     * esa etapa sea idéntico a la entrada (bypass real). Con ratios bajos y umbrales altos el
     * propio MBC actúa como ganancia fija por región — así Graves/Medios/Agudos se suman sin
     * duplicar filtros y sin depender sólo de la rejilla del PreEq.
     */
    private fun compressorBand(cutoffHz: Float, state: EqualizerState, bandIndex: Int): DynamicsProcessing.MbcBand {
        val compressor = state.compressor
        val gate = state.gate
        val compressing = state.effectsEnabled && state.modules.compressor && !state.bypass
        val gateActive = state.effectsEnabled && state.modules.gate && !state.bypass
        val regionGain = if (state.bypass) 0f else toneRegionDb(state, bandIndex)
        return DynamicsProcessing.MbcBand(
            true,
            cutoffHz,
            if (compressing) compressor.attackMs.coerceIn(1f, 100f) else 1f,
            if (compressing) compressor.releaseMs.coerceIn(10f, 500f) else 10f,
            if (compressing) compressor.ratio.coerceIn(1f, 20f) else 1f,
            if (compressing) compressor.threshold.coerceIn(-60f, 0f) else 0f,
            if (compressing) 6f else 0f,
            if (gateActive) gate.thresholdDb.coerceIn(-80f, -20f) else -80f,
            if (gateActive) gate.ratio.coerceIn(1f, 10f) else 1f,
            (if (compressing) compressor.makeupGain.coerceIn(0f, 24f) / 2f else 0f) + regionGain,
            0f
        )
    }

    /** Ganancia de tono (Graves/Medios/Agudos) que corresponde a cada banda del MBC. */
    private fun toneRegionDb(state: EqualizerState, bandIndex: Int): Float = when (bandIndex) {
        0 -> state.tone.bassDb
        1 -> state.tone.midDb
        else -> state.tone.trebleDb
    }.coerceIn(-MAX_BAND_GAIN_DB, MAX_BAND_GAIN_DB)

    private fun applyToDynamicsProcessing(dp: DynamicsProcessing, state: EqualizerState) {
        val bandCount = state.numBands.coerceIn(20, 25)
        val frequencies = bandFrequencies(state, bandCount)
        val bandGains = bandGains(state, bandCount)
        AuraLineSpectrum.setBandEdges(frequencies)
        val centers = bandCenters(frequencies)

        // Headroom real: suma precisa de la ganancia máxima de la curva (bandas + sub-bajo),
        // el realce por región del MBC, la compensación de volumen y la energía de la reverb.
        val peakBand = (0 until bandCount).maxOfOrNull { band ->
            appliedBandGain(state, bandGains[band], centers[band]).coerceAtLeast(0f)
        } ?: 0f
        val mbcPeak = if (state.bypass) 0f
        else (0 until MBC_BANDS).maxOf { toneRegionDb(state, it).coerceAtLeast(0f) }
        val effectsActive = state.effectsEnabled && !state.bypass
        val makeup = if (effectsActive) state.compressor.makeupGain.coerceAtLeast(0f) else 0f
        val reverbEnergy = if (effectsActive) state.reverb.mix * REVERB_ENERGY_DB else 0f
        val totalBoost = peakBand + mbcPeak + makeup + reverbEnergy

        val isBluetooth = _status.value.route == AudioRoute.BLUETOOTH
        // En Bluetooth A2DP/LE Audio, los códecs (SBC/AAC/LDAC) recortan estrictamente a 0 dBFS
        // y los filtros de reconstrucción generan sobreimpulsos inter-sample (True Peak).
        // Se reserva un margen de seguridad de 2.0 dB para evitar distorsión áspera y chillona.
        val codecMargin = if (isBluetooth) 2.0f else 0.5f

        val headroom = if (state.autoPreamp) {
            if (totalBoost > TARGET_HEADROOM_DB) {
                // Compensación exacta y suave: evita clipping sin destruir el carácter tonal
                (totalBoost - TARGET_HEADROOM_DB + codecMargin).coerceIn(0f, MAX_HEADROOM_DB)
            } else {
                0f
            }
        } else {
            // Protección mínima de seguridad en Bluetooth cuando el usuario apaga autoPreamp
            if (isBluetooth && totalBoost > 14f) {
                ((totalBoost - 14f) * 0.75f).coerceIn(0f, 18f)
            } else {
                0f
            }
        }
        val maxBoost = headroom

        val balance = state.stereo.balance / 100f
        val preamp = state.preampDb - maxBoost
        val leftGain = preamp + if (balance > 0f) -balance * 12f else 0f
        val rightGain = preamp + if (balance < 0f) balance * 12f else 0f
        dp.setInputGainbyChannel(0, leftGain)
        dp.setInputGainbyChannel(1, rightGain)

        val eqActive = !state.bypass && state.modules.equalizer
        for (band in 0 until bandCount) {
            // El PreEq lleva la curva de bandas más el sub-bajo; Graves/Medios/Agudos viajan en
            // el MBC con sus propios cruces configurables (ver compressorBand), sin duplicar filtros.
            val gain = if (eqActive) {
                (bandGains[band] + subBoostAt(state, centers[band]))
                    .coerceIn(-MAX_BAND_GAIN_DB, MAX_BAND_GAIN_DB)
            } else {
                // Con el módulo EQ apagado, el control de tono sigue actuando como estanterías.
                toneOffset(state, centers[band]) + subBoostAt(state, centers[band])
            }
            dp.setPreEqBandAllChannelsTo(
                band,
                DynamicsProcessing.EqBand(true, frequencies[band], gain.coerceIn(-MAX_BAND_GAIN_DB, MAX_BAND_GAIN_DB))
            )
        }

        if (DEBUG_LOG_BANDS) {
            val active = (0 until bandCount).filter { kotlin.math.abs(bandGains[it]) > 0.5f || state.bypass }
            Log.i(
                TAG,
                "preEq: " + active.joinToString(", ") {
                    "b$it@${frequencies[it].toInt()}Hz=${"%.1f".format(bandGains[it])}dB"
                } + if (state.bypass) " (bypass)" else ""
            )
        }

        for (band in 0 until MBC_BANDS) {
            dp.setMbcBandAllChannelsTo(band, compressorBand(mbcCutoffs(state)[band], state, band))
        }

        // Limitador de seguridad Brickwall con ataque ultrarrápido (1ms) y ratio alto (50:1)
        // para impedir que transitorios de graves saturen el codificador Bluetooth
        val limiterCeiling = if (isBluetooth) {
            // Techo de seguridad a -1.5 dBFS en Bluetooth para evitar clipping en códecs SBC/AAC
            minOf(-1.5f, state.compressor.limiterCeiling.coerceIn(-12f, 0f))
        } else {
            state.compressor.limiterCeiling.coerceIn(-12f, -0.2f)
        }

        dp.setLimiterAllChannelsTo(
            DynamicsProcessing.Limiter(
                state.modules.limiter && !state.bypass,
                true,
                0,
                1.0f, // 1 ms ataque rápido para capturar picos de graves
                80.0f, // 80 ms liberación transparente sin bombeo
                50.0f, // Ratio 50:1 (Brickwall limiter efectivo)
                limiterCeiling,
                0f
            )
        )
    }

    /**
     * Cruces del MBC a tres bandas: la frontera Graves/Medios y la de Medios/Agudos siguen los
     * controles de la pantalla Tono, con una separación mínima para que nunca se crucen entre sí.
     */
    private fun mbcCutoffs(state: EqualizerState): FloatArray {
        val low = state.tone.bassFrequencyHz.coerceIn(40f, 400f)
        val high = state.tone.trebleFrequencyHz.coerceIn(2_000f, 16_000f)
            .coerceAtLeast(low + CROSS_MIN_GAP_HZ)
        return floatArrayOf(low, high, TREBLE_SPLIT_HZ)
    }

    /**
     * Gana el `Equalizer` del sistema (o de una sesión de reproductor). Android limita el nivel
     * por banda al rango que publica el propio efecto (normalmente ±15 dB): si la orden pide más,
     * se aplica el máximo real y se registra el recorte para que la UI lo anuncie. Nunca se
     * simula un refuerzo que el hardware no puede dar.
     */
    private fun applyToEqualizer(eq: Equalizer, state: EqualizerState) {
        val deviceBands = eq.numberOfBands
        if (deviceBands <= 0) return
        val frequencies = bandFrequencies(state, state.numBands.coerceIn(20, 25))
        val gains = bandGains(state, state.numBands.coerceIn(20, 25))
        val min = try { eq.bandLevelRange[0].toFloat() } catch (_: Throwable) { -1500f }
        val max = try { eq.bandLevelRange[1].toFloat() } catch (_: Throwable) { 1500f }
        // ¿La orden supera lo que este Equalizer puede dar? Se registra el recorte real.
        var clippedAt = 0f
        for (band in 0 until deviceBands) {
            val centerHz = try { eq.getCenterFreq(band.toShort()) / 1000f } catch (_: Throwable) { continue }
            val nearest = frequencies.indices.minByOrNull { abs(frequencies[it] - centerHz) }
            val base = nearest?.let { gains[it] } ?: 0f
            val ordered = regionGainForLegacyEq(state, base, centerHz) + state.preampDb
            if (ordered > max / 100f || ordered < min / 100f) clippedAt = maxOf(clippedAt, abs(ordered))
        }
        if (clippedAt > 0f) {
            lastError = "EQ del sistema recorta a ±${max / 100f} dB (se pidieron %.1f dB)".format(clippedAt)
            Log.w(TAG, lastError!!)
        }

        for (band in 0 until deviceBands) {
            val centerHz = try { eq.getCenterFreq(band.toShort()) / 1000f } catch (_: Throwable) { 1000f }
            val nearest = frequencies.indices.minByOrNull { kotlin.math.abs(frequencies[it] - centerHz) }
            val base = if (nearest != null) gains[nearest] else 0f
            // Tono + sub-bajo repartidos igual que en la cadena DynamicsProcessing.
            val millibels = if (state.bypass) {
                (state.preampDb * 100f).toInt()
            } else {
                ((regionGainForLegacyEq(state, base, centerHz) + state.preampDb) * 100f).toInt()
            }
                .coerceIn(min.toInt(), max.toInt())
            runCatching { eq.setBandLevel(band.toShort(), millibels.toShort()) }
                .onFailure { lastError = "Equalizer banda $band: ${it.message}" }
        }
    }

    private fun applyVirtualizer(state: EqualizerState) {
        val width = state.stereo.expansion.coerceIn(1f, 2f)
        val strength = ((width - 1f) * 1000f).toInt().coerceIn(0, 1000).toShort()
        if (strength == 0.toShort()) {
            virtualizer?.let { effect ->
                runCatching { effect.enabled = false; effect.release() }
            }
            virtualizer = null
            return
        }
        if (virtualizer == null) {
            virtualizer = runCatching { Virtualizer(GLOBAL_SESSION, GLOBAL_SESSION) }
                .onFailure { Log.w(TAG, "Virtualizer no disponible: ${it.message}") }
                .getOrNull()
        }
        virtualizer?.let { effect ->
            runCatching {
                effect.setStrength(strength)
                effect.enabled = true
            }
        }
    }

    private fun applyReverb(state: EqualizerState) {
        if (!state.effectsEnabled) {
            reverb?.let { effect ->
                runCatching { effect.enabled = false; effect.release() }
            }
            reverb = null
            return
        }
        if (reverb == null) {
            reverb = runCatching { EnvironmentalReverb(GLOBAL_SESSION, GLOBAL_SESSION) }
                .onFailure { Log.w(TAG, "Reverb no disponible: ${it.message}") }
                .getOrNull()
        }
        val params = state.reverb
        reverb?.let { effect ->
            runCatching {
                effect.roomLevel = (params.size * 2000f - 1000f).toInt().toShort()
                effect.decayTime = (params.decay * 9000f).toInt().coerceIn(200, 10000)
                effect.decayHFRatio = ((1f - params.damping) * 1000f).toInt().toShort()
                // Reflexiones tempranas: es lo que hace distinguible una sala de otra.
                effect.reflectionsLevel = ((params.predelayMix * 700f) - 350f).toInt().toShort()
                effect.reflectionsDelay = (params.preDelay * 150f).toInt().coerceIn(0, 300)
                effect.reverbDelay = (params.preDelay * 300f).toInt().coerceIn(0, 500)
                effect.reverbLevel = (params.mix * 2000f).toInt().toShort()
                effect.diffusion = (params.filter * 1000f).toInt().toShort()
                effect.density = 1000
                effect.enabled = true
            }
        }
    }

    private fun releaseChain() {
        AuraLineSpectrum.stop()
        runCatching { processing?.enabled = false; processing?.release() }
        processing = null
        builtBandCount = 0
        runCatching { fallbackEq?.enabled = false; fallbackEq?.release() }
        fallbackEq = null
        runCatching { reverb?.enabled = false; reverb?.release() }
        reverb = null
        runCatching { virtualizer?.enabled = false; virtualizer?.release() }
        virtualizer = null
        lastError = null
    }

    // --- helpers -----------------------------------------------------------

    private fun bandFrequencies(state: EqualizerState, count: Int): FloatArray {
        val defaults = generateEqualizerBands(count)
        return FloatArray(count) { index ->
            val band = state.bands.getOrNull(index)
            band?.frequencyHz?.takeIf { it > 0f } ?: defaults[index].frequencyHz
        }
    }

    /** Centro audible de cada banda, para decidir a qué región del tono pertenece. */
    private fun bandCenters(cutoffs: FloatArray): FloatArray =
        FloatArray(cutoffs.size) { index ->
            if (index == 0) cutoffs[0] / 2f else kotlin.math.sqrt(cutoffs[index - 1] * cutoffs[index])
        }

    /** Graves / medios / agudos: tres regiones contiguas sobre la rejilla de bandas. */
    private fun toneOffset(state: EqualizerState, centerHz: Float): Float {
        val raw = when {
            centerHz <= state.tone.bassFrequencyHz -> state.tone.bassDb
            centerHz >= state.tone.trebleFrequencyHz -> state.tone.trebleDb
            else -> state.tone.midDb
        }
        return raw.coerceIn(-MAX_BAND_GAIN_DB, MAX_BAND_GAIN_DB)
    }

    /**
     * Refuerzo extra del sub-bajo (0–100 Hz) como paso bajo propio.
     *
     * Antes el sub se sumaba sólo a la primera banda; con rejillas de menos bandas esa banda
     * llegaba hasta 250 Hz y el refuerzo se diluía en los medios. Ahora el sub afecta a todas
     * las bandas por debajo de su corte, con caída suave hacia arriba para que se note
     * exclusivamente en la zona sub-grave.
     */
    private fun subBoostAt(state: EqualizerState, centerHz: Float): Float {
        val sub = state.tone.subDb
        if (sub == 0f || centerHz > 100f) return 0f
        val cutoff = state.tone.subCutoffHz.coerceIn(20f, 100f)
        val shaped = when {
            centerHz <= cutoff -> sub
            else -> sub * (1f - (centerHz - cutoff) / (100f - cutoff))
        }.coerceAtLeast(0f).let { if (sub < 0f) -(it) else it }
        return shaped.coerceIn(-MAX_BAND_GAIN_DB, MAX_BAND_GAIN_DB)
    }

    /**
     * Curva final que ve el usuario en la pantalla de bandas: ganancia de la banda más el
     * realce regional del tono (para estimaciones como el headroom; los filtros reales aplican
     * cada parte en su propia etapa, sin duplicar estanterías).
     */
    private fun appliedBandGain(state: EqualizerState, bandGainDb: Float, centerHz: Float): Float {
        val tone = toneOffset(state, centerHz) + subBoostAt(state, centerHz)
        return if (state.bypass) 0f else (bandGainDb + tone).coerceIn(-MAX_BAND_GAIN_DB, MAX_BAND_GAIN_DB)
    }

    /**
     * Ganancia por región para el ecualizador `Equalizer` de sesiones y dispositivos antiguos.
     * Aquí no hay MBC ni shelves independientes, así que Graves/Medios/Agudos sí se suman a la
     * banda que les corresponde (interpretación documentada en la auditoría).
     */
    private fun regionGainForLegacyEq(state: EqualizerState, bandGainDb: Float, centerHz: Float): Float {
        val tone = toneOffset(state, centerHz) + subBoostAt(state, centerHz)
        return if (state.bypass) 0f else (bandGainDb + tone).coerceIn(-MAX_BAND_GAIN_DB, MAX_BAND_GAIN_DB)
    }

    private fun bandGains(state: EqualizerState, count: Int): FloatArray {
        return FloatArray(count) { index -> state.bands.getOrNull(index)?.gainDb ?: 0f }
    }

    /** El efecto existe, está habilitado y conserva el control del hilo de audio. */
    private fun effectAlive(effect: android.media.audiofx.AudioEffect?): Boolean {
        if (effect == null) return false
        val enabled = runCatching { effect.enabled }.getOrDefault(false)
        val controlled = runCatching { effect.hasControl() }.getOrDefault(true)
        return enabled && controlled
    }

    private fun publish() {
        val app = appContext
        val manager = app?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val device = manager?.let { pickOutput(it) }
        val route = manager?.let { currentRoute(it) } ?: AudioRoute.UNKNOWN
        val sampleRate = manager?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 48000

        val stages = mutableListOf<EffectStage>()
        if (processing != null && effectAlive(processing)) {
            stages.add(EffectStage("DynamicsProcessing", true, "Motor principal activo"))
            stages.add(EffectStage("PreEQ", desired.modules.equalizer && !desired.bypass, "${builtBandCount} bandas"))
            stages.add(EffectStage("MBC", desired.modules.compressor && !desired.bypass, "3 bandas"))
            stages.add(EffectStage("Limitador", desired.modules.limiter && !desired.bypass, "True-Peak Limiter"))
        } else if (fallbackEq != null && effectAlive(fallbackEq)) {
            stages.add(EffectStage("Equalizer", true, "Modo compatibilidad"))
        }

        if (reverb != null && effectAlive(reverb)) {
            stages.add(EffectStage("Reverb", true, "Reverberación activa"))
        }
        if (virtualizer != null && effectAlive(virtualizer)) {
            stages.add(EffectStage("Virtualizer", true, "Expansión estéreo"))
        }

        val sessions = sessionChain.keys.map { id ->
            AudioSession(id = id, owner = "Sesión $id", global = id == GLOBAL_SESSION)
        }

        _status.value = EngineStatus(
            running = (processing != null && effectAlive(processing)) || (fallbackEq != null && effectAlive(fallbackEq)),
            globalScope = true,
            route = route,
            sampleRateHz = sampleRate,
            outputName = device?.productName?.toString() ?: device?.address?.toString(),
            channels = 2,
            stages = stages,
            sessions = sessions,
            lastError = lastError
        )
    }

    private fun registerDeviceCallback(context: Context) {
        if (deviceCallback != null) return
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                publish()
                reapplySoon()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                publish()
                reapplySoon()
            }
        }
        deviceCallback = callback
        runCatching {
            val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            manager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        }
    }

    /** Al cambiar de salida el sistema puede reconfigurar la mezcla: se reengancha la cadena. */
    private fun reapplySoon() {
        executor.execute {
            if (desired.motorEnabled) {
                // Durante la conexión/desconexión Bluetooth o cambio de códec A2DP (SBC/AAC/LDAC),
                // la frecuencia de muestreo de la salida puede cambiar (p.ej. de 48kHz a 44.1kHz o 96kHz).
                // Si la frecuencia cambió o el efecto perdió control, reconstruimos la cadena limpia.
                val manager = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val newRate = manager?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 0
                if (newRate > 0 && _status.value.sampleRateHz > 0 && newRate != _status.value.sampleRateHz) {
                    Log.i(TAG, "Cambio de frecuencia de salida detectado (${_status.value.sampleRateHz} -> $newRate Hz). Reconstruyendo DSP.")
                    runCatching { processing?.release() }
                    processing = null
                    builtBandCount = 0
                }
                runCatching { applyEngine(desired) }
                publish()
            }
        }
    }

    private fun isBluetoothType(type: Int): Boolean {
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
               type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
               type == 26 || // AudioDeviceInfo.TYPE_BLE_HEADSET (API 31+)
               type == 27 || // AudioDeviceInfo.TYPE_BLE_SPEAKER (API 31+)
               type == 30 || // AudioDeviceInfo.TYPE_BLE_BROADCAST (API 33+)
               type == 23    // AudioDeviceInfo.TYPE_HEARING_AID (API 28+)
    }

    /** Dispositivo que está sonando ahora mismo, con soporte completo para A2DP y LE Audio. */
    private fun pickOutput(manager: AudioManager): AudioDeviceInfo? {
        val devices = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val priority = listOf(
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            26, // TYPE_BLE_HEADSET
            27, // TYPE_BLE_SPEAKER
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            23, // TYPE_HEARING_AID
            30, // TYPE_BLE_BROADCAST
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        )
        return priority.firstNotNullOfOrNull { type -> devices.firstOrNull { it.type == type } }
    }

    private fun routeOf(device: AudioDeviceInfo): AudioRoute = when {
        device.type == AudioDeviceInfo.TYPE_USB_HEADSET || device.type == AudioDeviceInfo.TYPE_USB_DEVICE -> AudioRoute.USB
        isBluetoothType(device.type) -> AudioRoute.BLUETOOTH
        device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET -> AudioRoute.WIRED_HEADSET
        else -> AudioRoute.SPEAKER
    }

    private fun currentRoute(manager: AudioManager): AudioRoute {
        val devices = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return when {
            devices.any { it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE } -> AudioRoute.USB
            devices.any { isBluetoothType(it.type) } -> AudioRoute.BLUETOOTH
            devices.any {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
            } -> AudioRoute.WIRED_HEADSET
            else -> AudioRoute.SPEAKER
        }
    }
}
