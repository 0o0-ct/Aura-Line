/**
 * AuraLine AudioWorklet Processor
 * 
 * Bridges the WebAssembly-compiled Rust DSP engine with the Web Audio API.
 * Load this file in an AudioWorkletNode to process audio in the browser.
 * 
 * Usage:
 * ```typescript
 * const audioContext = new AudioContext();
 * const module = await audioContext.audioWorklet.addModule('auraline-processor.js');
 * const node = new AudioWorkletNode(audioContext, 'auraline-processor');
 * node.connect(audioContext.destination);
 * ```
 */

class AuraLineProcessor extends AudioWorkletProcessor {
    constructor() {
        super();
        this.engine = null;
        this.initialized = false;
        this.sampleRate = sampleRate;
        this.port.onmessage = this.handleMessage.bind(this);
    }

    async handleMessage(event) {
        const { type, data } = event.data;

        switch (type) {
            case 'init':
                await this.initEngine(data.sampleRate || this.sampleRate);
                break;
            case 'setPreamp':
                if (this.engine) {
                    this.engine.set_preamp_gain(data.gainDb);
                }
                break;
            case 'setBypass':
                if (this.engine) {
                    this.engine.set_bypass(data.bypass);
                }
                break;
            case 'setEqBand':
                // EQ band setting would go here
                break;
            case 'getTelemetry':
                if (this.engine) {
                    this.port.postMessage({
                        type: 'telemetry',
                        data: this.engine.get_telemetry()
                    });
                }
                break;
        }
    }

    async initEngine(sampleRate) {
        try {
            // Load WASM module
            const wasmModule = await import('./auraline_wasm.js');
            await wasmModule.default();
            this.engine = wasmModule.create_engine(sampleRate);
            this.initialized = true;
            this.port.postMessage({ type: 'ready', data: { sampleRate } });
        } catch (error) {
            console.error('Failed to initialize AuraLine WASM engine:', error);
            this.port.postMessage({ type: 'error', data: { message: error.message } });
        }
    }

    process(inputs, outputs) {
        if (!this.initialized || !this.engine) {
            // Pass through if not initialized
            return true;
        }

        const input = inputs[0];
        const output = outputs[0];

        if (!input || !output) {
            return true;
        }

        const numChannels = input.length;
        const numFrames = input[0].length;

        // Interleave channels for Rust engine
        const interleaved = new Float32Array(numFrames * numChannels);
        for (let i = 0; i < numFrames; i++) {
            for (let ch = 0; ch < numChannels; ch++) {
                interleaved[i * numChannels + ch] = input[ch][i];
            }
        }

        // Process through Rust engine
        this.engine.process_block(interleaved);

        // De-interleave back to Web Audio format
        for (let i = 0; i < numFrames; i++) {
            for (let ch = 0; ch < numChannels; ch++) {
                output[ch][i] = interleaved[i * numChannels + ch];
            }
        }

        return true;
    }
}

registerProcessor('auraline-processor', AuraLineProcessor);
