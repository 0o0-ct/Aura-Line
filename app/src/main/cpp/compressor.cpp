#include <cmath>
#include <algorithm>

// Multiband Compressor (Skeleton integration ready for Linkwitz-Riley crossovers)

class RMSDetector {
private:
    float envelope = 0.0f;
    float attack;
    float release;
public:
    void setTimes(float attackMs, float releaseMs, float sampleRate) {
        attack = std::exp(-1.0f / (attackMs * 0.001f * sampleRate));
        release = std::exp(-1.0f / (releaseMs * 0.001f * sampleRate));
    }

    float process(float in) {
        float inSquared = in * in;
        if (inSquared > envelope) {
            envelope = attack * (envelope - inSquared) + inSquared;
        } else {
            envelope = release * (envelope - inSquared) + inSquared;
        }
        return std::sqrt(std::max(envelope, 1e-6f));
    }
};

class MultibandCompressor {
public:
    float threshold = -20.0f; // dB
    float ratio = 4.0f;
    float makeupGain = 0.0f; // dB

    RMSDetector detectorL;
    RMSDetector detectorR;

    void init(float sampleRate) {
        detectorL.setTimes(10.0f, 100.0f, sampleRate);
        detectorR.setTimes(10.0f, 100.0f, sampleRate);
    }

    void process(float* buffer, int numFrames) {
        // En una implementación real, aquí dividiríamos la señal en bandas con Linkwitz-Riley,
        // aplicaríamos la compresión a cada banda usando los detectores RMS,
        // y luego sumaríamos las bandas.

        // Por ahora, actuamos como un compresor de banda ancha simple
        float thresholdLin = std::pow(10.0f, threshold / 20.0f);
        float makeupLin = std::pow(10.0f, makeupGain / 20.0f);

        for (int i = 0; i < numFrames; ++i) {
            float rmsL = detectorL.process(buffer[2*i]);
            float rmsR = detectorR.process(buffer[2*i+1]);

            float gainL = 1.0f;
            if (rmsL > thresholdLin) {
                float dbOver = 20.0f * std::log10(rmsL / thresholdLin);
                float dbReduction = dbOver * (1.0f - 1.0f / ratio);
                gainL = std::pow(10.0f, -dbReduction / 20.0f);
            }

            float gainR = 1.0f;
            if (rmsR > thresholdLin) {
                float dbOver = 20.0f * std::log10(rmsR / thresholdLin);
                float dbReduction = dbOver * (1.0f - 1.0f / ratio);
                gainR = std::pow(10.0f, -dbReduction / 20.0f);
            }

            buffer[2*i] *= gainL * makeupLin;
            buffer[2*i+1] *= gainR * makeupLin;
        }
    }
};
