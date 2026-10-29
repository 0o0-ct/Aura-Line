#include <cmath>
#include <vector>

// Feedback Delay Network (FDN) Reverb - Skeleton
// Parameters: damping, room size, mix

class DelayLine {
private:
    std::vector<float> buffer;
    int writeIndex = 0;
public:
    void init(int length) {
        buffer.assign(length, 0.0f);
        writeIndex = 0;
    }

    float read(int delaySamples) {
        int readIndex = writeIndex - delaySamples;
        if (readIndex < 0) readIndex += buffer.size();
        return buffer[readIndex];
    }

    void write(float sample) {
        buffer[writeIndex] = sample;
        writeIndex = (writeIndex + 1) % buffer.size();
    }
};

class FDNReverb {
public:
    float roomSize = 0.8f;
    float damping = 0.5f;
    float mix = 0.3f;

    DelayLine delays[4];
    float lastOutputs[4] = {0.0f};

    void init(float sampleRate) {
        // Prime delays with relatively prime lengths for modal density
        delays[0].init(sampleRate * 0.037f);
        delays[1].init(sampleRate * 0.041f);
        delays[2].init(sampleRate * 0.043f);
        delays[3].init(sampleRate * 0.047f);
    }

    void process(float* buffer, int numFrames) {
        // Simple Hadamard matrix feedback for 4 delay lines
        const float g = 0.5f; // Feedback matrix gain (1/sqrt(N))

        for (int i = 0; i < numFrames; ++i) {
            float inL = buffer[2*i];
            float inR = buffer[2*i+1];
            float monoIn = (inL + inR) * 0.5f;

            // Read from delays
            float d0 = delays[0].read(delays[0].buffer.size() - 1);
            float d1 = delays[1].read(delays[1].buffer.size() - 1);
            float d2 = delays[2].read(delays[2].buffer.size() - 1);
            float d3 = delays[3].read(delays[3].buffer.size() - 1);

            // Simple lowpass damping (one pole)
            d0 = lastOutputs[0] + (d0 - lastOutputs[0]) * (1.0f - damping);
            d1 = lastOutputs[1] + (d1 - lastOutputs[1]) * (1.0f - damping);
            d2 = lastOutputs[2] + (d2 - lastOutputs[2]) * (1.0f - damping);
            d3 = lastOutputs[3] + (d3 - lastOutputs[3]) * (1.0f - damping);

            lastOutputs[0] = d0; lastOutputs[1] = d1;
            lastOutputs[2] = d2; lastOutputs[3] = d3;

            // Hadamard mix & write back
            float f0 = ( d0 + d1 + d2 + d3) * g;
            float f1 = ( d0 - d1 + d2 - d3) * g;
            float f2 = ( d0 + d1 - d2 - d3) * g;
            float f3 = ( d0 - d1 - d2 + d3) * g;

            delays[0].write(monoIn + f0 * roomSize);
            delays[1].write(monoIn + f1 * roomSize);
            delays[2].write(monoIn + f2 * roomSize);
            delays[3].write(monoIn + f3 * roomSize);

            // Output tap
            float outL = d0 + d2;
            float outR = d1 + d3;

            buffer[2*i] = inL * (1.0f - mix) + outL * mix;
            buffer[2*i+1] = inR * (1.0f - mix) + outR * mix;
        }
    }
};
