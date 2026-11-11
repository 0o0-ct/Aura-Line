# 📜 CONTRATO API C FFI Y JNI DEL MOTOR RUST

**Proyecto:** AuraLine  
**Crate:** `auraline-android`  
**Idioma:** Español  

---

## 1. FUNCIONES C FFI EXPORTADAS (`repr(C)`)

```c
// Creacion y destruccion del motor
AuraLineCoreEngine* auraline_engine_create(double sample_rate);
void auraline_engine_destroy(AuraLineCoreEngine* engine);

// Procesamiento en tiempo real por bloque
void auraline_engine_process(AuraLineCoreEngine* engine, float* buffer, size_t length);

// Control de parametros
void auraline_engine_set_bypass(AuraLineCoreEngine* engine, bool bypass);
void auraline_engine_set_preamp_gain(AuraLineCoreEngine* engine, float gain_db);
```

---

## 2. BINDINGS JNI (`com.auraline.app.audio.NativeEngine`)

- `init(sampleRate: Int)`
- `setBypass(bypass: Boolean)`
- `setGeneratorMode(mode: Int)`
- `setGeneratorLevel(level: Float)`
- `setPreampGain(gainDb: Float)`
- `setEqBand(bandIndex: Int, freq: Float, gain: Float, q: Float)`
- `setSubsonic(enabled: Boolean, cutoffHz: Float)`
- `setInstrumentEnhancer(enabled: Boolean, fundamentalHz: Float, bodyDb: Float, punchDb: Float, mudCutDb: Float, harmonics: Float)`
- `setCompressor(enabled: Boolean, thresholdDb: Float, ratio: Float, attackMs: Float, releaseMs: Float, makeupGainDb: Float)`
- `setNoiseGate(enabled: Boolean, thresholdDb: Float, ratio: Float, attackMs: Float, releaseMs: Float)`
- `setReverb(enabled: Boolean, roomSize: Float, damping: Float, decay: Float, predelay: Float, mix: Float)`
- `setStereoWidener(enabled: Boolean, width: Float, bassMonoCutoffHz: Float)`
- `setLimiter(enabled: Boolean, ceilingDb: Float)`
- `setBluetoothRoute(isBluetooth: Boolean)`
- `writeInputPcm(samples: FloatArray, offset: Int, length: Int): Int`
- `getTelemetry(outTelemetry: FloatArray)`
