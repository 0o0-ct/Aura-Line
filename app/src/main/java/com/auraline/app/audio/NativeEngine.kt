package com.auraline.app.audio

data class NativeTelemetry(
    val rmsL: Float = 0f,
    val rmsR: Float = 0f,
    val peakL: Float = 0f,
    val peakR: Float = 0f,
    val clippingCount: Int = 0,
    val processedBlocks: Long = 0L
)

object NativeEngine {
    private var isLoaded = false

    init {
        try {
            System.loadLibrary("auralinedsp")
            isLoaded = true
        } catch (e: Throwable) {
            android.util.Log.e("NativeEngine", "No se pudo cargar libauralinedsp: ${e.message}")
            isLoaded = false
        }
    }

    fun isAvailable(): Boolean = isLoaded

    external fun init(sampleRate: Int)
    external fun setBypass(bypass: Boolean)
    external fun setGeneratorMode(mode: Int) // 0=off, 1=pink, 2=white, 3=sweep
    external fun setGeneratorLevel(level: Float) // 0.0 .. 1.0
    external fun setPreampGain(gainDb: Float)
    external fun setEqBand(bandIndex: Int, freq: Float, gain: Float, q: Float)
    external fun setSubsonic(enabled: Boolean, cutoffHz: Float)
    external fun setInstrumentEnhancer(enabled: Boolean, fundamentalHz: Float, bodyDb: Float, punchDb: Float, mudCutDb: Float, harmonics: Float)
    external fun setCompressor(enabled: Boolean, thresholdDb: Float, ratio: Float, attackMs: Float, releaseMs: Float, makeupGainDb: Float)
    external fun setNoiseGate(enabled: Boolean, thresholdDb: Float, ratio: Float)
    external fun setReverb(enabled: Boolean, roomSize: Float, damping: Float, decay: Float, predelay: Float, mix: Float)
    external fun setStereoWidener(enabled: Boolean, width: Float, bassMonoCutoffHz: Float)
    external fun setLimiter(enabled: Boolean, ceilingDb: Float)
    external fun setBluetoothRoute(isBluetooth: Boolean)
    external fun writeInputPcm(samples: FloatArray, offset: Int, length: Int): Int
    external fun getTelemetry(outTelemetry: FloatArray)

    private val telemetryBuffer = FloatArray(6)

    fun readTelemetry(): NativeTelemetry {
        if (!isLoaded) return NativeTelemetry()
        try {
            getTelemetry(telemetryBuffer)
            return NativeTelemetry(
                rmsL = telemetryBuffer[0],
                rmsR = telemetryBuffer[1],
                peakL = telemetryBuffer[2],
                peakR = telemetryBuffer[3],
                clippingCount = telemetryBuffer[4].toInt(),
                processedBlocks = telemetryBuffer[5].toLong()
            )
        } catch (t: Throwable) {
            return NativeTelemetry()
        }
    }
}
