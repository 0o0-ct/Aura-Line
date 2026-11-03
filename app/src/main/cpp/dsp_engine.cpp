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
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// Denormal number prevention
inline float sanitizeDenormal(float val) {
    return (std::abs(val) < 1e-15f) ? 0.0f : val;
}

// Biquad filter parameters using double precision to prevent numeric instability
struct BiquadParams {
    double b0 = 1.0, b1 = 0.0, b2 = 0.0;
    double a1 = 0.0, a2 = 0.0;
};

// Real-Time Safe Smoothed Biquad Filter (Direct Form II Transposed)
class SmoothedBiquad {
private:
    double b0 = 1.0, b1 = 0.0, b2 = 0.0;
    double a1 = 0.0, a2 = 0.0;

    std::atomic<double> target_b0{1.0};
    std::atomic<double> target_b1{0.0};
    std::atomic<double> target_b2{0.0};
    std::atomic<double> target_a1{0.0};
    std::atomic<double> target_a2{0.0};

    double z1_l = 0.0, z2_l = 0.0;
    double z1_r = 0.0, z2_r = 0.0;

    const double smoothingFactor = 0.01; // ~10ms exponential smoothing

public:
    SmoothedBiquad() = default;

    void reset() {
        z1_l = z2_l = z1_r = z2_r = 0.0;
    }

    void setTarget(const BiquadParams& params) {
        target_b0.store(params.b0, std::memory_order_relaxed);
        target_b1.store(params.b1, std::memory_order_relaxed);
        target_b2.store(params.b2, std::memory_order_relaxed);
        target_a1.store(params.a1, std::memory_order_relaxed);
        target_a2.store(params.a2, std::memory_order_relaxed);
    }

    void setImmediate(const BiquadParams& params) {
        b0 = params.b0; b1 = params.b1; b2 = params.b2;
        a1 = params.a1; a2 = params.a2;
        setTarget(params);
    }

    inline void updateCoefficients() {
        b0 += (target_b0.load(std::memory_order_relaxed) - b0) * smoothingFactor;
        b1 += (target_b1.load(std::memory_order_relaxed) - b1) * smoothingFactor;
        b2 += (target_b2.load(std::memory_order_relaxed) - b2) * smoothingFactor;
        a1 += (target_a1.load(std::memory_order_relaxed) - a1) * smoothingFactor;
        a2 += (target_a2.load(std::memory_order_relaxed) - a2) * smoothingFactor;
    }

    inline float processL(float in) {
        double ind = (double)in;
        double out = ind * b0 + z1_l;
        z1_l = sanitizeDenormal((float)(ind * b1 - out * a1 + z2_l));
        z2_l = sanitizeDenormal((float)(ind * b2 - out * a2));
        return (float)out;
    }

    inline float processR(float in) {
        double ind = (double)in;
        double out = ind * b0 + z1_r;
        z1_r = sanitizeDenormal((float)(ind * b1 - out * a1 + z2_r));
        z2_r = sanitizeDenormal((float)(ind * b2 - out * a2));
        return (float)out;
    }

    inline void processStereoSample(float& l, float& r) {
        updateCoefficients();
        l = processL(l);
        r = processR(r);
    }
};

// 4th-Order Linkwitz-Riley Crossover (LR4, 24 dB/oct) using two cascaded 2nd-order Butterworth filters
class LinkwitzRiley4Crossover {
private:
    SmoothedBiquad lp1, lp2;
    SmoothedBiquad hp1, hp2;
    double sampleRate = 48000.0;
    double cutoffFreq = 1000.0;

public:
    void init(double fs, double cutoff) {
        sampleRate = fs;
        cutoffFreq = cutoff;
        updateFilters();
    }

    void setCutoff(double cutoff) {
        cutoffFreq = cutoff;
        updateFilters();
    }

    void updateFilters() {
        double safeCutoff = std::clamp(cutoffFreq, 20.0, sampleRate * 0.49);
        double w0 = 2.0 * M_PI * safeCutoff / sampleRate;
        double cosw = std::cos(w0);
        double sinw = std::sin(w0);
        // Q = 1 / sqrt(2) for Butterworth 2nd-order stage
        double alpha = sinw / (2.0 * 0.7071067811865475);

        // Lowpass 2nd-order Butterworth
        double b0_lp = (1.0 - cosw) / 2.0;
        double b1_lp = 1.0 - cosw;
        double b2_lp = (1.0 - cosw) / 2.0;
        double a0_lp = 1.0 + alpha;
        double a1_lp = -2.0 * cosw;
        double a2_lp = 1.0 - alpha;

        BiquadParams lpParams{
            b0_lp / a0_lp, b1_lp / a0_lp, b2_lp / a0_lp,
            a1_lp / a0_lp, a2_lp / a0_lp
        };

        // Highpass 2nd-order Butterworth
        double b0_hp = (1.0 + cosw) / 2.0;
        double b1_hp = -(1.0 + cosw);
        double b2_hp = (1.0 + cosw) / 2.0;
        double a0_hp = 1.0 + alpha;
        double a1_hp = -2.0 * cosw;
        double a2_hp = 1.0 - alpha;

        BiquadParams hpParams{
            b0_hp / a0_hp, b1_hp / a0_hp, b2_hp / a0_hp,
            a1_hp / a0_hp, a2_hp / a0_hp
        };

        lp1.setTarget(lpParams);
        lp2.setTarget(lpParams);
        hp1.setTarget(hpParams);
        hp2.setTarget(hpParams);
    }

    inline void process(float inL, float inR, float& lowL, float& lowR, float& highL, float& highR) {
        float l_lp = lp2.processL(lp1.processL(inL));
        float r_lp = lp2.processR(lp1.processR(inR));
        float l_hp = hp2.processL(hp1.processL(inL));
        float r_hp = hp2.processR(hp1.processR(inR));

        lowL = l_lp;  lowR = r_lp;
        highL = l_hp; highR = r_hp;
    }
};

// Internal Audio Signal Generator for instant audible A/B testing
class SignalGenerator {
public:
    enum Mode {
        OFF = 0,
        PINK_NOISE = 1,
        WHITE_NOISE = 2,
        SINE_SWEEP = 3
    };

private:
    std::atomic<int> mode{OFF};
    std::atomic<float> level{0.35f}; // -9 dBFS default safe test level
    double sampleRate = 48000.0;

    // Pink noise filter state (Paul Kellet's algorithm)
    float b0 = 0.0f, b1 = 0.0f, b2 = 0.0f, b3 = 0.0f, b4 = 0.0f, b5 = 0.0f, b6 = 0.0f;
    uint32_t seed = 22222;

    // Sine sweep parameters
    double sweepPhase = 0.0;
    double sweepTime = 0.0;
    const double sweepDuration = 6.0; // 6-second cyclic sweep from 20 Hz to 20,000 Hz

    inline float randUniform() {
        seed = seed * 1664525u + 1013904223u;
        return ((float)(seed >> 9) / 8388608.0f) - 1.0f;
    }

public:
    void init(double fs) {
        sampleRate = fs;
        reset();
    }

    void reset() {
        b0 = b1 = b2 = b3 = b4 = b5 = b6 = 0.0f;
        sweepPhase = 0.0;
        sweepTime = 0.0;
    }

    void setMode(int m) {
        mode.store(m, std::memory_order_relaxed);
    }

    void setLevel(float l) {
        level.store(std::clamp(l, 0.0f, 1.0f), std::memory_order_relaxed);
    }

    int getMode() const {
        return mode.load(std::memory_order_relaxed);
    }

    inline void generateSample(float& outL, float& outR) {
        int m = mode.load(std::memory_order_relaxed);
        if (m == OFF) {
            outL = 0.0f;
            outR = 0.0f;
            return;
        }

        float amp = level.load(std::memory_order_relaxed);

        if (m == WHITE_NOISE) {
            float white = randUniform() * amp;
            outL = white;
            outR = white;
        } else if (m == PINK_NOISE) {
            float white = randUniform();
            b0 = 0.99886f * b0 + white * 0.0555179f;
            b1 = 0.99332f * b1 + white * 0.0750759f;
            b2 = 0.96900f * b2 + white * 0.1538520f;
            b3 = 0.86650f * b3 + white * 0.3104856f;
            b4 = 0.55000f * b4 + white * 0.5329522f;
            b5 = -0.7616f * b5 - white * 0.0168980f;
            float pink = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362f) * 0.11f;
            b6 = white * 0.115926f;
            outL = pink * amp;
            outR = pink * amp;
        } else if (m == SINE_SWEEP) {
            // Logarithmic frequency sweep from 20 Hz to 20,000 Hz
            double t = sweepTime / sweepDuration;
            double f = 20.0 * std::pow(1000.0, t);
            sweepPhase += 2.0 * M_PI * f / sampleRate;
            if (sweepPhase >= 2.0 * M_PI) sweepPhase -= 2.0 * M_PI;

            float val = (float)std::sin(sweepPhase) * amp;
            outL = val;
            outR = val;

            sweepTime += 1.0 / sampleRate;
            if (sweepTime >= sweepDuration) {
                sweepTime = 0.0;
            }
        }
    }
};

// Lookahead True-Peak Limiter with Inter-Sample Estimation
class LookaheadTruePeakLimiter {
private:
    std::atomic<bool> enabled{true};
    std::atomic<float> thresholdDb{-1.5f}; // Default -1.5 dBFS safe ceiling for Bluetooth
    float thresholdLin = 0.8414f;
    float attack = 0.05f;      // ~1ms fast attack
    float release = 0.0001f;   // ~80ms smooth release
    float currentGain = 1.0f;

    float prevInL = 0.0f;
    float prevInR = 0.0f;

    static const int LOOKAHEAD_SAMPLES = 256;
    float delayBuffer[LOOKAHEAD_SAMPLES * 2] = {0.0f};
    int delayWriteIndex = 0;

public:
    void init(float sampleRate) {
        attack = 1.0f - std::exp(-1.0f / (0.001f * sampleRate));
        release = 1.0f - std::exp(-1.0f / (0.080f * sampleRate));
        updateThreshold();
    }

    void setEnabled(bool e) {
        enabled.store(e, std::memory_order_relaxed);
    }

    void setCeilingDb(float db) {
        thresholdDb.store(std::clamp(db, -20.0f, 0.0f), std::memory_order_relaxed);
        updateThreshold();
    }

    void updateThreshold() {
        float db = thresholdDb.load(std::memory_order_relaxed);
        thresholdLin = std::pow(10.0f, db / 20.0f);
    }

    inline void process(float& inL, float& inR, bool& clipped) {
        if (!enabled.load(std::memory_order_relaxed)) return;

        // Gradient-based true peak estimation
        float absL = std::abs(inL);
        float absR = std::abs(inR);
        float estPeakL = absL + 0.25f * std::abs(inL - prevInL);
        float estPeakR = absR + 0.25f * std::abs(inR - prevInR);
        prevInL = inL;
        prevInR = inR;

        float peak = std::max(estPeakL, estPeakR);
        if (peak > 1.0f) {
            clipped = true;
        }

        float targetGain = 1.0f;
        if (peak > thresholdLin) {
            targetGain = thresholdLin / peak;
        }

        if (targetGain < currentGain) {
            currentGain += (targetGain - currentGain) * attack;
        } else {
            currentGain += (targetGain - currentGain) * release;
        }

        // Delay buffer
        delayBuffer[delayWriteIndex * 2] = inL;
        delayBuffer[delayWriteIndex * 2 + 1] = inR;

        int readIndex = (delayWriteIndex + 1) % LOOKAHEAD_SAMPLES;
        inL = delayBuffer[readIndex * 2] * currentGain;
        inR = delayBuffer[readIndex * 2 + 1] * currentGain;

        delayWriteIndex = readIndex;
    }
};

// Feedback Delay Network (FDN) Stereo Reverb
class StereoFDNReverb {
private:
    std::atomic<bool> enabled{false};
    std::atomic<float> roomSize{0.5f};
    std::atomic<float> damping{0.5f};
    std::atomic<float> mix{0.25f};

    static const int MAX_DELAY = 4800; // ~100ms at 48kHz
    float delayBuffers[4][MAX_DELAY] = {{0.0f}};
    int writeIndices[4] = {0};
    int delayLengths[4] = {1777, 1997, 2179, 2383}; // Mutually prime lengths
    float lastFilterOutputs[4] = {0.0f};

public:
    void init(float sampleRate) {
        float scale = sampleRate / 48000.0f;
        delayLengths[0] = std::clamp((int)(1777 * scale), 100, MAX_DELAY - 1);
        delayLengths[1] = std::clamp((int)(1997 * scale), 100, MAX_DELAY - 1);
        delayLengths[2] = std::clamp((int)(2179 * scale), 100, MAX_DELAY - 1);
        delayLengths[3] = std::clamp((int)(2383 * scale), 100, MAX_DELAY - 1);
        std::memset(delayBuffers, 0, sizeof(delayBuffers));
        std::memset(lastFilterOutputs, 0, sizeof(lastFilterOutputs));
    }

    void setParams(bool e, float size, float damp, float m) {
        enabled.store(e, std::memory_order_relaxed);
        roomSize.store(std::clamp(size, 0.0f, 0.95f), std::memory_order_relaxed);
        damping.store(std::clamp(damp, 0.0f, 0.9f), std::memory_order_relaxed);
        mix.store(std::clamp(m, 0.0f, 1.0f), std::memory_order_relaxed);
    }

    inline void process(float& l, float& r) {
        if (!enabled.load(std::memory_order_relaxed)) return;

        float m = mix.load(std::memory_order_relaxed);
        if (m <= 0.001f) return;

        float size = roomSize.load(std::memory_order_relaxed);
        float damp = damping.load(std::memory_order_relaxed);
        float monoIn = (l + r) * 0.5f;

        // Read delays
        float d[4];
        for (int i = 0; i < 4; ++i) {
            int readIdx = writeIndices[i] - delayLengths[i];
            if (readIdx < 0) readIdx += MAX_DELAY;
            d[i] = delayBuffers[i][readIdx];

            // One-pole lowpass damping
            d[i] = lastFilterOutputs[i] + (d[i] - lastFilterOutputs[i]) * (1.0f - damp);
            lastFilterOutputs[i] = sanitizeDenormal(d[i]);
        }

        // Hadamard 4x4 unitary feedback matrix
        const float g = 0.5f;
        float f0 = ( d[0] + d[1] + d[2] + d[3]) * g;
        float f1 = ( d[0] - d[1] + d[2] - d[3]) * g;
        float f2 = ( d[0] + d[1] - d[2] - d[3]) * g;
        float f3 = ( d[0] - d[1] - d[2] + d[3]) * g;

        delayBuffers[0][writeIndices[0]] = sanitizeDenormal(monoIn + f0 * size);
        delayBuffers[1][writeIndices[1]] = sanitizeDenormal(monoIn + f1 * size);
        delayBuffers[2][writeIndices[2]] = sanitizeDenormal(monoIn + f2 * size);
        delayBuffers[3][writeIndices[3]] = sanitizeDenormal(monoIn + f3 * size);

        for (int i = 0; i < 4; ++i) {
            writeIndices[i] = (writeIndices[i] + 1) % MAX_DELAY;
        }

        float wetL = (d[0] + d[2]) * 0.7f;
        float wetR = (d[1] + d[3]) * 0.7f;

        l = l * (1.0f - m) + wetL * m;
        r = r * (1.0f - m) + wetR * m;
    }
};

// Mid/Side Stereo Widener with Bass Mono Protection (< 120 Hz)
class StereoWidener {
private:
    std::atomic<bool> enabled{true};
    std::atomic<float> width{1.0f}; // 1.0 = normal, 0.0 = mono, 2.0 = extra wide
    SmoothedBiquad bassMonoHpL;
    SmoothedBiquad bassMonoHpR;

public:
    void init(double sampleRate) {
        // Highpass at 120 Hz to keep bass strictly mono (centered)
        double w0 = 2.0 * M_PI * 120.0 / sampleRate;
        double cosw = std::cos(w0);
        double alpha = std::sin(w0) / (2.0 * 0.7071);
        double b0 = (1.0 + cosw) / 2.0;
        double b1 = -(1.0 + cosw);
        double b2 = (1.0 + cosw) / 2.0;
        double a0 = 1.0 + alpha;
        double a1 = -2.0 * cosw;
        double a2 = 1.0 - alpha;

        BiquadParams params{b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0};
        bassMonoHpL.setImmediate(params);
        bassMonoHpR.setImmediate(params);
    }

    void setParams(bool e, float w) {
        enabled.store(e, std::memory_order_relaxed);
        width.store(std::clamp(w, 0.0f, 2.0f), std::memory_order_relaxed);
    }

    inline void process(float& l, float& r) {
        if (!enabled.load(std::memory_order_relaxed)) return;
        float w = width.load(std::memory_order_relaxed);
        if (std::abs(w - 1.0f) < 0.01f) return;

        float mid = (l + r) * 0.5f;
        float side = (l - r) * 0.5f;

        // Bass mono: only widen side frequencies above 120 Hz
        float sideHigh = bassMonoHpL.processL(side);
        float sideLow = side - sideHigh;

        // Apply width to mids/highs, keep lows intact (width = 1.0)
        float widenedSide = sideLow + sideHigh * w;

        l = mid + widenedSide;
        r = mid - widenedSide;
    }
};

// Acoustic Model for 15 Low Frequency Instruments
class LowInstrumentEnhancer {
private:
    std::atomic<bool> enabled{false};
    SmoothedBiquad fundamentalBoost;
    SmoothedBiquad attackBoost;
    SmoothedBiquad mudCut;
    std::atomic<float> harmonicsAmount{0.0f};

public:
    void init(double sampleRate) {
        // Default flat
        BiquadParams flat{1.0, 0.0, 0.0, 0.0, 0.0};
        fundamentalBoost.setImmediate(flat);
        attackBoost.setImmediate(flat);
        mudCut.setImmediate(flat);
    }

    void setProfile(bool isEnabled, double sampleRate, float fundFreq, float fundGainDb,
                    float attackFreq, float attackGainDb, float mudFreq, float mudCutGainDb, float harmonics) {
        enabled.store(isEnabled, std::memory_order_relaxed);
        harmonicsAmount.store(harmonics, std::memory_order_relaxed);

        if (!isEnabled) return;

        // Peaking filter helper
        auto calcPeaking = [sampleRate](double f, double gDb, double q) -> BiquadParams {
            double safeF = std::clamp(f, 20.0, sampleRate * 0.49);
            double A = std::pow(10.0, gDb / 40.0);
            double w0 = 2.0 * M_PI * safeF / sampleRate;
            double alpha = std::sin(w0) / (2.0 * q);
            double b0 = 1.0 + alpha * A;
            double b1 = -2.0 * std::cos(w0);
            double b2 = 1.0 - alpha * A;
            double a0 = 1.0 + alpha / A;
            double a1 = -2.0 * std::cos(w0);
            double a2 = 1.0 - alpha / A;
            return {b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0};
        };

        fundamentalBoost.setTarget(calcPeaking(fundFreq, fundGainDb, 1.4));
        attackBoost.setTarget(calcPeaking(attackFreq, attackGainDb, 2.0));
        mudCut.setTarget(calcPeaking(mudFreq, mudCutGainDb, 1.2));
    }

    inline void process(float& l, float& r) {
        if (!enabled.load(std::memory_order_relaxed)) return;

        fundamentalBoost.processStereoSample(l, r);
        attackBoost.processStereoSample(l, r);
        mudCut.processStereoSample(l, r);

        float harm = harmonicsAmount.load(std::memory_order_relaxed);
        if (harm > 0.01f) {
            // Soft saturation for controlled warm bass harmonics (tanh-like curve)
            auto saturate = [harm](float in) -> float {
                float x = in * (1.0f + harm * 0.5f);
                return x - (x * x * x) * (harm * 0.15f);
            };
            l = saturate(l);
            r = saturate(r);
        }
    }
};

// Dynamics Suite (Compressor & Noise Gate)
class DynamicsSuite {
private:
    std::atomic<bool> compEnabled{true};
    std::atomic<float> compThresholdDb{-20.0f};
    std::atomic<float> compRatio{4.0f};
    std::atomic<float> compMakeupDb{0.0f};
    float compAttack = 0.05f;
    float compRelease = 0.001f;
    float compEnvelope = 0.0f;

    std::atomic<bool> gateEnabled{true};
    std::atomic<float> gateThresholdDb{-60.0f};
    std::atomic<float> gateRatio{2.0f};
    float gateEnvelope = 0.0f;
    float gateGain = 1.0f;

public:
    void init(float sampleRate) {
        compAttack = 1.0f - std::exp(-1.0f / (0.010f * sampleRate)); // 10ms
        compRelease = 1.0f - std::exp(-1.0f / (0.150f * sampleRate)); // 150ms
    }

    void setCompressor(bool e, float thresh, float rat, float attackMs, float releaseMs, float makeup, float sampleRate) {
        compEnabled.store(e, std::memory_order_relaxed);
        compThresholdDb.store(thresh, std::memory_order_relaxed);
        compRatio.store(std::max(1.0f, rat), std::memory_order_relaxed);
        compMakeupDb.store(makeup, std::memory_order_relaxed);
        compAttack = 1.0f - std::exp(-1.0f / (std::max(0.001f, attackMs * 0.001f) * sampleRate));
        compRelease = 1.0f - std::exp(-1.0f / (std::max(0.010f, releaseMs * 0.001f) * sampleRate));
    }

    void setGate(bool e, float thresh, float rat) {
        gateEnabled.store(e, std::memory_order_relaxed);
        gateThresholdDb.store(thresh, std::memory_order_relaxed);
        gateRatio.store(std::max(1.0f, rat), std::memory_order_relaxed);
    }

    inline void process(float& l, float& r) {
        float peak = std::max(std::abs(l), std::abs(r));

        // Noise gate
        if (gateEnabled.load(std::memory_order_relaxed)) {
            float threshLin = std::pow(10.0f, gateThresholdDb.load(std::memory_order_relaxed) / 20.0f);
            float targetGate = 1.0f;
            if (peak < threshLin) {
                targetGate = 0.0f; // Silence or attenuate
            }
            gateGain += (targetGate - gateGain) * 0.05f;
            l *= gateGain;
            r *= gateGain;
        }

        // Compressor
        if (compEnabled.load(std::memory_order_relaxed)) {
            float threshLin = std::pow(10.0f, compThresholdDb.load(std::memory_order_relaxed) / 20.0f);
            float makeupLin = std::pow(10.0f, compMakeupDb.load(std::memory_order_relaxed) / 20.0f);

            if (peak > compEnvelope) {
                compEnvelope += (peak - compEnvelope) * compAttack;
            } else {
                compEnvelope += (peak - compEnvelope) * compRelease;
            }

            float gain = 1.0f;
            if (compEnvelope > threshLin && threshLin > 1e-6f) {
                float dbOver = 20.0f * std::log10(compEnvelope / threshLin);
                float ratio = compRatio.load(std::memory_order_relaxed);
                float dbReduction = dbOver * (1.0f - 1.0f / ratio);
                gain = std::pow(10.0f, -dbReduction / 20.0f);
            }

            l = l * gain * makeupLin;
            r = r * gain * makeupLin;
        }
    }
};

// Circular Ring Buffer for AudioPlaybackCapture input PCM
class AudioRingBuffer {
private:
    static const int CAPACITY = 16384 * 2; // ~340ms stereo buffer
    float buffer[CAPACITY] = {0.0f};
    std::atomic<int> writeHead{0};
    std::atomic<int> readHead{0};

public:
    int write(const float* data, int numSamples) {
        int w = writeHead.load(std::memory_order_relaxed);
        int r = readHead.load(std::memory_order_acquire);
        int available = CAPACITY - ((w - r + CAPACITY) % CAPACITY) - 1;
        int toWrite = std::min(numSamples, available);

        for (int i = 0; i < toWrite; ++i) {
            buffer[(w + i) % CAPACITY] = data[i];
        }
        writeHead.store((w + toWrite) % CAPACITY, std::memory_order_release);
        return toWrite;
    }

    int read(float* outData, int numSamples) {
        int r = readHead.load(std::memory_order_relaxed);
        int w = writeHead.load(std::memory_order_acquire);
        int available = (w - r + CAPACITY) % CAPACITY;
        int toRead = std::min(numSamples, available);

        for (int i = 0; i < toRead; ++i) {
            outData[i] = buffer[(r + i) % CAPACITY];
        }
        readHead.store((r + toRead) % CAPACITY, std::memory_order_release);
        return toRead;
    }

    int availableRead() const {
        int r = readHead.load(std::memory_order_relaxed);
        int w = writeHead.load(std::memory_order_relaxed);
        return (w - r + CAPACITY) % CAPACITY;
    }
};

// Global Master Audio Callback for Oboe
class AudioCallback : public oboe::AudioStreamDataCallback {
public:
    std::atomic<float> sampleRate{48000.0f};
    std::atomic<bool> isBypass{false};
    std::atomic<float> preampGain{1.0f};

    // Subsonic DC Blocker
    SmoothedBiquad subsonicHpL;
    SmoothedBiquad subsonicHpR;
    std::atomic<bool> subsonicEnabled{true};

    // 25-Band Graphic/Parametric EQ
    static const int NUM_BANDS = 25;
    SmoothedBiquad eqBands[NUM_BANDS];

    // Modules
    SignalGenerator generator;
    LookaheadTruePeakLimiter limiter;
    StereoFDNReverb reverb;
    StereoWidener widener;
    LowInstrumentEnhancer enhancer;
    DynamicsSuite dynamics;
    AudioRingBuffer inputBuffer;

    // Real-Time Telemetry
    std::atomic<float> meterRmsL{0.0f};
    std::atomic<float> meterRmsR{0.0f};
    std::atomic<float> meterPeakL{0.0f};
    std::atomic<float> meterPeakR{0.0f};
    std::atomic<uint32_t> clippingCount{0};
    std::atomic<uint64_t> processedBlocks{0};

    AudioCallback() = default;

    void init(float fs) {
        sampleRate.store(fs, std::memory_order_relaxed);
        generator.init(fs);
        limiter.init(fs);
        reverb.init(fs);
        widener.init(fs);
        enhancer.init(fs);
        dynamics.init(fs);

        // Subsonic 25 Hz Butterworth 2nd order HPF
        double w0 = 2.0 * M_PI * 25.0 / fs;
        double cosw = std::cos(w0);
        double alpha = std::sin(w0) / (2.0 * 0.7071);
        double b0 = (1.0 + cosw) / 2.0;
        double b1 = -(1.0 + cosw);
        double b2 = (1.0 + cosw) / 2.0;
        double a0 = 1.0 + alpha;
        double a1 = -2.0 * cosw;
        double a2 = 1.0 - alpha;
        BiquadParams hp{b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0};
        subsonicHpL.setImmediate(hp);
        subsonicHpR.setImmediate(hp);
    }

    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream *audioStream,
            void *audioData,
            int32_t numFrames) override {

        float *floatData = static_cast<float *>(audioData);

        // 1. Fill input either from internal signal generator or captured input ringbuffer
        int genMode = generator.getMode();
        bool hasInput = (inputBuffer.availableRead() >= numFrames * 2);

        float sumSquaresL = 0.0f;
        float sumSquaresR = 0.0f;
        float peakL = 0.0f;
        float peakR = 0.0f;
        bool clippedInBlock = false;

        bool bypass = isBypass.load(std::memory_order_relaxed);
        float preamp = preampGain.load(std::memory_order_relaxed);

        for (int i = 0; i < numFrames; ++i) {
            float inL = 0.0f;
            float inR = 0.0f;

            if (genMode != SignalGenerator::OFF) {
                // Internal high-precision test generator
                generator.generateSample(inL, inR);
            } else if (hasInput) {
                float pair[2];
                inputBuffer.read(pair, 2);
                inL = pair[0];
                inR = pair[1];
            } else {
                // Silence
                inL = 0.0f;
                inR = 0.0f;
            }

            // If A/B Bypass is active, route pure generator/input directly to output
            if (bypass) {
                floatData[2*i] = inL;
                floatData[2*i + 1] = inR;
            } else {
                // Apply Preamp Gain
                float curL = inL * preamp;
                float curR = inR * preamp;

                // Subsonic Protection Filter
                if (subsonicEnabled.load(std::memory_order_relaxed)) {
                    curL = subsonicHpL.processL(curL);
                    curR = subsonicHpR.processR(curR);
                }

                // 25-Band EQ Cascade
                for (int b = 0; b < NUM_BANDS; ++b) {
                    eqBands[b].processStereoSample(curL, curR);
                }

                // Low Instrument Acoustic Enhancer
                enhancer.process(curL, curR);

                // Dynamics: Gate & Compressor
                dynamics.process(curL, curR);

                // Spatial: Stereo Widener & FDN Reverb
                widener.process(curL, curR);
                reverb.process(curL, curR);

                // True-Peak Lookahead Limiter
                limiter.process(curL, curR, clippedInBlock);

                floatData[2*i] = curL;
                floatData[2*i + 1] = curR;
            }

            // Metering
            float outL = floatData[2*i];
            float outR = floatData[2*i + 1];
            sumSquaresL += outL * outL;
            sumSquaresR += outR * outR;
            peakL = std::max(peakL, std::abs(outL));
            peakR = std::max(peakR, std::abs(outR));
        }

        // Update atomic telemetry
        float rmsL = std::sqrt(sumSquaresL / (float)numFrames);
        float rmsR = std::sqrt(sumSquaresR / (float)numFrames);
        meterRmsL.store(rmsL, std::memory_order_relaxed);
        meterRmsR.store(rmsR, std::memory_order_relaxed);
        meterPeakL.store(peakL, std::memory_order_relaxed);
        meterPeakR.store(peakR, std::memory_order_relaxed);

        if (clippedInBlock) {
            clippingCount.fetch_add(1, std::memory_order_relaxed);
        }
        processedBlocks.fetch_add(1, std::memory_order_relaxed);

        return oboe::DataCallbackResult::Continue;
    }
};

static AudioCallback g_audioCallback;
static std::shared_ptr<oboe::AudioStream> g_stream;

extern "C" {

JNIEXPORT void JNICALL
Java_com_auraline_app_audio_NativeEngine_init(
        JNIEnv* env,
        jobject /* this */,
        jint sampleRate) {
    LOGI("Inicializando motor DSP nativo unificado a %d Hz", sampleRate);

    g_audioCallback.init((float)sampleRate);

    if (g_stream && g_stream->getState() == oboe::StreamState::Open) {
        g_stream->close();
    }

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
           ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
           ->setSharingMode(oboe::SharingMode::Shared)
           ->setFormat(oboe::AudioFormat::Float)
           ->setChannelCount(2)
           ->setSampleRate(sampleRate)
           ->setDataCallback(&g_audioCallback);

    oboe::Result result = builder.openStream(g_stream);
    if (result == oboe::Result::OK) {
        g_stream->requestStart();
        LOGI("Oboe output stream abierto con exito (LowLatency Shared Float)");
    } else {
        LOGE("Fallo al abrir stream Oboe: %s", oboe::convertToText(result));
    }
}

JNIEXPORT void JNICALL
Java_com_auraline_app_audio_NativeEngine_setBypass(
        JNIEnv* env,
        jobject /* this */,
        jboolean bypass) {
    g_audioCallback.isBypass.store(bypass, std::memory_order_relaxed);
}

JNIEXPORT void JNICALL
Java_com_auraline_app_audio_NativeEngine_setGeneratorMode(
        JNIEnv* env,
        jobject /* this */,
        jint mode) {
    g_audioCallback.generator.setMode(mode);
}

JNIEXPORT void JNICALL
Java_com_auraline_app_audio_NativeEngine_setGeneratorLevel(
        JNIEnv* env,
        jobject /* this */,
        jfloat level) {
    g_audioCallback.generator.setLevel(level);
}

JNIEXPORT void JNICALL
Java_com_auraline_app_audio_NativeEngine_setPreampGain(
        JNIEnv* env,
        jobject /* this */,
        jfloat gainDb) {
    float safeDb = std::clamp((float)gainDb, -36.0f, 36.0f);
    g_audioCallback.preampGain.store(std::pow(10.0f, safeDb / 20.0f), std::memory_order_relaxed);
}

JNIEXPORT void JNICALL
Java_com_auraline_app_audio_NativeEngine_setEqBand(
        JNIEnv* env,
        jobject /* this */,
        jint bandIndex,
        jfloat freq,
        jfloat gain,
        jfloat q) {

    if (bandIndex < 0 || bandIndex >= AudioCallback::NUM_BANDS) return;

    double fs = g_audioCallback.sampleRate.load(std::memory_order_relaxed);
    if (fs <= 0.0) fs = 48000.0;

    double safeFreq = std::clamp((double)freq, 10.0, fs * 0.49);
    double safeGain = std::clamp((double)gain, -36.0, 36.0);
    double safeQ = std::clamp((double)q, 0.1, 20.0);

    double A = std::pow(10.0, safeGain / 40.0);
    double w0 = 2.0 * M_PI * safeFreq / fs;
    double alpha = std::sin(w0) / (2.0 * safeQ);

    double b0 = 1.0 + alpha * A;
    double b1 = -2.0 * std::cos(w0);
    double b2 = 1.0 - alpha * A;
    double a0 = 1.0 + alpha / A;
    double a1 = -2.0 * std::cos(w0);
    double a2 = 1.0 - alpha / A;

    BiquadParams params{b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0};
    g_audioCallback.eqBands[bandIndex].setTarget(params);
}

JNIEXPORT void JNICALL
Java_com_auraline_app_audio_NativeEngine_setSubsonic(
        JNIEnv* env,
        jobject /* this */,
        jboolean enabled,
        jfloat cutoffHz) {
    g_audioCallback.subsonicEnabled.store(enabled, std::memory_order_relaxed);
}

JNIEXPORT void JNICALL
Java_com_auraline_app_audio_NativeEngine_setInstrumentEnhancer(
        JNIEnv* env,
        jobject /* this */,
        jboolean enabled,
        jfloat fundamentalHz,
        jfloat bodyDb,
        jfloat punchDb,
        jfloat mudCutDb,
        jfloat harmonics) {
    double fs = g_audioCallback.sampleRate.load(std::memory_order_relaxed);
    g_audioCallback.enhancer.setProfile(enabled, fs, fundamentalHz, bodyDb, fundamentalHz * 3.0f, punchDb, 400.0f, mudCutDb, harmonics);
}

JNIEXPORT void JNICALL
Java_com_auraline_app_audio_NativeEngine_setCompressor(
        JNIEnv* env,
        jobject /* this */,
        jboolean enabled,
        jfloat thresholdDb,
        jfloat ratio,
        jfloat attackMs,
        jfloat releaseMs,
        jfloat makeupGainDb) {
    float fs = g_audioCallback.sampleRate.load(std::memory_order_relaxed);
    g_audioCallback.dynamics.setCompressor(enabled, thresholdDb, ratio, attackMs, releaseMs, makeupGainDb, fs);
}

JNIEXPORT void JNICALL
Java_com_auraline_app_audio_NativeEngine_setNoiseGate(
        JNIEnv* env,
        jobject /* this */,
        jboolean enabled,
        jfloat thresholdDb,
        jfloat ratio) {
    g_audioCallback.dynamics.setGate(enabled, thresholdDb, ratio);
}

JNIEXPORT void JNICALL
Java_com_auraline_app_audio_NativeEngine_setReverb(
        JNIEnv* env,
        jobject /* this */,
        jboolean enabled,
        jfloat roomSize,
        jfloat damping,
        jfloat decay,
        jfloat predelay,
        jfloat mix) {
    g_audioCallback.reverb.setParams(enabled, roomSize, damping, mix);
}

JNIEXPORT void JNICALL
Java_com_auraline_app_audio_NativeEngine_setStereoWidener(
        JNIEnv* env,
        jobject /* this */,
        jboolean enabled,
        jfloat width,
        jfloat bassMonoCutoffHz) {
    g_audioCallback.widener.setParams(enabled, width);
}

JNIEXPORT void JNICALL
Java_com_auraline_app_audio_NativeEngine_setLimiter(
        JNIEnv* env,
        jobject /* this */,
        jboolean enabled,
        jfloat ceilingDb) {
    g_audioCallback.limiter.setEnabled(enabled);
    g_audioCallback.limiter.setCeilingDb(ceilingDb);
}

JNIEXPORT void JNICALL
Java_com_auraline_app_audio_NativeEngine_setBluetoothRoute(
        JNIEnv* env,
        jobject /* this */,
        jboolean isBluetooth) {
    if (isBluetooth) {
        g_audioCallback.limiter.setCeilingDb(-1.5f); // Safety -1.5 dBFS for Bluetooth
    } else {
        g_audioCallback.limiter.setCeilingDb(-1.0f);
    }
}

JNIEXPORT jint JNICALL
Java_com_auraline_app_audio_NativeEngine_writeInputPcm(
        JNIEnv* env,
        jobject /* this */,
        jfloatArray samples,
        jint offset,
        jint length) {
    if (!samples || length <= 0) return 0;
    jfloat* pcm = env->GetFloatArrayElements(samples, nullptr);
    int written = g_audioCallback.inputBuffer.write(pcm + offset, length);
    env->ReleaseFloatArrayElements(samples, pcm, JNI_ABORT);
    return written;
}

JNIEXPORT void JNICALL
Java_com_auraline_app_audio_NativeEngine_getTelemetry(
        JNIEnv* env,
        jobject /* this */,
        jfloatArray outTelemetry) {
    if (!outTelemetry) return;
    jsize len = env->GetArrayLength(outTelemetry);
    if (len < 6) return;

    jfloat tele[6];
    tele[0] = g_audioCallback.meterRmsL.load(std::memory_order_relaxed);
    tele[1] = g_audioCallback.meterRmsR.load(std::memory_order_relaxed);
    tele[2] = g_audioCallback.meterPeakL.load(std::memory_order_relaxed);
    tele[3] = g_audioCallback.meterPeakR.load(std::memory_order_relaxed);
    tele[4] = (float)g_audioCallback.clippingCount.load(std::memory_order_relaxed);
    tele[5] = (float)g_audioCallback.processedBlocks.load(std::memory_order_relaxed);

    env->SetFloatArrayRegion(outTelemetry, 0, 6, tele);
}

} // extern "C"
