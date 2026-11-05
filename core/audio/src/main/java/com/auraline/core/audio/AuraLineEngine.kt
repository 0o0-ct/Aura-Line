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
import com.auraline.core.domain.AudioProcessingState
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

    /**
     * Corte superior del MBC. Debe cubrir todo el espectro audible: un corte a 9 kHz dejaba
     * fuera la última octava (9–20 kHz) y oscurecía el sonido al activar el compresor.
     */
    private const val MBC_TOP_HZ = 20_000f

    /** Rodilla del compresor. Una rodilla suave evita bombeo y cambios bruscos de nivel. */
    private const val COMPRESSOR_KNEE_DB = 6f

    /** Umbral de seguridad del modo Bluetooth cuando el usuario desactiva el preamp automático. */
    private const val BLUETOOTH_SAFE_BOOST_DB = 14f

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

    /**
     * Cuando hay una cadena por sesión controlando un reproductor, la cadena global se
     * suspende para no procesar el mismo audio dos veces (doble EQ, doble compresión y
     * caída de volumen). Se reactiva al cerrar la última sesión controlada.
     */
    private var globalSuppressed = false

    /** Prioridad elevada de control para que AuraLine tenga soberanía sobre la cadena de efectos. */
    private const val EFFECT_PRIORITY = 100

    /**
     * Nodo de efectos dedicado por sesión de reproductor externo.
     * Gestiona DynamicsProcessing (PreEQ + MBC + Limiter), Reverb estéreo y Virtualizer independientes.
     */
    private class SessionNode(
        val sessionId: Int,
        var packageName: String?,
        var dynamicsProcessing: DynamicsProcessing? = null,
        var fallbackEq: Equalizer? = null,
        var reverb: EnvironmentalReverb? = null,
        var virtualizer: Virtualizer? = null,
        var hasControl: Boolean = true,
        var errorMessage: String? = null
    ) {
        fun release() {
            runCatching { dynamicsProcessing?.enabled = false; dynamicsProcessing?.release() }
            dynamicsProcessing = null
            runCatching { fallbackEq?.enabled = false; fallbackEq?.release() }
            fallbackEq = null
            runCatching { reverb?.enabled = false; reverb?.release() }
            reverb = null
            runCatching { virtualizer?.enabled = false; virtualizer?.release() }
            virtualizer = null
        }
    }

    /** Registro de sesiones externas activas (Spotify, Poweramp, etc.). LRU: máximo 8. */
    private val sessionMap = LinkedHashMap<Int, SessionNode>()
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

    fun onSessionOpened(sessionId: Int, packageName: String? = null) {
        if (sessionId == GLOBAL_SESSION) return
        executor.execute {
            if (!desired.motorEnabled) return@execute
            val node = sessionMap.getOrPut(sessionId) { SessionNode(sessionId, packageName) }
            if (packageName != null) {
                node.packageName = packageName
            }

            // Una pista nueva puede haber soltado la cadena global: se reaplica.
            if (processing != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                applyEngine(desired)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // DynamicsProcessing por sesión: es el camino fiable en Android 9+.
                try {
                    if (node.dynamicsProcessing == null) {
                        node.dynamicsProcessing = createDynamicsProcessing(desired, sessionId)
                    }
                    node.dynamicsProcessing?.let { dp ->
                        dp.enabled = true
                        applyToDynamicsProcessing(dp, desired)
                        node.hasControl = effectAlive(dp)
                    }
                    Log.i(TAG, "Sesión $sessionId ($packageName) enganchada con DynamicsProcessing (prioridad $EFFECT_PRIORITY)")
                } catch (error: Throwable) {
                    node.errorMessage = "DP sesión $sessionId: ${error.message}"
                    Log.w(TAG, node.errorMessage!!)
                }
            } else {
                try {
                    if (node.fallbackEq == null) {
                        node.fallbackEq = Equalizer(EFFECT_PRIORITY, sessionId)
                    }
                    node.fallbackEq?.let { eq ->
                        eq.enabled = true
                        applyToEqualizer(eq, desired)
                        node.hasControl = effectAlive(eq)
                    }
                    Log.i(TAG, "Sesión $sessionId ($packageName) enganchada con Equalizer")
                } catch (error: Throwable) {
                    node.errorMessage = "Equalizer sesión $sessionId: ${error.message}"
                    Log.w(TAG, node.errorMessage!!)
                }
            }

            updateSessionAuxEffects(node, desired)
            // La caché LRU puede desalojar la única sesión controlada: primero se recorta y
            // después se decide si la cadena global debe seguir suspendida.
            trimSessionCache()
            syncGlobalSuppression()
            publish()
        }
    }

    fun onSessionClosed(sessionId: Int) {
        executor.execute {
            sessionMap.remove(sessionId)?.release()
            syncGlobalSuppression()
            Log.i(TAG, "Sesión $sessionId liberada")
            publish()
        }
    }

    fun shutdown() {
        executor.execute {
            releaseChain()
            sessionMap.values.forEach { it.release() }
            sessionMap.clear()
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
            dp.enabled = !globalSuppressed
            if (!globalSuppressed) applyToDynamicsProcessing(dp, state)
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
                eq.enabled = !globalSuppressed
                if (!globalSuppressed) applyToEqualizer(eq, state)
            }
        }

        if (globalSuppressed) {
            // La cadena por sesión ya procesa este audio; la global queda suspendida.
        } else if (state.effectsEnabled && !state.bypass && state.modules.width) {
            applyVirtualizer(state)
        } else {
            runCatching { virtualizer?.enabled = false; virtualizer?.release() }
            virtualizer = null
        }
        if (globalSuppressed) {
            // Igual que arriba: evita reverb global duplicada sobre la sesión del reproductor.
        } else if (!state.bypass && state.modules.reverb) {
            applyReverb(state)
        } else {
            runCatching { reverb?.enabled = false; reverb?.release() }
            reverb = null
        }
        if (state.motorEnabled) AuraLineSpectrum.start() else AuraLineSpectrum.stop()

        // Sincronizar de forma atómica a todas las sesiones de reproductores activos
        sessionMap.values.forEach { node ->
            node.dynamicsProcessing?.let { dp ->
                runCatching {
                    dp.enabled = true
                    applyToDynamicsProcessing(dp, state)
                    node.hasControl = effectAlive(dp)
                }.onFailure { Log.w(TAG, "Reaplicar DP en sesión ${node.sessionId}: ${it.message}") }
            }
            node.fallbackEq?.let { eq ->
                runCatching {
                    eq.enabled = true
                    applyToEqualizer(eq, state)
                    node.hasControl = effectAlive(eq)
                }.onFailure { Log.w(TAG, "Reaplicar EQ en sesión ${node.sessionId}: ${it.message}") }
            }
            updateSessionAuxEffects(node, state)
        }
        syncGlobalSuppression()
    }

    /**
     * Mantiene una sola cadena activa por audio: si una sesión de reproductor tiene una cadena
     * propia bajo control, la global (sesión 0) se apaga; al cerrar la última sesión, vuelve.
     */
    private fun syncGlobalSuppression() {
        val shouldSuppress = sessionMap.values.any { node ->
            node.hasControl && (node.dynamicsProcessing?.enabled == true || node.fallbackEq?.enabled == true)
        }
        if (shouldSuppress == globalSuppressed) return
        globalSuppressed = shouldSuppress

        if (shouldSuppress) {
            runCatching { processing?.enabled = false }
            runCatching { fallbackEq?.enabled = false }
            runCatching { reverb?.enabled = false }
            runCatching { virtualizer?.enabled = false }
            Log.i(TAG, "Cadena global suspendida: sesión de reproductor controlada")
            return
        }

        processing?.let { dp ->
            runCatching {
                dp.enabled = true
                applyToDynamicsProcessing(dp, desired)
            }
        }
        fallbackEq?.let { eq ->
            runCatching {
                eq.enabled = true
                applyToEqualizer(eq, desired)
            }
        }
        if (desired.effectsEnabled && !desired.bypass) {
            if (desired.modules.reverb) applyReverb(desired)
            if (desired.modules.width) applyVirtualizer(desired)
        }
        Log.i(TAG, "Cadena global reactivada: no quedan sesiones controladas")
    }

    /**
     * Aplica o actualiza Reverb y Virtualizer directamente sobre la sesión del reproductor externo.
     * Esto garantiza que los efectos espaciales y acústicos se escuchen efectivamente en auriculares.
     */
    private fun updateSessionAuxEffects(node: SessionNode, state: EqualizerState) {
        val sessionId = node.sessionId
        val widthActive = state.effectsEnabled && !state.bypass && state.modules.width
        val reverbActive = !state.bypass && state.modules.reverb && state.effectsEnabled

        // Virtualizer en sesión del reproductor
        if (widthActive) {
            val width = state.stereo.expansion.coerceIn(1f, 2f)
            val strength = ((width - 1f) * 1000f).toInt().coerceIn(0, 1000).toShort()
            if (strength > 0) {
                if (node.virtualizer == null) {
                    node.virtualizer = runCatching { Virtualizer(EFFECT_PRIORITY, sessionId) }
                        .onFailure { Log.w(TAG, "Virtualizer en sesión $sessionId no disponible: ${it.message}") }
                        .getOrNull()
                }
                node.virtualizer?.let { virt ->
                    runCatching {
                        virt.setStrength(strength)
                        virt.enabled = true
                    }
                }
            } else {
                runCatching { node.virtualizer?.enabled = false; node.virtualizer?.release() }
                node.virtualizer = null
            }
        } else {
            runCatching { node.virtualizer?.enabled = false; node.virtualizer?.release() }
            node.virtualizer = null
        }

        // Reverb en sesión del reproductor
        if (reverbActive) {
            if (node.reverb == null) {
                node.reverb = runCatching { EnvironmentalReverb(EFFECT_PRIORITY, sessionId) }
                    .onFailure { Log.w(TAG, "Reverb en sesión $sessionId no disponible: ${it.message}") }
                    .getOrNull()
            }
            node.reverb?.let { rev ->
                applyReverbParams(rev, state.reverb)
            }
        } else {
            runCatching { node.reverb?.enabled = false; node.reverb?.release() }
            node.reverb = null
        }
    }

    /** Cache LRU: si un reproductor deja sesiones huérfanas, se liberan las más antiguas. */
    private fun trimSessionCache() {
        while (sessionMap.size > maxSessions) {
            val oldest = sessionMap.keys.firstOrNull() ?: break
            sessionMap.remove(oldest)?.release()
            Log.i(TAG, "Sesión $oldest desalojada por límite de cache")
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
        val mbcSettings = compressorBandSettings(state)
        val cutoffs = mbcCutoffs(state)
        for (band in 0 until MBC_BANDS) {
            mbc.setBand(band, compressorBand(cutoffs[band], mbcSettings))
        }

        val limiterCeiling = limiterCeilingDb(state, _status.value.route == AudioRoute.BLUETOOTH)
        val limiter = DynamicsProcessing.Limiter(
            state.modules.limiter && !state.bypass,
            true, 0, 1f, 80f, 50f, limiterCeiling, 0f
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

        val priority = if (session == GLOBAL_SESSION) 0 else EFFECT_PRIORITY
        return DynamicsProcessing(priority, session, config.build()).apply {
            enabled = true
            attachWatchdog(this)
            Log.i(TAG, "Cadena lista (sesión $session, prioridad $priority): $bandCount bandas + MBC + limitador (cortes ${frequencies.first().toInt()}–${frequencies.last().toInt()} Hz)")
        }
    }

    /**
     * Si el sistema desactiva el efecto (otra app toma el control, cambio de salida, nueva
     * pista), se vuelve a activar y se reaplican los parámetros para que el motor no se caiga.
     */
    private fun attachWatchdog(effect: DynamicsProcessing) {
        runCatching {
            effect.setEnableStatusListener { _, enabled ->
                if (!enabled && desired.motorEnabled && !globalSuppressed) {
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
     * Parámetros del MBC que no dependen de la frecuencia de corte.
     *
     * El maquillaje del compresor se aplica SIEMPRE como `postGain` (después de comprimir).
     * Aplicarlo como `preGain` hacía que el compresor recibiera más nivel, aumentara la
     * reducción de ganancia y el volumen percibido cayera al activar los efectos.
     */
    internal data class MbcBandSettings(
        val attackMs: Float,
        val releaseMs: Float,
        val ratio: Float,
        val thresholdDb: Float,
        val gateThresholdDb: Float,
        val expanderRatio: Float,
        val preGainDb: Float,
        val postGainDb: Float
    )

    internal fun compressorBandSettings(state: EqualizerState): MbcBandSettings {
        val compressing = state.effectsEnabled && state.modules.compressor && !state.bypass
        val gateActive = state.effectsEnabled && state.modules.gate && !state.bypass
        val compressor = state.compressor
        val gate = state.gate
        return MbcBandSettings(
            attackMs = if (compressing) compressor.attackMs.coerceIn(1f, 100f) else 1f,
            releaseMs = if (compressing) compressor.releaseMs.coerceIn(10f, 500f) else 10f,
            ratio = if (compressing) compressor.ratio.coerceIn(1f, 20f) else 1f,
            thresholdDb = if (compressing) compressor.threshold.coerceIn(-60f, 0f) else 0f,
            gateThresholdDb = if (gateActive) gate.thresholdDb.coerceIn(-80f, -20f) else -80f,
            expanderRatio = if (gateActive) gate.ratio.coerceIn(1f, 10f) else 1f,
            // El tono ya se aplica una sola vez en el PreEq. El MBC no debe duplicarlo.
            preGainDb = 0f,
            postGainDb = if (compressing) compressor.makeupGain.coerceIn(-12f, 24f) else 0f
        )
    }

    private fun compressorBand(cutoffHz: Float, settings: MbcBandSettings): DynamicsProcessing.MbcBand =
        DynamicsProcessing.MbcBand(
            true,
            cutoffHz,
            settings.attackMs,
            settings.releaseMs,
            settings.ratio,
            settings.thresholdDb,
            COMPRESSOR_KNEE_DB,
            settings.gateThresholdDb,
            settings.expanderRatio,
            settings.preGainDb,
            settings.postGainDb
        )

    private fun applyToDynamicsProcessing(dp: DynamicsProcessing, state: EqualizerState) {
        val bandCount = state.numBands.coerceIn(20, 25)
        val frequencies = bandFrequencies(state, bandCount)
        val bandGains = bandGains(state, bandCount)
        AuraLineSpectrum.setBandEdges(frequencies)
        val centers = bandCenters(frequencies)

        val eqActive = !state.bypass && state.modules.equalizer

        // Headroom real: solo la ganancia positiva que realmente aplica el PreEq (bandas +
        // tono + sub-bajo, una sola vez). El maquillaje del compresor es postGain y la reverb
        // es un envío auxiliar: ninguno debe atenuar la entrada; el limitador protege los picos.
        val peakBand = if (state.bypass) 0f else (0 until bandCount).maxOfOrNull { band ->
            preEqGainDb(state, bandGains[band], centers[band], eqActive).coerceAtLeast(0f)
        } ?: 0f

        val isBluetooth = _status.value.route == AudioRoute.BLUETOOTH
        val headroom = headroomDb(state, peakBand, isBluetooth)

        val balance = if (state.bypass) 0f else state.stereo.balance / 100f
        val preamp = if (state.bypass) 0f else (state.preampDb - headroom).coerceIn(-60f, 36f)
        val leftGain = preamp + if (balance > 0f) -balance * 12f else 0f
        val rightGain = preamp + if (balance < 0f) balance * 12f else 0f
        dp.setInputGainbyChannel(0, leftGain)
        dp.setInputGainbyChannel(1, rightGain)

        for (band in 0 until bandCount) {
            // El PreEq aplica directamente bandas, tono y sub-bajo. El tono no se repite en el MBC.
            dp.setPreEqBandAllChannelsTo(
                band,
                DynamicsProcessing.EqBand(
                    true,
                    frequencies[band],
                    preEqGainDb(state, bandGains[band], centers[band], eqActive)
                )
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

        val cutoffs = mbcCutoffs(state)
        val mbcSettings = compressorBandSettings(state)
        for (band in 0 until MBC_BANDS) {
            dp.setMbcBandAllChannelsTo(band, compressorBand(cutoffs[band], mbcSettings))
        }

        // Limitador de seguridad Brickwall con ataque ultrarrápido (1 ms) y ratio alto (50:1)
        // para impedir que transitorios de graves saturen el codificador Bluetooth.
        // No aplica atenuación estática: solo actúa sobre los picos que superan el techo.
        val limiterCeiling = limiterCeilingDb(state, isBluetooth)

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
     * Atenuación de entrada destinada exclusivamente a evitar clipping por realces de EQ/tono.
     *
     * No cuenta el maquillaje del compresor ni la reverb: contarlos atenuaba la señal incluso
     * con una curva plana y era una de las causas de la pérdida de volumen al activar efectos.
     * Con una curva neutral devuelve exactamente 0 dB.
     */
    internal fun headroomDb(state: EqualizerState, peakBoostDb: Float, isBluetooth: Boolean): Float {
        if (state.bypass) return 0f
        val boost = peakBoostDb.coerceAtLeast(0f)
        // En Bluetooth los códecs recortan a 0 dBFS y los filtros de reconstrucción generan
        // sobreimpulsos inter-sample; se reserva un margen adicional.
        val codecMargin = if (isBluetooth) 2.0f else 0.5f
        return if (state.autoPreamp) {
            if (boost > TARGET_HEADROOM_DB) {
                (boost - TARGET_HEADROOM_DB + codecMargin).coerceIn(0f, MAX_HEADROOM_DB)
            } else {
                0f
            }
        } else {
            if (isBluetooth && boost > BLUETOOTH_SAFE_BOOST_DB) {
                ((boost - BLUETOOTH_SAFE_BOOST_DB) * 0.75f).coerceIn(0f, MAX_HEADROOM_DB)
            } else {
                0f
            }
        }
    }

    internal fun limiterCeilingDb(state: EqualizerState, isBluetooth: Boolean): Float =
        if (isBluetooth) {
            minOf(-1.5f, state.compressor.limiterCeiling.coerceIn(-12f, 0f))
        } else {
            state.compressor.limiterCeiling.coerceIn(-12f, -0.2f)
        }

    /**
     * Cruces del MBC a tres bandas: la frontera Graves/Medios y la de Medios/Agudos siguen los
     * controles de la pantalla Tono, con una separación mínima para que nunca se crucen entre sí.
     * La banda superior termina en 20 kHz para no perder la última octava audible.
     */
    internal fun mbcCutoffs(state: EqualizerState): FloatArray {
        val low = state.tone.bassFrequencyHz.coerceIn(40f, 400f)
        val high = state.tone.trebleFrequencyHz.coerceIn(2_000f, 16_000f)
            .coerceAtLeast(low + CROSS_MIN_GAP_HZ)
            .coerceAtMost(MBC_TOP_HZ - CROSS_MIN_GAP_HZ)
        return floatArrayOf(low, high, MBC_TOP_HZ)
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
            val ordered = if (state.bypass) 0f
            else regionGainForLegacyEq(state, base, centerHz) + state.preampDb
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
                0
            } else {
                ((regionGainForLegacyEq(state, base, centerHz) + state.preampDb) * 100f).toInt()
            }
                .coerceIn(min.toInt(), max.toInt())
            runCatching { eq.setBandLevel(band.toShort(), millibels.toShort()) }
                .onFailure { lastError = "Equalizer banda $band: ${it.message}" }
        }
    }

    private fun applyVirtualizer(state: EqualizerState) {
        if (!state.effectsEnabled || state.bypass || !state.modules.width) {
            runCatching { virtualizer?.enabled = false; virtualizer?.release() }
            virtualizer = null
            return
        }
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
        reverb?.let { effect ->
            applyReverbParams(effect, state.reverb)
        }
    }

    private fun applyReverbParams(effect: EnvironmentalReverb, params: com.auraline.core.domain.ReverbParams) {
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
        // Las cadenas por sesión también pertenecen al motor: apagarlo debe silenciarlas,
        // no sólo la cadena global.
        sessionMap.values.forEach { it.release() }
        sessionMap.clear()
        globalSuppressed = false
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
        // La rampa conserva el signo: con sub negativo también debe atenuar (antes un
        // `coerceAtLeast(0f)` anulaba todos los cortes de sub-bajo).
        val shaped = when {
            centerHz <= cutoff -> sub
            else -> sub * (1f - (centerHz - cutoff) / (100f - cutoff))
        }
        return shaped.coerceIn(-MAX_BAND_GAIN_DB, MAX_BAND_GAIN_DB)
    }

    /**
     * Ganancia final de una banda del PreEq: banda + tono + sub-bajo, aplicada una sola vez.
     * Cuando el módulo de ecualización está en bypass se conservan tono y sub-bajo, que son
     * controles independientes de la curva gráfica.
     */
    private fun parametricToneGain(state: EqualizerState, centerHz: Float): Float {
        val toneBands = state.tone.bands
        if (toneBands.isEmpty()) {
            return toneOffset(state, centerHz) + subBoostAt(state, centerHz)
        }
        var toneGain = 0f
        for (tb in toneBands) {
            if (!tb.enabled) continue
            val f = tb.frequencyHz.coerceAtLeast(10f)
            val g = tb.gainDb
            val q = tb.q.coerceAtLeast(0.1f)
            when (tb.filterType) {
                "LOW_SHELF" -> {
                    if (centerHz <= f) {
                        toneGain += g
                    } else {
                        val ratio = centerHz / f
                        toneGain += g / (1f + (ratio - 1f) * (ratio - 1f) * q)
                    }
                }
                "HIGH_SHELF" -> {
                    if (centerHz >= f) {
                        toneGain += g
                    } else {
                        val ratio = f / centerHz.coerceAtLeast(10f)
                        toneGain += g / (1f + (ratio - 1f) * (ratio - 1f) * q)
                    }
                }
                else -> { // PEAKING / bell / notch
                    val octaveDist = kotlin.math.abs(kotlin.math.ln(centerHz.coerceAtLeast(10f) / f) / kotlin.math.ln(2f))
                    val weighting = 1f + (octaveDist * q * 2.0f).let { it * it }
                    toneGain += g / weighting
                }
            }
        }
        toneGain += subBoostAt(state, centerHz)
        return toneGain.coerceIn(-MAX_BAND_GAIN_DB, MAX_BAND_GAIN_DB)
    }

    /**
     * Ganancia final de una banda del PreEq: banda + tono + sub-bajo, aplicada una sola vez.
     * Cuando el módulo de ecualización está en bypass se conservan tono y sub-bajo, que son
     * controles independientes de la curva gráfica.
     */
    internal fun preEqGainDb(
        state: EqualizerState,
        bandGainDb: Float,
        centerHz: Float,
        eqActive: Boolean
    ): Float {
        if (state.bypass) return 0f
        val tone = parametricToneGain(state, centerHz)
        val base = if (eqActive) bandGainDb else 0f
        return (base + tone).coerceIn(-MAX_BAND_GAIN_DB, MAX_BAND_GAIN_DB)
    }

    /**
     * Ganancia por región para el ecualizador `Equalizer` de sesiones y dispositivos antiguos.
     * Comparte exactamente la misma curva que el PreEq para que ambos caminos suenen igual.
     */
    private fun regionGainForLegacyEq(state: EqualizerState, bandGainDb: Float, centerHz: Float): Float =
        preEqGainDb(state, bandGainDb, centerHz, eqActive = true)

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
        val hasActivePlayerSession = sessionMap.values.any { it.hasControl && (it.dynamicsProcessing?.enabled == true || it.fallbackEq?.enabled == true) }
        val globalDpActive = processing != null && effectAlive(processing)
        val globalEqActive = fallbackEq != null && effectAlive(fallbackEq)

        if (globalDpActive || hasActivePlayerSession) {
            stages.add(EffectStage("DynamicsProcessing", true, "Motor principal activo"))
            stages.add(EffectStage("PreEQ", desired.modules.equalizer && !desired.bypass, "${builtBandCount} bandas"))
            stages.add(EffectStage("MBC", desired.modules.compressor && !desired.bypass, "3 bandas"))
            stages.add(EffectStage("Limitador", desired.modules.limiter && !desired.bypass, "True-Peak Limiter"))
        } else if (globalEqActive) {
            stages.add(EffectStage("Equalizer", true, "Modo compatibilidad"))
        }

        val reverbActive = (reverb != null && effectAlive(reverb)) || sessionMap.values.any { it.reverb != null && effectAlive(it.reverb) }
        if (reverbActive) {
            stages.add(EffectStage("Reverb", true, "Reverberación activa"))
        }
        val virtualizerActive = (virtualizer != null && effectAlive(virtualizer)) || sessionMap.values.any { it.virtualizer != null && effectAlive(it.virtualizer) }
        if (virtualizerActive) {
            stages.add(EffectStage("Virtualizer", true, "Expansión estéreo"))
        }

        val sessions = sessionMap.values.map { node ->
            val ownerLabel = node.packageName?.let { pkg ->
                when {
                    pkg.contains("spotify", ignoreCase = true) -> "Spotify"
                    pkg.contains("youtube", ignoreCase = true) -> "YouTube Music"
                    pkg.contains("poweramp", ignoreCase = true) -> "Poweramp"
                    pkg.contains("deezer", ignoreCase = true) -> "Deezer"
                    pkg.contains("vlc", ignoreCase = true) -> "VLC"
                    pkg.contains("musicolet", ignoreCase = true) -> "Musicolet"
                    else -> pkg.substringAfterLast('.')
                }
            } ?: "Sesión ${node.sessionId}"
            AudioSession(id = node.sessionId, owner = ownerLabel, global = false)
        }

        // Determinar estado verídico de procesamiento para la UI
        val processingState: AudioProcessingState
        val stateDescription: String
        val activePkg = sessionMap.values.firstOrNull()?.packageName

        if (!desired.motorEnabled) {
            processingState = AudioProcessingState.AVAILABLE_NO_SESSION
            stateDescription = "El motor de audio está apagado. Enciéndelo en la barra superior."
        } else if (hasActivePlayerSession) {
            val primary = sessions.first()
            processingState = AudioProcessingState.PROCESSING_AUDIO
            stateDescription = "Procesando audio en tiempo real: ${primary.owner} (Sesión ${primary.id}) hacia ${route.label}."
        } else if (globalDpActive) {
            val isSamsung = Build.MANUFACTURER.contains("samsung", ignoreCase = true)
            if (isSamsung) {
                processingState = AudioProcessingState.AVAILABLE_NO_SESSION
                stateDescription = "Motor en sesión global. En One UI (Samsung), reproductores externos requieren activar 'Ecualizador' en sus ajustes para procesar audio en auriculares."
            } else {
                processingState = AudioProcessingState.PROCESSING_AUDIO
                stateDescription = "Procesando mezcla global del sistema hacia ${route.label}."
            }
        } else if (lastError != null) {
            processingState = AudioProcessingState.PROCESSING_ERROR
            stateDescription = "Error de procesamiento: $lastError"
        } else {
            processingState = AudioProcessingState.AVAILABLE_NO_SESSION
            stateDescription = "Motor preparado. Esperando sesión de audio de un reproductor compatible."
        }

        _status.value = EngineStatus(
            running = (processing != null && effectAlive(processing)) || (fallbackEq != null && effectAlive(fallbackEq)) || hasActivePlayerSession,
            globalScope = true,
            route = route,
            sampleRateHz = sampleRate,
            outputName = device?.productName?.toString() ?: device?.address?.toString(),
            channels = 2,
            stages = stages,
            sessions = sessions,
            processingState = processingState,
            stateDescription = stateDescription,
            activePackageName = activePkg,
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
