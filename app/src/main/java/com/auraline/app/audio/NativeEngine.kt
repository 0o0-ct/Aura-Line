package com.auraline.app.audio

object NativeEngine {
    init {
        System.loadLibrary("auralinedsp")
    }

    external fun init(sampleRate: Int)
    external fun setEqBand(bandIndex: Int, freq: Float, gain: Float, q: Float)
}
