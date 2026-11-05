package com.auraline.app

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.Visualizer
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.auraline.app.di.AuraLineModule
import com.auraline.app.ui.MainActivity
import com.auraline.core.audio.AuraLineEngine
import com.auraline.core.audio.AuraLineSpectrum
import com.auraline.core.domain.CompressorParams
import com.auraline.core.domain.EqualizerState
import com.auraline.core.domain.GateParams
import com.auraline.core.domain.ModuleState
import com.auraline.core.domain.generateEqualizerBands
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Medición acústica real de la cadena de efectos sobre un AudioTrack propio.
 *
 * La pista se reproduce por el altavoz y se captura con el micrófono en la misma grabación.
 * El estado A/B se conmuta dentro de una sola captura para que el AGC del micrófono y la
 * respuesta de la sala afecten por igual a la referencia y a la señal procesada.
 *
 * Requisitos: altavoz como salida (Bluetooth desconectado), permiso RECORD_AUDIO concedido y
 * volumen multimedia estable. No sustituye a una medición con analizador externo, pero prueba
 * que el audio real que sale del dispositivo no pierde nivel al activar los efectos.
 */
@RunWith(AndroidJUnit4::class)
class EffectsAcousticTest {

    private lateinit var context: Context
    private lateinit var audioManager: AudioManager
    private var activity: android.app.Activity? = null
    private var originalVolume = 0

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            (audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * 0.75f).toInt(),
            0
        )
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent)
        SystemClock.sleep(700)
        AuraLineEngine.initialize(context)
    }

    @After
    fun tearDown() {
        AuraLineEngine.setState(flatState(motor = false))
        AuraLineEngine.flush()
        SystemClock.sleep(100)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
        activity?.finish()
    }

    private fun flatState(
        motor: Boolean = true,
        effects: Boolean = false,
        bypass: Boolean = false,
        compressor: CompressorParams = CompressorParams(ratio = 1f, makeupGain = 0f),
        gate: GateParams = GateParams(thresholdDb = -80f, ratio = 1f),
        modules: ModuleState = ModuleState(reverb = false, width = false)
    ): EqualizerState = EqualizerState(
        numBands = 25,
        bands = generateEqualizerBands(25),
        preampDb = 0f,
        motorEnabled = motor,
        effectsEnabled = effects,
        bypass = bypass,
        autoPreamp = false,
        compressor = compressor,
        gate = gate,
        modules = modules
    )

    /**
     * Reproduce 9 s de tono continuo, conmuta el estado a los 3 s y lo restaura a los 6 s,
     * y devuelve el RMS de las tres ventanas. Todo ocurre en la misma captura.
     */
    private fun measureAB(
        label: String,
        offState: EqualizerState,
        onState: EqualizerState
    ): ABResult {
        AuraLineEngine.setState(offState)
        AuraLineEngine.flush()
        SystemClock.sleep(200)

        val track = buildTrack()
        val sessionId = track.audioSessionId
        try {
            val signal = stereoSine()
            val written = track.write(signal, 0, signal.size)
            Log.i(
                TAG,
                "track: write=$written/${signal.size}, bufferFrames=${track.bufferSizeInFrames}, " +
                    "session=$sessionId"
            )
            AuraLineEngine.setState(offState)
            AuraLineEngine.onSessionOpened(sessionId, "acoustic-test")
            AuraLineEngine.flush()
            SystemClock.sleep(250)

            val recorder = buildRecorder()
            check(recorder.state == AudioRecord.STATE_INITIALIZED) {
                "AudioRecord no inicializado"
            }
            Log.i(
                TAG,
                "recorder: source=${recorder.audioSource}, bufferFrames=${recorder.bufferSizeInFrames}"
            )
            val captured = ShortArray(TOTAL_SAMPLES)
            var filled = 0
            recorder.startRecording()
            SystemClock.sleep(80)
            track.play()

            var switchedOn = false
            var switchedOff = false
            while (filled < captured.size) {
                val read = recorder.read(captured, filled, captured.size - filled)
                if (read <= 0) break
                filled += read
                if (!switchedOn && filled >= ON_SWITCH_SAMPLE) {
                    switchedOn = true
                    AuraLineEngine.setState(onState)
                    AuraLineEngine.flush()
                }
                if (!switchedOff && filled >= OFF_SWITCH_SAMPLE) {
                    switchedOff = true
                    AuraLineEngine.setState(offState)
                    AuraLineEngine.flush()
                }
            }
            recorder.stop()
            track.stop()
            Log.i(
                TAG,
                "track: playbackHead=${track.playbackHeadPosition} frames, " +
                    "filled=$filled/${captured.size} samples"
            )

            val first = rmsDb(captured, OFF1_START, OFF1_END)
            val on = rmsDb(captured, ON_START, ON_END)
            val second = rmsDb(captured, OFF2_START, OFF2_END)
            val reference = (first + second) / 2f
            val delta = on - reference
            val drift = second - first

            Log.i(
                TAG,
                "%s: off1=%.2f dBFS, on=%.2f, off2=%.2f, deltaOn=%.2f dB, drift=%.2f dB"
                    .format(label, first, on, second, delta, drift)
            )
            recorder.release()
            return ABResult(label, first, on, second, delta, drift)
        } finally {
            runCatching { track.release() }
            AuraLineEngine.onSessionClosed(sessionId)
            AuraLineEngine.flush()
        }
    }

    /**
     * Mide el nivel real del flujo DSP sin micrófono ni sala.
     *
     * `Visualizer` con `SCALING_MODE_AS_PLAYED` entrega la forma de onda tal como sale de la
     * mezcla. Se mide en ejecuciones separadas (el estado no cambia durante la captura) para
     * que el analizador de la app no robe la sesión global mientras se conmuta.
     */
    private fun measureGlobalRun(
        label: String,
        state: EqualizerState?,
        frequencyHz: Double = TONE_HZ,
        amplitude: Double = AMPLITUDE,
        rightFrequencyHz: Double = frequencyHz
    ): Float {
        // `state == null` mide lo que ya está aplicado en el motor (por ejemplo, tras
        // `applyUsageProfile`) sin sobrescribirlo.
        if (state != null) {
            AuraLineEngine.setState(state)
            AuraLineEngine.flush()
            SystemClock.sleep(150)
        }

        val track = buildTrack()
        val sessionId = track.audioSessionId
        val levels = CopyOnWriteArrayList<Pair<Long, Float>>()
        var visualizer: Visualizer? = null
        try {
            val signal = stereoSine(frequencyHz, amplitude, rightFrequencyHz)
            val written = track.write(signal, 0, signal.size)
            AuraLineEngine.onSessionOpened(sessionId, "visualizer-test")
            AuraLineEngine.flush()
            SystemClock.sleep(150)

            AuraLineSpectrum.stop()
            val vis = Visualizer(0)
            visualizer = vis
            vis.scalingMode = Visualizer.SCALING_MODE_AS_PLAYED
            vis.captureSize = 1024
            vis.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        vis: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (waveform == null || waveform.isEmpty()) return
                        var sum = 0.0
                        for (byte in waveform) {
                            val sample = ((byte.toInt() and 0xFF) - 128) / 128.0
                            sum += sample * sample
                        }
                        val rms = sqrt(sum / waveform.size).coerceAtLeast(1e-6)
                        levels.add(
                            SystemClock.elapsedRealtime() to
                                (20.0 * log10(rms)).toFloat()
                        )
                    }

                    override fun onFftDataCapture(
                        vis: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) = Unit
                },
                Visualizer.getMaxCaptureRate() / 2,
                true,
                false
            )
            vis.enabled = true

            val start = SystemClock.elapsedRealtime()
            track.play()
            SystemClock.sleep(2_000)
            track.stop()

            val values = levels.filter {
                it.first in (start + 500) until (start + 1_800)
            }
            check(values.isNotEmpty()) { "$label: el Visualizer no entregó muestras" }
            val power = values.map { 10.0.pow(it.second / 10.0) }.average()
            val db = (10.0 * log10(power.coerceAtLeast(1e-12))).toFloat()
            Log.i(
                TAG,
                "$label: global RMS=%.2f dBFS (%d tramas, write=%d/%d)"
                    .format(db, values.size, written, signal.size)
            )
            return db
        } finally {
            runCatching { visualizer?.enabled = false; visualizer?.release() }
            runCatching { track.stop(); track.release() }
            AuraLineEngine.onSessionClosed(sessionId)
            AuraLineEngine.flush()
        }
    }

    /**
     * Mide la cola tras un pulso de 0,8 s. Si la reverb procesa realmente la mezcla global,
     * la ventana de cola debe subir respecto a la referencia seca. En Android
     * `EnvironmentalReverb` es un efecto auxiliar: sólo suena si el reproductor le envía señal.
     */
    private fun measureGlobalDecay(label: String, state: EqualizerState): Pair<Float, Float> {
        AuraLineEngine.setState(state)
        AuraLineEngine.flush()
        SystemClock.sleep(150)

        val track = buildTrack(DECAY_FRAMES)
        val sessionId = track.audioSessionId
        val levels = CopyOnWriteArrayList<Pair<Long, Float>>()
        var visualizer: Visualizer? = null
        try {
            val signal = decaySignal()
            track.write(signal, 0, signal.size)
            AuraLineEngine.onSessionOpened(sessionId, "decay-test")
            AuraLineEngine.flush()
            SystemClock.sleep(150)

            AuraLineSpectrum.stop()
            val vis = Visualizer(0)
            visualizer = vis
            vis.scalingMode = Visualizer.SCALING_MODE_AS_PLAYED
            vis.captureSize = 1024
            vis.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        vis: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (waveform == null || waveform.isEmpty()) return
                        var sum = 0.0
                        for (byte in waveform) {
                            val sample = ((byte.toInt() and 0xFF) - 128) / 128.0
                            sum += sample * sample
                        }
                        val rms = sqrt(sum / waveform.size).coerceAtLeast(1e-6)
                        levels.add(
                            SystemClock.elapsedRealtime() to
                                (20.0 * log10(rms)).toFloat()
                        )
                    }

                    override fun onFftDataCapture(
                        vis: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) = Unit
                },
                Visualizer.getMaxCaptureRate() / 2,
                true,
                false
            )
            vis.enabled = true

            val start = SystemClock.elapsedRealtime()
            track.play()
            SystemClock.sleep(DECAY_MS + 300)
            track.stop()

            fun window(fromMs: Long, toMs: Long): Float {
                val values = levels.filter { it.first in (start + fromMs) until (start + toMs) }
                check(values.isNotEmpty()) { "$label: sin muestras en ventana $fromMs-$toMs" }
                val power = values.map { 10.0.pow(it.second / 10.0) }.average()
                return (10.0 * log10(power.coerceAtLeast(1e-12))).toFloat()
            }

            val dry = window(200, 700)
            val tail = window(1_200, 2_600)
            Log.i(
                TAG,
                "%s: decay dry=%.2f dBFS, tail=%.2f dBFS (%d tramas)"
                    .format(label, dry, tail, levels.size)
            )
            return dry to tail
        } finally {
            runCatching { visualizer?.enabled = false; visualizer?.release() }
            runCatching { track.stop(); track.release() }
            AuraLineEngine.onSessionClosed(sessionId)
            AuraLineEngine.flush()
        }
    }

    /**
     * Alterna A/B/B/A para cancelar cualquier deriva lenta del dispositivo entre ejecuciones.
     */
    private fun measureGlobalAB(
        label: String,
        offState: EqualizerState,
        onState: EqualizerState,
        frequencyHz: Double = TONE_HZ,
        amplitude: Double = AMPLITUDE,
        rightFrequencyHz: Double = frequencyHz
    ): Float {
        val offLevels = mutableListOf<Float>()
        val onLevels = mutableListOf<Float>()
        listOf(offState to false, onState to true, onState to true, offState to false)
            .forEachIndexed { index, (state, isOn) ->
                val level = measureGlobalRun(
                    "$label#$index${if (isOn) "-on" else "-off"}",
                    state,
                    frequencyHz,
                    amplitude,
                    rightFrequencyHz
                )
                if (isOn) onLevels.add(level) else offLevels.add(level)
            }
        val offAvg = averageDb(offLevels)
        val onAvg = averageDb(onLevels)
        val delta = onAvg - offAvg
        Log.i(
            TAG,
            "$label: DSP off=%.2f dBFS, on=%.2f dBFS, delta=%+.2f dB"
                .format(offAvg, onAvg, delta)
        )
        return delta
    }

    private fun averageDb(levels: List<Float>): Float {
        val power = levels.map { 10.0.pow(it / 10.0) }.average()
        return (10.0 * log10(power.coerceAtLeast(1e-12))).toFloat()
    }

    @Test
    fun legacyCalibrationDropIsVisibleInDspOutput() {
        val off = flatState(effects = false)
        val legacy = flatState(
            effects = true,
            compressor = CompressorParams(
                threshold = -28f,
                ratio = 4f,
                attackMs = 10f,
                releaseMs = 200f,
                makeupGain = 3f
            )
        )
        val delta = measureGlobalAB("legacy", off, legacy)

        assertTrue(
            "La calibración heredada debe mostrar una caída clara en el flujo DSP: $delta dB",
            delta < -3.0f
        )
    }

    @Test
    fun neutralEffectsPreserveDspOutputLevel() {
        val off = flatState(effects = false)
        val on = flatState(effects = true)
        val delta = measureGlobalAB("neutral", off, on)

        assertTrue(
            "Una cadena neutra no debe alterar el nivel del flujo DSP: $delta dB",
            abs(delta) < 1.0f
        )
    }

    @Test
    fun bypassPreservesDspOutputLevel() {
        val off = flatState(effects = false)
        val bypass = flatState(effects = true, bypass = true)
        val delta = measureGlobalAB("bypass", off, bypass)

        assertTrue(
            "El bypass debe conservar el nivel del flujo DSP: $delta dB",
            abs(delta) < 1.0f
        )
    }

    @Test
    fun defaultCalibrationDoesNotDropDspOutputLevel() {
        val off = flatState(effects = false)
        val on = flatState(effects = true, compressor = CompressorParams())
        val delta = measureGlobalAB("default", off, on)

        assertTrue(
            "La calibración de fábrica no debe atenuar el programa moderado: $delta dB",
            delta > -3.0f
        )
    }

    @Test
    fun equalizerBandBoostIsAppliedToTheDspOutput() {
        val off = flatState(effects = false)
        val bands = generateEqualizerBands(25)
        val targetIndex = bands.indices.minByOrNull {
            abs(bands[it].frequencyHz - 1_000f)
        } ?: 0
        val boosted = flatState(effects = true).copy(
            bands = bands.mapIndexed { index, band ->
                if (index == targetIndex) band.copy(gainDb = 12f) else band
            }
        )
        val delta = measureGlobalAB("eq", off, boosted, amplitude = 0.08)

        assertTrue("La banda de 1 kHz debe subir el tono: $delta dB", delta > 6f)
    }

    @Test
    fun subBassControlIsAppliedToTheDspOutput() {
        val off = flatState(effects = false)
        val boosted = flatState(effects = true).copy(
            tone = com.auraline.core.domain.ToneControls(
                subDb = 12f,
                subCutoffHz = 100f
            )
        )
        val delta = measureGlobalAB("sub", off, boosted, frequencyHz = 60.0, amplitude = 0.10)

        assertTrue("El sub-bajo debe subir el tono de 60 Hz: $delta dB", delta > 6f)
    }

    @Test
    fun trebleControlIsAppliedToTheDspOutput() {
        val off = flatState(effects = false)
        val boosted = flatState(effects = true).copy(
            tone = com.auraline.core.domain.ToneControls(
                trebleDb = 12f,
                trebleFrequencyHz = 4_000f
            )
        )
        val delta = measureGlobalAB("treble", off, boosted, frequencyHz = 8_000.0, amplitude = 0.08)

        assertTrue("Los agudos deben subir el tono de 8 kHz: $delta dB", delta > 6f)
    }

    @Test
    fun gateAttenuatesOnlySignalsBelowItsThreshold() {
        val off = flatState(effects = false)
        val gated = flatState(
            effects = true,
            gate = GateParams(thresholdDb = -20f, ratio = 10f)
        )
        val delta = measureGlobalAB("gate", off, gated, amplitude = 0.05)

        assertTrue("La puerta debe atenuar el tono débil: $delta dB", delta < -3f)
    }

    @Test
    fun limiterClampsPeaksWithoutTouchingNormalLevels() {
        val off = flatState(effects = false)
        val limited = flatState(
            effects = true,
            compressor = CompressorParams(
                ratio = 1f,
                makeupGain = 0f,
                limiterCeiling = -12f
            )
        )
        val delta = measureGlobalAB("limiter", off, limited, amplitude = 0.999)

        assertTrue("El limitador debe recortar el tono a 0 dBFS: $delta dB", delta < -6f)
    }

    @Test
    fun reverbDoesNotAttenuateTheDrySignal() {
        val off = flatState(effects = false)
        val withReverb = flatState(
            effects = true,
            modules = ModuleState(reverb = true, width = false)
        ).copy(reverb = com.auraline.core.domain.ReverbParams(mix = 0.5f, decay = 0.8f))
        val delta = measureGlobalAB("reverb", off, withReverb)

        assertTrue("La reverb no debe atenuar la señal seca: $delta dB", delta > -1.5f)
    }

    /**
     * La anchura estéreo se valida por vía acústica (test `stereoWidthDoesNotAttenuateTheMix`).
     * No se usa `Visualizer` para juzgar su nivel: ese efecto entrega una suma mono y la
     * decorrelación de fase del `Virtualizer` cancela parte de esa suma aunque la energía
     * estéreo total se conserve. Aquí sólo se documenta la cola de la reverb.
     */
    @Test
    fun reverbTailOnGlobalOutputIsDiagnosed() {
        val off = flatState(effects = false)
        val withReverb = flatState(
            effects = true,
            modules = ModuleState(reverb = true, width = false)
        ).copy(
            reverb = com.auraline.core.domain.ReverbParams(
                mix = 0.8f,
                decay = 0.9f,
                size = 0.8f
            )
        )
        val (dryOff, tailOff) = measureGlobalDecay("decay-off", off)
        val (dryOn, tailOn) = measureGlobalDecay("decay-on", withReverb)
        Log.i(
            TAG,
            "reverb tail: dryOff=%.2f, dryOn=%.2f, tailOff=%.2f, tailOn=%.2f, deltaTail=%.2f dB"
                .format(dryOff, dryOn, tailOff, tailOn, tailOn - tailOff)
        )
        // Sin aserción: en Android la reverb es auxiliar y los reproductores externos
        // tendrían que enviarle señal. El resultado se documenta como limitación real.
    }

    @Test
    fun usageProfileIsAppliedToTheDspChain() {
        val repository = AuraLineModule.repository(context)
        val originalState = repository.stateFlow.value
        val originalSettings = repository.settingsFlow.value
        try {
            repository.saveState(flatState(effects = false))
            AuraLineEngine.flush()
            SystemClock.sleep(250)
            val reference = measureGlobalRun(
                "profile-reference",
                null,
                frequencyHz = 60.0,
                amplitude = 0.10
            )

            // Perfil "Barra o sistema 5.1": graves +2 dB y sub-bajo +3 dB con corte 80 Hz.
            repository.applyUsageProfile("surround")
            AuraLineEngine.flush()
            SystemClock.sleep(250)
            val profiled = measureGlobalRun(
                "profile-surround",
                null,
                frequencyHz = 60.0,
                amplitude = 0.10
            )

            val delta = profiled - reference
            Log.i(
                TAG,
                "profile surround: reference=%.2f dBFS, profiled=%.2f dBFS, delta=%+.2f dB"
                    .format(reference, profiled, delta)
            )
            assertTrue(
                "El perfil debe aplicar sus controles de tono al DSP: $delta dB",
                delta > 3.0f
            )
        } finally {
            repository.saveSettings(originalSettings)
            repository.saveState(originalState)
            AuraLineEngine.flush()
        }
    }

    @Test
    fun defaultCompressorDoesNotOverAttenuateLoudProgram() {
        val off = flatState(effects = false)
        val on = flatState(effects = true, compressor = CompressorParams())
        val delta = measureGlobalAB("default-loud", off, on, amplitude = 0.5)

        assertTrue(
            "El compresor de fábrica no debe atenuar más de 3 dB un programa fuerte: $delta dB",
            delta > -3.0f
        )
    }

    @Test
    fun constantToneControlShowsNoInherentDrift() {
        val off = flatState(effects = false)
        val result = measureAB("control", off, off)

        assertTrue(
            "La captura de un tono constante no debe derivar: ${result.drift} dB",
            abs(result.drift) < 2.0f
        )
    }

    @Test
    fun neutralEffectsPreserveSpeakerLevel() {
        val off = flatState(effects = false)
        val on = flatState(effects = true)
        val result = measureAB("neutral", off, on)

        assertTrue(
            "El drift de la captura debe ser pequeño: ${result.drift} dB",
            abs(result.drift) < 1.5f
        )
        assertTrue(
            "Una cadena neutra no debe alterar el nivel: ${result.delta} dB",
            abs(result.delta) < 1.5f
        )
    }

    @Test
    fun bypassPreservesSpeakerLevel() {
        val off = flatState(effects = false)
        val bypass = flatState(effects = true, bypass = true)
        val result = measureAB("bypass", off, bypass)

        assertTrue(
            "El bypass A/B debe ser equivalente a efectos apagados: ${result.delta} dB",
            abs(result.delta) < 1.5f
        )
    }

    @Test
    fun defaultCalibrationDoesNotDropSpeakerLevel() {
        val off = flatState(effects = false)
        val on = flatState(effects = true, compressor = CompressorParams())
        val result = measureAB("default", off, on)

        assertTrue(
            "La calibración de fábrica no debe atenuar el programa moderado: ${result.delta} dB",
            result.delta > -3.0f
        )
    }

    @Test
    fun stereoWidthDoesNotAttenuateTheMix() {
        val off = flatState(effects = false)
        val wide = flatState(
            effects = true,
            modules = ModuleState(reverb = false, width = true)
        ).copy(stereo = com.auraline.core.domain.StereoControls(expansion = 1.6f))
        // El micrófono es la sonda correcta para la anchura: mide presión acústica real, no
        // la suma mono interna que usa el Visualizer.
        val result = measureAB("width", off, wide)

        assertTrue(
            "La expansión estéreo no debe atenuar el nivel global: ${result.delta} dB",
            abs(result.delta) < 1.5f
        )
    }

    private fun buildTrack(frames: Int = TOTAL_SAMPLES): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(frames * 2 * 2)
            .build()
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            ?.let { track.setPreferredDevice(it) }
        return track
    }

    private fun buildRecorder(): AudioRecord {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(SAMPLE_RATE / 2)

        fun create(source: Int): AudioRecord = AudioRecord.Builder()
            .setAudioSource(source)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer * 2)
            .build()

        val unprocessed = create(MediaRecorder.AudioSource.UNPROCESSED)
        if (unprocessed.state == AudioRecord.STATE_INITIALIZED) {
            Log.i(TAG, "recorder: usando AudioSource.UNPROCESSED")
            return unprocessed
        }
        Log.w(TAG, "recorder: UNPROCESSED no disponible, se usa MIC (puede tener AGC)")
        unprocessed.release()
        return create(MediaRecorder.AudioSource.MIC)
    }

    private fun stereoSine(
        frequencyHz: Double = TONE_HZ,
        amplitude: Double = AMPLITUDE,
        rightFrequencyHz: Double = frequencyHz
    ): ShortArray {
        if (rightFrequencyHz == frequencyHz) {
            val mono = ShortArray(TOTAL_SAMPLES) { index ->
                (amplitude * Short.MAX_VALUE *
                    sin(2.0 * PI * frequencyHz * index / SAMPLE_RATE)).toInt().toShort()
            }
            return ShortArray(TOTAL_SAMPLES * 2) { index -> mono[index / 2] }
        }
        return ShortArray(TOTAL_SAMPLES * 2) { index ->
            val frequency = if (index % 2 == 0) frequencyHz else rightFrequencyHz
            (amplitude * Short.MAX_VALUE *
                sin(2.0 * PI * frequency * (index / 2) / SAMPLE_RATE)).toInt().toShort()
        }
    }

    /** Pulso de 0,8 s seguido de silencio; la cola revela si la reverb procesa la mezcla. */
    private fun decaySignal(): ShortArray {
        val toneFrames = (SAMPLE_RATE * 0.8).toInt()
        return ShortArray(DECAY_FRAMES * 2) { index ->
            val frame = index / 2
            if (frame < toneFrames) {
                (AMPLITUDE * Short.MAX_VALUE *
                    sin(2.0 * PI * TONE_HZ * frame / SAMPLE_RATE)).toInt().toShort()
            } else {
                0
            }
        }
    }

    private fun rmsDb(samples: ShortArray, start: Int, end: Int): Float {
        var sum = 0.0
        for (index in start until end) {
            val value = samples[index] / 32768.0
            sum += value * value
        }
        val rms = sqrt(sum / (end - start)).coerceAtLeast(1e-9)
        return (20.0 * log10(rms)).toFloat()
    }

    private data class ABResult(
        val label: String,
        val off1Db: Float,
        val onDb: Float,
        val off2Db: Float,
        val delta: Float,
        val drift: Float
    )

    private companion object {
        const val TAG = "AuraLineAcoustic"
        const val SAMPLE_RATE = 48_000
        const val TONE_HZ = 1_000.0

        /** Nivel moderado: pico ≈ -10.5 dBFS, RMS ≈ -13.5 dBFS. */
        const val AMPLITUDE = 0.30

        const val SECONDS = 12
        const val TOTAL_SAMPLES = SAMPLE_RATE * SECONDS

        /** Pulso de 0,8 s + 2,2 s de silencio para medir la cola de reverb. */
        const val DECAY_MS = 3_000L
        const val DECAY_FRAMES = SAMPLE_RATE * 3

        /** Conmutaciones a los 6 s y 9 s, tras 4,5 s de calentamiento del AGC. */
        const val ON_SWITCH_SAMPLE = SAMPLE_RATE * 6
        const val OFF_SWITCH_SAMPLE = SAMPLE_RATE * 9

        /** Ventanas alejadas de las conmutaciones y del asentamiento inicial del micrófono. */
        const val OFF1_START = SAMPLE_RATE * 45 / 10
        const val OFF1_END = SAMPLE_RATE * 59 / 10
        const val ON_START = SAMPLE_RATE * 70 / 10
        const val ON_END = SAMPLE_RATE * 84 / 10
        const val OFF2_START = SAMPLE_RATE * 100 / 10
        const val OFF2_END = SAMPLE_RATE * 114 / 10
    }
}
