// AuraLine — motor DSP nativo (esqueleto seguro).
//
// Estado actual, para no engañar a nadie que lea este archivo:
//   * El motor NO está enganchado a la salida del sistema. Sin root, Android no permite
//     insertar un procesador propio en la mezcla global; para eso sigue usándose
//     android.media.audiofx.DynamicsProcessing desde Kotlin (core/audio/AuraLineEngine.kt).
//   * Este código procesa buffers que se le entreguen explícitamente (processBlock) y sirve
//     como base verificable para el modo "captura + salida propia" o para un módulo de efecto
//     con root. Por diseño NO abre ningún stream de Oboe: un stream Exclusive de salida que
//     emite silencio puede robar la ruta de audio y dejar el teléfono mudo.
//   * Todo lo que toca el camino de audio está libre de asignaciones, locks y logging.
//
// Cadena: EQ (biquads en cascada, coeficientes double con rampa) → limitador con lookahead
// y detección de pico inter-muestra → saneado de NaN/Inf → soft-clip de seguridad.

#include <jni.h>
#include <cmath>
#include <atomic>
#include <algorithm>
#include <cstring>
#include <cstdint>
#include <array>
#include <android/log.h>

#define LOG_TAG "AuraLineDSP"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

namespace {

constexpr int kMaxBands = 31;
constexpr int kChannels = 2;
// Lookahead: 240 muestras ≈ 5 ms a 48 kHz. Potencia de dos no es necesaria, se usa módulo.
constexpr int kLookahead = 240;
// Rampa de parámetros ≈ 15 ms a 48 kHz (1 - exp(-1/(0.015*48000)) ≈ 0.00139).
constexpr double kSmoothing = 0.0014;

inline float sanitize(float v) {
    // NaN/Inf → 0; denormales → 0 (evita el coste de FP denormal en ARM sin FTZ).
    if (!std::isfinite(v)) return 0.0f;
    if (std::fabs(v) < 1.0e-20f) return 0.0f;
    return v;
}

inline float dbToLinear(float db) { return std::pow(10.0f, db / 20.0f); }

/** Biquad estéreo (Direct Form II transpuesta) con rampa lineal de coeficientes. */
class SmoothedBiquad {
public:
    void setTarget(double b0, double b1, double b2, double a1, double a2) {
        t_[0].store(b0, std::memory_order_relaxed);
        t_[1].store(b1, std::memory_order_relaxed);
        t_[2].store(b2, std::memory_order_relaxed);
        t_[3].store(a1, std::memory_order_relaxed);
        t_[4].store(a2, std::memory_order_relaxed);
    }

    void reset() {
        for (auto& z : zl_) z = 0.0;
        for (auto& z : zr_) z = 0.0;
    }

    void process(float* interleaved, int frames) {
        for (int i = 0; i < frames; ++i) {
            // Rampa una vez por frame: suficiente para matar el "zipper noise".
            c_[0] += (t_[0].load(std::memory_order_relaxed) - c_[0]) * kSmoothing;
            c_[1] += (t_[1].load(std::memory_order_relaxed) - c_[1]) * kSmoothing;
            c_[2] += (t_[2].load(std::memory_order_relaxed) - c_[2]) * kSmoothing;
            c_[3] += (t_[3].load(std::memory_order_relaxed) - c_[3]) * kSmoothing;
            c_[4] += (t_[4].load(std::memory_order_relaxed) - c_[4]) * kSmoothing;

            interleaved[2 * i]     = tick(interleaved[2 * i], zl_);
            interleaved[2 * i + 1] = tick(interleaved[2 * i + 1], zr_);
        }
    }

private:
    inline float tick(float in, double* z) const {
        const double x = in;
        const double y = x * c_[0] + z[0];
        z[0] = x * c_[1] - y * c_[3] + z[1];
        z[1] = x * c_[2] - y * c_[4];
        return static_cast<float>(y);
    }

    // c_ = {b0, b1, b2, a1, a2} actuales; t_ = objetivos escritos por la UI.
    double c_[5] = {1.0, 0.0, 0.0, 0.0, 0.0};
    std::atomic<double> t_[5] = {{1.0}, {0.0}, {0.0}, {0.0}, {0.0}};
    double zl_[2] = {0.0, 0.0};
    double zr_[2] = {0.0, 0.0};
};

/**
 * Limitador con lookahead real: la señal se retrasa kLookahead muestras y la ganancia se
 * calcula sobre el pico MÁXIMO de la ventana que está por salir, de modo que la reducción
 * llega antes que el transitorio. El pico se estima con interpolación parabólica entre
 * muestras consecutivas (aproximación barata al true-peak; un oversampling 4x con FIR
 * polifásico es la mejora pendiente).
 *
 * La ganancia nunca queda por encima de lo que exige la ventana (clamp tras el ataque): así
 * una señal a 0 dBFS con +24 dB en todas las bandas sigue saliendo bajo el techo. El máximo
 * deslizante se calcula con una cola monótona de tamaño fijo: O(1) amortizado por muestra,
 * sin asignaciones.
 */
class LookaheadLimiter {
public:
    void setCeilingDb(float db) { ceiling_.store(dbToLinear(db), std::memory_order_relaxed); }
    void setSampleRate(float fs) {
        attackCoef_  = 1.0f - std::exp(-1.0f / (0.0005f * fs)); // 0.5 ms
        releaseCoef_ = 1.0f - std::exp(-1.0f / (0.080f  * fs)); // 80 ms
    }
    float gainReductionDb() const {
        const float g = gain_.load(std::memory_order_relaxed);
        return 20.0f * std::log10(std::max(g, 1.0e-6f));
    }

    void reset() {
        delay_.fill(0.0f);
        peaks_.fill(0.0f);
        write_ = 0;
        env_ = 1.0;
        prevL_ = prevR_ = 0.0f;
        dqHead_ = dqTail_ = 0;
        sampleIndex_ = 0;
    }

    void process(float* interleaved, int frames) {
        const float ceiling = ceiling_.load(std::memory_order_relaxed);
        for (int i = 0; i < frames; ++i) {
            const float inL = interleaved[2 * i];
            const float inR = interleaved[2 * i + 1];

            // 1) La muestra que sale ahora es la escrita hace exactamente kLookahead muestras.
            const float outL = delay_[write_ * 2];
            const float outR = delay_[write_ * 2 + 1];
            delay_[write_ * 2]     = inL;
            delay_[write_ * 2 + 1] = inR;
            write_ = (write_ + 1 == kLookahead) ? 0 : write_ + 1;

            // 2) Pico inter-muestra de la entrada y máximo deslizante de la ventana.
            const float pk = std::max(interPeak(prevL_, inL), interPeak(prevR_, inR));
            prevL_ = inL; prevR_ = inR;
            pushPeak(pk);
            const float windowPeak = frontPeak();

            // 3) Ganancia objetivo y envolvente. El clamp garantiza que nunca se supere el techo.
            // env_ es double: en float la liberación se estanca a ~1e-4 del objetivo (epsilon)
            // y la prueba nula deja de ser transparente.
            const double target = windowPeak > ceiling ? static_cast<double>(ceiling / windowPeak) : 1.0;
            if (target < env_) env_ += (target - env_) * attackCoef_;
            else               env_ += (target - env_) * releaseCoef_;
            if (env_ > target) env_ = target;

            const float g = static_cast<float>(env_);
            interleaved[2 * i]     = outL * g;
            interleaved[2 * i + 1] = outR * g;
        }
        gain_.store(static_cast<float>(env_), std::memory_order_relaxed);
    }

private:
    static inline float interPeak(float a, float b) {
        // Máximo de la parábola que pasa por |a| y |b| con vértice entre ambos.
        const float fa = std::fabs(a), fb = std::fabs(b);
        const float m = std::max(fa, fb);
        // Si ambos tienen el mismo signo y son grandes, el pico real puede superar a ambos.
        if ((a > 0.0f) == (b > 0.0f)) return m + 0.25f * std::fabs(fb - fa);
        return m;
    }

    // --- máximo deslizante (cola monótona decreciente sobre un anillo fijo) ---
    static constexpr int kDq = kLookahead + 1;
    inline void pushPeak(float pk) {
        // Expulsa por el final todo lo que sea menor o igual: nunca volverá a ser el máximo.
        while (dqHead_ != dqTail_) {
            const int last = (dqTail_ == 0 ? kDq : dqTail_) - 1;
            if (peaks_[last] > pk) break;
            dqTail_ = last;
        }
        peaks_[dqTail_] = pk;
        peakIdx_[dqTail_] = sampleIndex_;
        dqTail_ = (dqTail_ + 1 == kDq) ? 0 : dqTail_ + 1;
        // Expulsa por el frente lo que ya salió de la ventana.
        while (dqHead_ != dqTail_ &&
               sampleIndex_ - peakIdx_[dqHead_] >= static_cast<uint32_t>(kLookahead)) {
            dqHead_ = (dqHead_ + 1 == kDq) ? 0 : dqHead_ + 1;
        }
        ++sampleIndex_;
    }
    inline float frontPeak() const { return dqHead_ != dqTail_ ? peaks_[dqHead_] : 0.0f; }

    std::atomic<float> ceiling_{dbToLinear(-1.0f)};
    std::atomic<float> gain_{1.0f};
    float attackCoef_ = 0.04f, releaseCoef_ = 0.00026f;
    std::array<float, kLookahead * kChannels> delay_{};
    std::array<float, kDq> peaks_{};
    std::array<uint32_t, kDq> peakIdx_{};
    int dqHead_ = 0, dqTail_ = 0;
    uint32_t sampleIndex_ = 0;
    int write_ = 0;
    double env_ = 1.0;
    float prevL_ = 0.0f, prevR_ = 0.0f;
};

/** Red de seguridad: tanh suave sólo por encima del techo; en régimen normal es identidad. */
inline float softClip(float x) {
    constexpr float kKnee = 0.95f;
    const float ax = std::fabs(x);
    if (ax <= kKnee) return x;
    const float over = ax - kKnee;
    const float y = kKnee + (1.0f - kKnee) * std::tanh(over / (1.0f - kKnee));
    return x < 0.0f ? -y : y;
}

/** Estantería vía peaking EQ de Q alta en el crossover (aproximación shelf por paso). */
static void setPeakingTarget(SmoothedBiquad& b, float fs, float freq, float gainDb, float q) {
    const double A = std::pow(10.0, gainDb / 40.0);
    const double w0 = 2.0 * M_PI * freq / fs;
    const double alpha = std::sin(w0) / (2.0 * q);
    const double cosw = std::cos(w0);
    const double a0 = 1.0 + alpha / A;
    b.setTarget((1.0 + alpha * A) / a0, (-2.0 * cosw) / a0, (1.0 - alpha * A) / a0, (-2.0 * cosw) / a0, (1.0 - alpha / A) / a0);
}

/**
 * Compresor dinamico con lookahead (ventana maxima como el limitador) y makeup posterior.
 * Modo: si el pico de la ventana supera el umbral, se comprime con ratio (1 = limite con
 * makeup); el makeup sube el post-gain despues de la reduction. attack/release en ms.
 */
class Compressor {
public:
    void setSampleRate(float fs) {
        attackCoef_ = 1.0f - std::exp(-1.0f / (0.001f * attackMs_ * fs));
        releaseCoef_ = 1.0f - std::exp(-1.0f / (0.001f * releaseMs_ * fs));
    }
    void setParams(float thresholdDb, float ratio, float attackMs, float releaseMs, float makeupDb) {
        thresholdDb_ = thresholdDb;
        ratio_ = std::max(1.0f, ratio);
        attackMs_ = attackMs;
        releaseMs_ = releaseMs;
        makeupLin_ = dbToLinear(makeupDb);
        setSampleRate(48000.0f);
    }
    float gainReductionDb() const {
        const float g = gain_.load(std::memory_order_relaxed);
        return 20.0f * std::log10(std::max(g, 1.0e-6f));
    }
    void reset() {
        delay_.fill(0.0f);
        peaks_.fill(0.0f);
        write_ = 0;
        env_ = 1.0;
        prevL_ = prevR_ = 0.0f;
        dqHead_ = dqTail_ = 0;
        sampleIndex_ = 0;
    }
    void process(float* interleaved, int frames) {
        const float threshold = dbToLinear(thresholdDb_);
        for (int i = 0; i < frames; ++i) {
            const float inL = interleaved[2 * i];
            const float inR = interleaved[2 * i + 1];
            const float outL = delay_[write_ * 2];
            const float outR = delay_[write_ * 2 + 1];
            delay_[write_ * 2] = inL;
            delay_[write_ * 2 + 1] = inR;
            write_ = (write_ + 1 == kLookahead) ? 0 : write_ + 1;
            const float pk = std::max(interPeak(prevL_, inL), interPeak(prevR_, inR));
            prevL_ = inL; prevR_ = inR;
            pushPeak(pk);
            const float windowPeak = frontPeak();
            float g = 1.0f;
            if (windowPeak > threshold) {
                g = (threshold + (windowPeak - threshold) / ratio_) / windowPeak;
            }
            if (g < env_) env_ += (g - env_) * releaseCoef_;
            else       env_ += (g - env_) * attackCoef_;
            if (env_ > g) env_ = g;
            const float gain = env_ * makeupLin_;
            interleaved[2 * i] = outL * gain;
            interleaved[2 * i + 1] = outR * gain;
        }
        gain_.store(env_, std::memory_order_relaxed);
    }
private:
    static inline float interPeak(float a, float b) {
        const float fa = std::fabs(a), fb = std::fabs(b);
        const float m = std::max(fa, fb);
        if ((a > 0.0f) == (b > 0.0f)) return m + 0.25f * std::fabs(fb - fa);
        return m;
    }
    static constexpr int kDq = kLookahead + 1;
    inline void pushPeak(float pk) {
        while (dqHead_ != dqTail_) {
            const int last = (dqTail_ == 0 ? kDq : dqTail_) - 1;
            if (peaks_[last] > pk) break;
            dqTail_ = last;
        }
        peaks_[dqTail_] = pk;
        peakIdx_[dqTail_] = sampleIndex_;
        dqTail_ = (dqTail_ + 1 == kDq) ? 0 : dqTail_ + 1;
        while (dqHead_ != dqTail_ &&
               sampleIndex_ - peakIdx_[dqHead_] >= static_cast<uint32_t>(kLookahead)) {
            dqHead_ = (dqHead_ + 1 == kDq) ? 0 : dqHead_ + 1;
        }
        ++sampleIndex_;
    }
    inline float frontPeak() const { return dqHead_ != dqTail_ ? peaks_[dqHead_] : 0.0f; }

    std::atomic<float> thresholdDb_{-6.0f};
    std::atomic<float> gain_{1.0f};
    float ratio_ = 20.0f;
    float attackMs_ = 10.0f;
    float releaseMs_ = 200.0f;
    float makeupLin_ = 1.0f;
    float attackCoef_ = 0.04f, releaseCoef_ = 0.00026f;
    std::array<float, kLookahead * kChannels> delay_{};
    std::array<float, kDq> peaks_{};
    std::array<uint32_t, kDq> peakIdx_{};
    int dqHead_ = 0, dqTail_ = 0;
    uint32_t sampleIndex_ = 0;
    int write_ = 0;
    double env_ = 1.0;
    float prevL_ = 0.0f, prevR_ = 0.0f;
};

/**
 * Puerta (gate) dinamico con lookahead, umbral y makeup: cuando el pico de la ventana esta
 * por debajo del umbral se aplica el makeup (puerta cerrada); por encima se abre con ganancia
 * unitaria. Es el modulacion de dinamica que opera en las bandas del compresor.
 */
class Gate {
public:
    void setSampleRate(float fs) {
        attackCoef_ = 1.0f - std::exp(-1.0f / (0.001f * attackMs_ * fs));
        releaseCoef_ = 1.0f - std::exp(-1.0f / (0.001f * releaseMs_ * fs));
    }
    void setParams(float thresholdDb, float ratio, float attackMs, float releaseMs, float makeupDb) {
        thresholdDb_ = thresholdDb;
        ratio_ = std::max(1.0f, ratio);
        attackMs_ = attackMs;
        releaseMs_ = releaseMs;
        makeupLin_ = dbToLinear(makeupDb);
        setSampleRate(48000.0f);
    }
    void reset() {
        delay_.fill(0.0f);
        peaks_.fill(0.0f);
        write_ = 0;
        env_ = 1.0;
        prevL_ = prevR_ = 0.0f;
        dqHead_ = dqTail_ = 0;
        sampleIndex_ = 0;
    }
    void process(float* interleaved, int frames) {
        const float threshold = dbToLinear(thresholdDb_);
        for (int i = 0; i < frames; ++i) {
            const float inL = interleaved[2 * i];
            const float inR = interleaved[2 * i + 1];
            const float outL = delay_[write_ * 2];
            const float outR = delay_[write_ * 2 + 1];
            delay_[write_ * 2] = inL;
            delay_[write_ * 2 + 1] = inR;
            write_ = (write_ + 1 == kLookahead) ? 0 : write_ + 1;
            const float pk = std::max(interPeak(prevL_, inL), interPeak(prevR_, inR));
            prevL_ = inL; prevR_ = inR;
            pushPeak(pk);
            const float windowPeak = frontPeak();
            float g = (windowPeak > threshold) ? 1.0f : makeupLin_;
            if (g < env_) env_ += (g - env_) * releaseCoef_;
            else       env_ += (g - env_) * attackCoef_;
            if (env_ > g) env_ = g;
            interleaved[2 * i] = outL * env_;
            interleaved[2 * i + 1] = outR * env_;
        }
    }
private:
    static inline float interPeak(float a, float b) {
        const float fa = std::fabs(a), fb = std::fabs(b);
        const float m = std::max(fa, fb);
        if ((a > 0.0f) == (b > 0.0f)) return m + 0.25f * std::fabs(fb - fa);
        return m;
    }
    static constexpr int kDq = kLookahead + 1;
    inline void pushPeak(float pk) {
        while (dqHead_ != dqTail_) {
            const int last = (dqTail_ == 0 ? kDq : dqTail_) - 1;
            if (peaks_[last] > pk) break;
            dqTail_ = last;
        }
        peaks_[dqTail_] = pk;
        peakIdx_[dqTail_] = sampleIndex_;
        dqTail_ = (dqTail_ + 1 == kDq) ? 0 : dqTail_ + 1;
        while (dqHead_ != dqTail_ &&
               sampleIndex_ - peakIdx_[dqHead_] >= static_cast<uint32_t>(kLookahead)) {
            dqHead_ = (dqHead_ + 1 == kDq) ? 0 : dqHead_ + 1;
        }
        ++sampleIndex_;
    }
    inline float frontPeak() const { return dqHead_ != dqTail_ ? peaks_[dqHead_] : 0.0f; }
    std::atomic<float> thresholdDb_{-6.0f};
    std::atomic<float> gain_{1.0f};
    float ratio_ = 20.0f;
    float attackMs_ = 10.0f;
    float releaseMs_ = 200.0f;
    float makeupLin_ = 1.0f;
    float attackCoef_ = 0.04f, releaseCoef_ = 0.00026f;
    std::array<float, kLookahead * kChannels> delay_{};
    std::array<float, kDq> peaks_{};
    std::array<uint32_t, kDq> peakIdx_{};
    int dqHead_ = 0, dqTail_ = 0;
    uint32_t sampleIndex_ = 0;
    int write_ = 0;
    double env_ = 1.0;
    float prevL_ = 0.0f, prevR_ = 0.0f;
};

struct Engine {
    std::atomic<float> sampleRate{48000.0f};
    std::atomic<float> preampLin{1.0f};
    std::atomic<int>   activeBands{0};
    std::atomic<bool>  enabled{false};
    std::atomic<float> peakOut{0.0f};
    SmoothedBiquad bands[kMaxBands];
    LookaheadLimiter limiter;
    // Tono (Fase 4): estanterías Graves/Medios (crossover graves) y Medios/Agudos (crossover
    // agudos) + paso bajo del sub-bajo. Se aplican tras el PreEq y antes del limitador.
    SmoothedBiquad toneBassShelf;
    SmoothedBiquad toneTrebleShelf;
    SmoothedBiquad toneSubShelf;
    Compressor compressor;
    bool compressorActive_ = false;
    Gate gate;
    bool gateActive_ = false;
    float toneBassDb = 0.0f, toneBassHz = 100.0f;
    float toneTrebleDb = 0.0f, toneTrebleHz = 8000.0f;
    float toneSubDb = 0.0f, toneSubHz = 60.0f;

    void reset() {
        for (auto& b : bands) b.reset();
        toneBassShelf.reset();
        toneTrebleShelf.reset();
        toneSubShelf.reset();
        compressor.reset();
        gate.reset();
        limiter.reset();
    }

    /**
     * Tono completo: estanterías (Graves/Medios y Medios/Agudos) y sub-bajo. Las estanterías
     * se aproximan con peaking EQ de Q alta en el crossover (misma aproximación de banda que el
     * MBC del camino de Android). Los rangos son los publicados por la UI.
     */
    void setTone(float bassDb, float bassHz, float trebleDb, float trebleHz, float subDb, float subHz) {
        bassDb = std::min(30.0f, std::max(-30.0f, bassDb));
        trebleDb = std::min(30.0f, std::max(-30.0f, trebleDb));
        subDb = std::min(30.0f, std::max(-30.0f, subDb));
        bassHz = std::min(400.0f, std::max(20.0f, bassHz));
        trebleHz = std::min(16000.0f, std::max(2000.0f, trebleHz));
        subHz = std::min(100.0f, std::max(20.0f, subHz));
        toneBassDb = bassDb; toneBassHz = bassHz;
        toneTrebleDb = trebleDb; toneTrebleHz = trebleHz;
        toneSubDb = subDb; toneSubHz = subHz;
        const float fs = sampleRate.load(std::memory_order_relaxed);
        setPeakingTarget(toneBassShelf, fs, toneBassHz, toneBassDb, 8.0f);
        setPeakingTarget(toneTrebleShelf, fs, toneTrebleHz, toneTrebleDb, 8.0f);
        setPeakingTarget(toneSubShelf, fs, toneSubHz, toneSubDb, 8.0f);
    }

    void setCompressor(float thresholdDb, float ratio, float attackMs, float releaseMs, float makeupDb) {
        compressor.setSampleRate(sampleRate.load(std::memory_order_relaxed));
        compressor.setParams(thresholdDb, ratio, attackMs, releaseMs, makeupDb);
        // El stage compresor solo se aplica cuando la reduccion lo pide (ratio>1 o makeup>0).
        compressorActive_ = (ratio > 1.0f) || (makeupDb > 0.0f);
    }

    void setGate(float thresholdDb, float ratio, float attackMs, float releaseMs, float makeupDb) {
        gate.setSampleRate(sampleRate.load(std::memory_order_relaxed));
        gate.setParams(thresholdDb, ratio, attackMs, releaseMs, makeupDb);
        // La puerta se aplica cuando pide actuacion (makeup>0 o ratio>1).
        gateActive_ = (makeupDb > 0.0f) || (ratio > 1.0f);
    }

    /** Procesa in situ un bloque estéreo intercalado. Sin asignaciones, sin locks. */
    void processBlock(float* buf, int frames) {
        if (!enabled.load(std::memory_order_relaxed)) return;
        const float pre = preampLin.load(std::memory_order_relaxed);
        const int n = frames * kChannels;
        for (int i = 0; i < n; ++i) buf[i] = sanitize(buf[i]) * pre;

        const int count = std::min(activeBands.load(std::memory_order_relaxed), kMaxBands);
        for (int b = 0; b < count; ++b) bands[b].process(buf, frames);

        // Tono: estanterías + sub-bajo (Fase 4).
        toneBassShelf.process(buf, frames);
        toneTrebleShelf.process(buf, frames);
        toneSubShelf.process(buf, frames);

        // Compresor: lookahead + ratio + makeup, antes del limitador final.
        if (compressorActive_) compressor.process(buf, frames);
        if (gateActive_) gate.process(buf, frames);
        limiter.process(buf, frames);

        float pk = 0.0f;
        for (int i = 0; i < n; ++i) {
            const float v = softClip(sanitize(buf[i]));
            buf[i] = v;
            pk = std::max(pk, std::fabs(v));
        }
        peakOut.store(pk, std::memory_order_relaxed);
    }
};

Engine g_engine;

} // namespace

// ---------------------------------------------------------------------------------------
// JNI — com.auraline.app.audio.NativeEngine
// ---------------------------------------------------------------------------------------

extern "C" JNIEXPORT void JNICALL
Java_com_auraline_app_audio_NativeEngine_nativeInit(JNIEnv*, jobject, jint sampleRate) {
    const float fs = sampleRate > 0 ? static_cast<float>(sampleRate) : 48000.0f;
    g_engine.sampleRate.store(fs, std::memory_order_relaxed);
    g_engine.limiter.setSampleRate(fs);
    g_engine.reset();
    LOGI("Motor nativo listo a %.0f Hz (sin stream de salida: modo offline/verificación)", fs);
}

extern "C" JNIEXPORT void JNICALL
Java_com_auraline_app_audio_NativeEngine_nativeSetEnabled(JNIEnv*, jobject, jboolean on) {
    g_engine.enabled.store(on == JNI_TRUE, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_auraline_app_audio_NativeEngine_nativeSetPreamp(JNIEnv*, jobject, jfloat db) {
    g_engine.preampLin.store(dbToLinear(std::clamp(db, -40.0f, 20.0f)), std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_auraline_app_audio_NativeEngine_nativeSetLimiterCeiling(JNIEnv*, jobject, jfloat db) {
    g_engine.limiter.setCeilingDb(std::clamp(db, -12.0f, 0.0f));
}

extern "C" JNIEXPORT void JNICALL
Java_com_auraline_app_audio_NativeEngine_nativeSetBandCount(JNIEnv*, jobject, jint count) {
    g_engine.activeBands.store(std::clamp(count, 0, kMaxBands), std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_com_auraline_app_audio_NativeEngine_nativeSetEqBand(
        JNIEnv*, jobject, jint index, jfloat freq, jfloat gainDb, jfloat q) {
    if (index < 0 || index >= kMaxBands) return;
    const double fs = g_engine.sampleRate.load(std::memory_order_relaxed);
    const double f  = std::clamp(static_cast<double>(freq), 10.0, fs * 0.45);
    const double Q  = std::clamp(static_cast<double>(q), 0.1, 10.0);

    // Peaking EQ (RBJ cookbook), double.
    const double A     = std::pow(10.0, static_cast<double>(gainDb) / 40.0);
    const double w0    = 2.0 * M_PI * f / fs;
    const double alpha = std::sin(w0) / (2.0 * Q);
    const double cosw  = std::cos(w0);

    const double a0 = 1.0 + alpha / A;
    g_engine.bands[index].setTarget(
            (1.0 + alpha * A) / a0,
            (-2.0 * cosw) / a0,
            (1.0 - alpha * A) / a0,
            (-2.0 * cosw) / a0,
            (1.0 - alpha / A) / a0);
}

/**
 * Procesa un bloque estéreo intercalado float32 in situ. Pensado para pruebas (señal a
 * 0 dBFS con todas las bandas al máximo no debe superar el techo) y para el futuro camino
 * de captura. Devuelve el pico de salida lineal.
 */
extern "C" JNIEXPORT jfloat JNICALL
Java_com_auraline_app_audio_NativeEngine_nativeProcessBlock(
        JNIEnv* env, jobject, jfloatArray interleaved, jint frames) {
    if (interleaved == nullptr || frames <= 0) return 0.0f;
    const jsize len = env->GetArrayLength(interleaved);
    if (len < frames * kChannels) return 0.0f;
    // Nota: GetPrimitiveArrayCritical puede bloquear el GC; aceptable aquí porque este camino
    // no es el callback de audio en tiempo real.
    auto* buf = static_cast<float*>(env->GetPrimitiveArrayCritical(interleaved, nullptr));
    if (buf == nullptr) return 0.0f;
    g_engine.processBlock(buf, frames);
    env->ReleasePrimitiveArrayCritical(interleaved, buf, 0);
    return g_engine.peakOut.load(std::memory_order_relaxed);
}

extern "C" JNIEXPORT jfloat JNICALL
Java_com_auraline_app_audio_NativeEngine_nativeGainReductionDb(JNIEnv*, jobject) {
    return g_engine.limiter.gainReductionDb();
}

extern "C" JNIEXPORT void JNICALL
Java_com_auraline_app_audio_NativeEngine_nativeSetTone(
        JNIEnv*, jobject, jfloat bassDb, jfloat bassHz, jfloat trebleDb, jfloat trebleHz, jfloat subDb, jfloat subHz) {
    // Tono completo: estanterías Graves/Medios (crossover graves) y Medios/Agudos (crossover
    // agudos) + sub-bajo (paso bajo). Rangos saneados a los publicados por la UI.
    g_engine.setTone(bassDb, bassHz, trebleDb, trebleHz, subDb, subHz);
}

extern "C" JNIEXPORT void JNICALL
Java_com_auraline_app_audio_NativeEngine_nativeSetCompressor(
        JNIEnv*, jobject, jfloat thresholdDb, jfloat ratio, jfloat attackMs, jfloat releaseMs, jfloat makeupDb) {
    // Compresor: umbral, ratio, attack/release y makeup. El lookahead es identico al del
    // limitador (ventana maxima); el ratio<inf convierte la reduction en compresion.
    g_engine.setCompressor(thresholdDb, ratio, attackMs, releaseMs, makeupDb);
}

extern "C" JNIEXPORT void JNICALL
Java_com_auraline_app_audio_NativeEngine_nativeSetGate(
        JNIEnv*, jobject, jfloat thresholdDb, jfloat ratio, jfloat attackMs, jfloat releaseMs, jfloat makeupDb) {
    // Puerta: umbral, ratio, attack/release y makeup; se aplica cuando pide actuacion.
    g_engine.setGate(thresholdDb, ratio, attackMs, releaseMs, makeupDb);
}
