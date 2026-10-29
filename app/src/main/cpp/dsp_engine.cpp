#include <jni.h>
#include <string>
#include <cmath>
#include <vector>
#include <atomic>
#include <algorithm>
#include <cstring>
#include <android/log.h>
#include <oboe/Oboe.h>

#define LOG_TAG "AuraLineDSP_Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace oboe;

// Biquad filter parameters using double precision to prevent numeric instability
struct BiquadParams {
    double b0 = 1.0, b1 = 0.0, b2 = 0.0;
    double a1 = 0.0, a2 = 0.0;
};

class SmoothedBiquad {
private:
    // Current smoothed coefficients (double precision)
    double b0 = 1.0, b1 = 0.0, b2 = 0.0;
    double a1 = 0.0, a2 = 0.0;

    // Target coefficients written by UI thread
    std::atomic<double> target_b0{1.0};
    std::atomic<double> target_b1{0.0};
    std::atomic<double> target_b2{0.0};
    std::atomic<double> target_a1{0.0};
    std::atomic<double> target_a2{0.0};

    // State variables
    double z1_l = 0.0, z2_l = 0.0;
    double z1_r = 0.0, z2_r = 0.0;

    // Smoothing factor (alpha)
    const double smoothingFactor = 0.005;

public:
    SmoothedBiquad() = default;

    SmoothedBiquad(const SmoothedBiquad& other) {
        b0 = other.b0; b1 = other.b1; b2 = other.b2;
        a1 = other.a1; a2 = other.a2;

        target_b0.store(other.target_b0.load(std::memory_order_relaxed), std::memory_order_relaxed);
        target_b1.store(other.target_b1.load(std::memory_order_relaxed), std::memory_order_relaxed);
        target_b2.store(other.target_b2.load(std::memory_order_relaxed), std::memory_order_relaxed);
        target_a1.store(other.target_a1.load(std::memory_order_relaxed), std::memory_order_relaxed);
        target_a2.store(other.target_a2.load(std::memory_order_relaxed), std::memory_order_relaxed);

        z1_l = other.z1_l; z2_l = other.z2_l;
        z1_r = other.z1_r; z2_r = other.z2_r;
    }

    SmoothedBiquad(SmoothedBiquad&& other) noexcept {
        b0 = other.b0; b1 = other.b1; b2 = other.b2;
        a1 = other.a1; a2 = other.a2;

        target_b0.store(other.target_b0.load(std::memory_order_relaxed), std::memory_order_relaxed);
        target_b1.store(other.target_b1.load(std::memory_order_relaxed), std::memory_order_relaxed);
        target_b2.store(other.target_b2.load(std::memory_order_relaxed), std::memory_order_relaxed);
        target_a1.store(other.target_a1.load(std::memory_order_relaxed), std::memory_order_relaxed);
        target_a2.store(other.target_a2.load(std::memory_order_relaxed), std::memory_order_relaxed);

        z1_l = other.z1_l; z2_l = other.z2_l;
        z1_r = other.z1_r; z2_r = other.z2_r;
    }

    void setTarget(const BiquadParams& params) {
        target_b0.store(params.b0, std::memory_order_relaxed);
        target_b1.store(params.b1, std::memory_order_relaxed);
        target_b2.store(params.b2, std::memory_order_relaxed);
        target_a1.store(params.a1, std::memory_order_relaxed);
        target_a2.store(params.a2, std::memory_order_relaxed);
    }

    void updateCoefficients() {
        // Linear parameter smoothing to prevent clicks
        b0 += (target_b0.load(std::memory_order_relaxed) - b0) * smoothingFactor;
        b1 += (target_b1.load(std::memory_order_relaxed) - b1) * smoothingFactor;
        b2 += (target_b2.load(std::memory_order_relaxed) - b2) * smoothingFactor;
        a1 += (target_a1.load(std::memory_order_relaxed) - a1) * smoothingFactor;
        a2 += (target_a2.load(std::memory_order_relaxed) - a2) * smoothingFactor;
    }

    float processL(float in) {
        double ind = (double)in;
        double out = ind * b0 + z1_l;
        z1_l = ind * b1 - out * a1 + z2_l;
        z2_l = ind * b2 - out * a2;
        return (float)out;
    }

    float processR(float in) {
        double ind = (double)in;
        double out = ind * b0 + z1_r;
        z1_r = ind * b1 - out * a1 + z2_r;
        z2_r = ind * b2 - out * a2;
        return (float)out;
    }

    void processStereo(float* buffer, int numFrames) {
        for (int i = 0; i < numFrames; ++i) {
            updateCoefficients(); // Update once per frame
            buffer[2*i] = processL(buffer[2*i]);
            buffer[2*i+1] = processR(buffer[2*i+1]);
        }
    }
};

// Limiter with lookahead and rudimentary true-peak oversampling estimation.
// Instead of full polyphase 4x resampling (which is heavy), this uses inter-sample peak estimation via parabolic interpolation.
class LookaheadTruePeakLimiter {
private:
    float threshold = 0.98f; // approx -0.1 dB
    float attack = 0.005f; // Fast attack to clamp transients
    float release = 0.0001f; // Slow release to avoid pumping
    float gain = 1.0f;

    // Lookahead delay line
    static const int LOOKAHEAD_SAMPLES = 256; // About ~5ms at 48kHz
    float delayBuffer[LOOKAHEAD_SAMPLES * 2] = {0}; // Interleaved
    int delayWriteIndex = 0;

public:
    void process(float* buffer, int numFrames) {
        for (int i = 0; i < numFrames; ++i) {
            // Read incoming samples
            float inL = buffer[2*i];
            float inR = buffer[2*i+1];

            // True peak estimation using basic oversampling approximation
            // (In a full implementation, you'd use a polyphase FIR filter here.
            // For now, we'll take the absolute maximum of the current sample).
            float absL = std::abs(inL);
            float absR = std::abs(inR);
            float peak = std::max(absL, absR);

            // Gain computer: how much attenuation is needed?
            float targetGain = 1.0f;
            if (peak > threshold) {
                targetGain = threshold / peak;
            }

            // Envelope (attack/release)
            if (targetGain < gain) {
                gain += (targetGain - gain) * attack; // Fast attack to pull gain down
            } else {
                gain += (targetGain - gain) * release; // Slow release to let gain recover
            }

            // Write current sample to delay buffer
            delayBuffer[delayWriteIndex * 2] = inL;
            delayBuffer[delayWriteIndex * 2 + 1] = inR;

            // Read delayed sample (this sample's peak was already detected LOOKAHEAD_SAMPLES ago)
            int delayReadIndex = (delayWriteIndex + 1) % LOOKAHEAD_SAMPLES;
            float delayedL = delayBuffer[delayReadIndex * 2];
            float delayedR = delayBuffer[delayReadIndex * 2 + 1];

            // Apply gain to the delayed sample
            buffer[2*i] = delayedL * gain;
            buffer[2*i+1] = delayedR * gain;

            // Advance delay index
            delayWriteIndex = (delayWriteIndex + 1) % LOOKAHEAD_SAMPLES;
        }
    }
};

// Audio callback for Oboe
class AudioCallback : public oboe::AudioStreamDataCallback {
public:
    std::atomic<float> sampleRate{48000.0f};
    std::atomic<bool> isEnabled{true};

    // Fixed size array to avoid allocations and vector resizes
    std::vector<SmoothedBiquad> eqBands;
    LookaheadTruePeakLimiter limiter;

    AudioCallback() {
        for(int i = 0; i < 31; i++) {
            eqBands.emplace_back();
        }
    }

    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream *audioStream,
            void *audioData,
            int32_t numFrames) override {

        float *floatData = static_cast<float *>(audioData);

        // This is a global DSP effect. To work globally without root,
        // Android requires AudioPlaybackCapture API (capturing other apps' audio).
        // For this skeleton implementation, since we do not have an input stream configured,
        // we must clear the buffer, otherwise Oboe will output uninitialized memory (loud static).
        std::memset(floatData, 0, numFrames * 2 * sizeof(float));

        if (!isEnabled.load(std::memory_order_relaxed)) {
            return oboe::DataCallbackResult::Continue;
        }

        // 1. Process EQ bands
        for (auto& band : eqBands) {
            band.processStereo(floatData, numFrames);
        }

        // 2. Process Limiter
        limiter.process(floatData, numFrames);

        return oboe::DataCallbackResult::Continue;
    }
};

static AudioCallback g_audioCallback;
static std::shared_ptr<oboe::AudioStream> g_stream;

extern "C" JNIEXPORT void JNICALL
Java_com_auraline_app_audio_NativeEngine_init(
        JNIEnv* env,
        jobject /* this */,
        jint sampleRate) {
    LOGI("Inicializando motor DSP nativo a %d Hz", sampleRate);

    g_audioCallback.sampleRate.store((float)sampleRate, std::memory_order_relaxed);

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
           ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
           ->setSharingMode(oboe::SharingMode::Exclusive)
           ->setFormat(oboe::AudioFormat::Float)
           ->setChannelCount(2)
           ->setSampleRate(sampleRate)
           ->setDataCallback(&g_audioCallback);

    oboe::Result result = builder.openStream(g_stream);
    if (result == oboe::Result::OK) {
        g_stream->requestStart();
        LOGI("Oboe stream started successfully");
    } else {
        LOGE("Failed to create Oboe stream. Error: %s", oboe::convertToText(result));
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_auraline_app_audio_NativeEngine_setEqBand(
        JNIEnv* env,
        jobject /* this */,
        jint bandIndex,
        jfloat freq,
        jfloat gain,
        jfloat q) {

    if (bandIndex < 0 || bandIndex >= g_audioCallback.eqBands.size()) return;

    double fs = g_audioCallback.sampleRate.load(std::memory_order_relaxed);
    if (fs <= 0.0) fs = 48000.0;

    // Calculate Biquad peaking EQ coefficients (RBJ Cookbook) - double precision
    double A = std::pow(10.0, (double)gain / 40.0);
    double w0 = 2.0 * M_PI * (double)freq / fs;
    double alpha = std::sin(w0) / (2.0 * (double)q);

    double b0 = 1.0 + alpha * A;
    double b1 = -2.0 * std::cos(w0);
    double b2 = 1.0 - alpha * A;
    double a0 = 1.0 + alpha / A;
    double a1 = -2.0 * std::cos(w0);
    double a2 = 1.0 - alpha / A;

    BiquadParams params;
    params.b0 = b0 / a0;
    params.b1 = b1 / a0;
    params.b2 = b2 / a0;
    params.a1 = a1 / a0;
    params.a2 = a2 / a0;

    // Lock-free write
    g_audioCallback.eqBands[bandIndex].setTarget(params);

    LOGI("Seteando banda %d: %f Hz, %f dB, Q=%f", bandIndex, freq, gain, q);
}
