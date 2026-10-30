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

    /** Por debajo de este centro la banda recibe además el control de sub-bajo. */
    private const val SUB_BAND_HZ = 70f

    /** Objetivo de nivel de salida: deja margen para que el códec no sature. */
    private const val TARGET_HEADROOM_DB = 4f

    private const val MAX_HEADROOM_DB = 6f

    /** Techo del limitador de DynamicsProcessing, en dBFS. Único sitio donde se define. */
    private const val LIMITER_THRESHOLD_DB = -2f

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
    private val sessionEq = LinkedHashMap<Int, Equalizer>()

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
            if (sessionEq.containsKey(sessionId)) return@execute
            // Una pista nueva puede haber soltado la cadena global: se reaplica.
            if (processing != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                applyEngine(desired)
            }

            try {
                val eq = Equalizer(0, sessionId)
                eq.enabled = true
                sessionEq[sessionId] = eq
                applyToEqualizer(eq, desired)
                Log.i(TAG, "Sesión $sessionId enganchada")
            } catch (error: Throwable) {
                Log.w(TAG, "Sesión $sessionId rechazada: ${error.message}")
            }
            publish()
        }
    }

    fun onSessionClosed(sessionId: Int) {
        executor.execute {
            sessionEq.remove(sessionId)?.let { effect ->
                runCatching { effect.enabled = false; effect.release() }
            }
            Log.i(TAG, "Sesión $sessionId liberada")
            publish()
        }
    }

    fun shutdown() {
        executor.execute {
            releaseChain()
            sessionEq.values.forEach { runCatching { it.release() } }
            sessionEq.clear()
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
        sessionEq.values.forEach { applyToEqualizer(it, state) }
    }

    private fun createDynamicsProcessing(state: EqualizerState): DynamicsProcessing {
        val bandCount = state.numBands.coerceIn(20, 25)
        val frequencies = bandFrequencies(state, bandCount)

        val preEq = DynamicsProcessing.Eq(true, true, bandCount)
        for (band in 0 until bandCount) {
            preEq.setBand(band, DynamicsProcessing.EqBand(true, frequencies[band], 0f))
        }

        val mbc = DynamicsProcessing.Mbc(true, true, MBC_BANDS)
        val crossovers = floatArrayOf(180f, 1800f, 9000f)
        for (band in 0 until MBC_BANDS) {
            mbc.setBand(band, compressorBand(crossovers[band], state))
        }

        val limiter = DynamicsProcessing.Limiter(
            state.modules.limiter,
            true, 0, 5f, 60f, 10f, LIMITER_THRESHOLD_DB, 0f
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

        return DynamicsProcessing(GLOBAL_SESSION, GLOBAL_SESSION, config.build()).apply {
            enabled = true
            attachWatchdog(this)
            Log.i(TAG, "Cadena global lista: $bandCount bandas + MBC + limitador (cortes ${frequencies.first().toInt()}–${frequencies.last().toInt()} Hz)")
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

    private fun compressorBand(cutoffHz: Float, state: EqualizerState): DynamicsProcessing.MbcBand {
        val compressor = state.compressor
        val gate = state.gate
        val active = state.effectsEnabled && state.modules.compressor && !state.bypass
        val gateActive = state.effectsEnabled && state.modules.gate && !state.bypass
        return DynamicsProcessing.MbcBand(
            active,
            cutoffHz,
            compressor.attackMs.coerceIn(1f, 100f),
            compressor.releaseMs.coerceIn(10f, 500f),
            compressor.ratio.coerceIn(1f, 20f),
            compressor.threshold.coerceIn(-60f, 0f),
            6f,
            if (gateActive) gate.thresholdDb.coerceIn(-80f, -20f) else -80f,
            if (gateActive) gate.ratio.coerceIn(1f, 10f) else 1f,
            compressor.makeupGain.coerceIn(0f, 24f) / 2f,
            0f
        )
    }

    private fun applyToDynamicsProcessing(dp: DynamicsProcessing, state: EqualizerState) {
        val bandCount = state.numBands.coerceIn(20, 25)
        val frequencies = bandFrequencies(state, bandCount)
        val bandGains = bandGains(state, bandCount)
        AuraLineSpectrum.setBandEdges(frequencies)
        val centers = bandCenters(frequencies)


        // Preamp y Headroom:
        // En lugar de recortar todo el volumen, permitimos que el limitador haga su trabajo.
        // Sólo reducimos la ganancia si hay un refuerzo masivo de más de 12dB.
        val peakBand = (0 until bandCount).maxOfOrNull { band ->
            (bandGains[band] + toneOffset(state, centers[band])).coerceAtLeast(0f)
        } ?: 0f
        val effectsActive = state.effectsEnabled && !state.bypass
        val makeup = if (effectsActive) state.compressor.makeupGain.coerceAtLeast(0f) else 0f
        val totalBoost = peakBand + makeup
        
        val headroom = if (state.autoPreamp) {
            ((totalBoost - 12f) * 0.2f).coerceIn(0f, 6f)
        } else {
            0f
        }

        val balance = state.stereo.balance / 100f
        val preamp = state.preampDb - headroom
        val leftGain = preamp + if (balance > 0f) -balance * 12f else 0f
        val rightGain = preamp + if (balance < 0f) balance * 12f else 0f
        
        // Aplicamos la ganancia global de forma segura
        runCatching {
            dp.setInputGainAllChannelsTo(preamp)
            dp.setInputGainbyChannel(0, leftGain)
            dp.setInputGainbyChannel(1, rightGain)
        }

        val eqActive = !state.bypass && state.modules.equalizer
        for (band in 0 until bandCount) {
            val gain = if (eqActive) {
                (bandGains[band] + toneOffset(state, centers[band])).coerceIn(-24f, 24f)
            } else {
                0f
            }
            dp.setPreEqBandAllChannelsTo(
                band,
                DynamicsProcessing.EqBand(true, frequencies[band], gain)
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
            val cutoff = floatArrayOf(180f, 1800f, 9000f)[band]
            dp.setMbcBandAllChannelsTo(band, compressorBand(cutoff, state))
        }
        dp.setLimiterAllChannelsTo(
            DynamicsProcessing.Limiter(state.modules.limiter && !state.bypass, true, 0, 5f, 60f, 10f, LIMITER_THRESHOLD_DB, 0f)
        )
    }

    private fun applyToEqualizer(eq: Equalizer, state: EqualizerState) {
        val deviceBands = eq.numberOfBands
        if (deviceBands <= 0) return
        val frequencies = bandFrequencies(state, state.numBands.coerceIn(20, 25))
        val gains = bandGains(state, state.numBands.coerceIn(20, 25))
        val min = try { eq.bandLevelRange[0] } catch (_: Throwable) { -1500.toShort() }
        val max = try { eq.bandLevelRange[1] } catch (_: Throwable) { 1500.toShort() }

        for (band in 0 until deviceBands) {
            val centerHz = try { eq.getCenterFreq(band.toShort()) / 1000f } catch (_: Throwable) { 1000f }
            val nearest = frequencies.indices.minByOrNull { kotlin.math.abs(frequencies[it] - centerHz) }
            val base = if (nearest != null) gains[nearest] else 0f
            val shelf = toneOffset(state, centerHz)
            val millibels = if (state.bypass) {
                (state.preampDb * 100f).toInt()
            } else {
                ((base + shelf + state.preampDb) * 100f).toInt()
            }
                .coerceIn(min.toInt(), max.toInt())
            runCatching { eq.setBandLevel(band.toShort(), millibels.toShort()) }
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
    private fun toneOffset(state: EqualizerState, centerHz: Float): Float = when {
        // La banda más grave suma graves + sub-bajo: es la única que cubre 0-100 Hz.
        centerHz <= SUB_BAND_HZ -> state.tone.subDb + state.tone.bassDb
        centerHz <= state.tone.bassFrequencyHz -> state.tone.bassDb
        centerHz >= state.tone.trebleFrequencyHz -> state.tone.trebleDb
        else -> state.tone.midDb
    }

    private fun bandGains(state: EqualizerState, count: Int): FloatArray {
        return FloatArray(count) { index -> state.bands.getOrNull(index)?.gainDb ?: 0f }
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
                runCatching { applyEngine(desired) }
            }
        }
    }

    private fun publish() {
        val context = appContext ?: return
        val manager = runCatching {
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        }.getOrNull()

        val output = manager?.let { pickOutput(it) }
        val route = output?.let { routeOf(it) } ?: AudioRoute.UNKNOWN
        val sampleRate = manager?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull() ?: 0

        val stages = listOf(
            EffectStage(
                "Ecualizador paramétrico",
                processing != null || fallbackEq != null,
                if (processing != null) "${desired.numBands.coerceIn(20, 25)} bandas · DP" else "5 bandas · EQ del dispositivo"
            ),
            EffectStage("Compresor multibanda", processing != null && desired.effectsEnabled, "3 bandas"),
            EffectStage("Limitador", processing != null, "${LIMITER_THRESHOLD_DB.toInt()} dBFS"),
            EffectStage(
                "Graves / Agudos",
                processing != null || fallbackEq != null,
                "shelves ${desired.tone.bassFrequencyHz.toInt()} / ${desired.tone.trebleFrequencyHz.toInt()} Hz"
            ),
            EffectStage("Anchura estéreo", virtualizer != null, "Virtualizer"),
            EffectStage("Reverb", reverb != null, "EnvironmentalReverb")
        )

        val sessions = buildList {
            if (processing != null || fallbackEq != null) {
                add(AudioSession(GLOBAL_SESSION, "Mezcla del sistema", true))
            }
            sessionEq.keys.forEach { add(AudioSession(it, "Reproductor", false)) }
        }

        _status.value = EngineStatus(
            running = desired.motorEnabled && (processing != null || fallbackEq != null),
            globalScope = processing != null || fallbackEq != null,
            route = route,
            sampleRateHz = sampleRate,
            outputName = output?.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            channels = output?.channelCounts?.maxOrNull() ?: 2,
            stages = stages,
            sessions = sessions,
            lastError = lastError
        )
    }

    /** Dispositivo que está sonando ahora mismo, con lo que el sistema permite ver. */
    private fun pickOutput(manager: AudioManager): AudioDeviceInfo? {
        val devices = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val priority = listOf(
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        )
        return priority.firstNotNullOfOrNull { type -> devices.firstOrNull { it.type == type } }
    }

    private fun routeOf(device: AudioDeviceInfo): AudioRoute = when (device.type) {
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> AudioRoute.USB
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> AudioRoute.BLUETOOTH
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> AudioRoute.WIRED_HEADSET
        else -> AudioRoute.SPEAKER
    }

    private fun currentRoute(manager: AudioManager): AudioRoute {
        val devices = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return when {
            devices.any { it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE } -> AudioRoute.USB
            devices.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            } -> AudioRoute.BLUETOOTH
            devices.any {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
            } -> AudioRoute.WIRED_HEADSET
            else -> AudioRoute.SPEAKER
        }
    }
}
