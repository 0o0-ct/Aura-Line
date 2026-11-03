package com.auraline.app.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class CaptureState(
    val isCapturing: Boolean = false,
    val capturedFrames: Long = 0L,
    val errorMessage: String? = null
)

object AudioPlaybackCaptureManager {
    private const val TAG = "PlaybackCapture"
    private val scope = CoroutineScope(Dispatchers.IO)
    private var captureJob: Job? = null
    private var audioRecord: AudioRecord? = null

    private val _captureState = MutableStateFlow(CaptureState())
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startCapture(context: Context, mediaProjection: MediaProjection, sampleRate: Int = 48000): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            _captureState.value = CaptureState(errorMessage = "AudioPlaybackCapture requiere Android 10 (API 29)+")
            return false
        }

        stopCapture()

        try {
            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_FLOAT
            ).coerceAtLeast(4096)

            val record = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize * 2)
                .build()

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                _captureState.value = CaptureState(errorMessage = "AudioRecord no se pudo inicializar para captura")
                return false
            }

            record.startRecording()
            audioRecord = record
            _captureState.value = CaptureState(isCapturing = true)

            captureJob = scope.launch {
                val floatBuffer = FloatArray(1024)
                var totalFrames = 0L

                while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = record.read(floatBuffer, 0, floatBuffer.size, AudioRecord.READ_BLOCKING)
                    if (read > 0) {
                        NativeEngine.writeInputPcm(floatBuffer, 0, read)
                        totalFrames += (read / 2)
                        if (totalFrames % 48000 == 0L) {
                            _captureState.value = _captureState.value.copy(capturedFrames = totalFrames)
                        }
                    } else if (read < 0) {
                        Log.w(TAG, "AudioRecord error code: $read")
                        break
                    }
                }
            }
            return true
        } catch (t: Throwable) {
            Log.e(TAG, "Error iniciando AudioPlaybackCapture: ${t.message}")
            _captureState.value = CaptureState(errorMessage = t.message)
            stopCapture()
            return false
        }
    }

    fun stopCapture() {
        captureJob?.cancel()
        captureJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Throwable) {}
        audioRecord = null
        _captureState.value = _captureState.value.copy(isCapturing = false)
    }
}
