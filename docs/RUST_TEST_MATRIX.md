# 🧪 MATRIZ DE PRUEBAS DEL MOTOR RUST

**Proyecto:** AuraLine  
**Idioma:** Español  

---

## 1. COBERTURA DE PRUEBAS EN RUST (`cargo test`)

| Módulo | Pruebas Matemáticas Ejecutadas | Resultado |
|---|---|---|
| `biquad` | Estabilidad de polos, pase plano en 0 dB, sanitización de denormales | **PASADO** |
| `crossover` | Reconstrucción suma complementaria LR4 (2000 muestras) | **PASADO** |
| `auraline-core` | Tubería completa del motor `AuraLineCoreEngine` | **PASADO** |
| `auraline-android` | Ciclo de vida C FFI (`create`, `process`, `destroy`) | **PASADO** |
| `limiter::true_peak` | Latencia lookahead (256 muestras), sin clipping a bajo nivel, reducción de ganancia | **PASADO** |

---

## 2. PRUEBAS PENDIENTES (Fases futuras)

| Módulo | Prueba | Estado |
|---|---|---|
| `biquad` | Respuesta en frecuencia completa (sweep) | Pendiente Fase 2 |
| `biquad` | Frecuencias extremas (cercanas a Nyquist) | Pendiente Fase 2 |
| `biquad` | Q extremo (0.1 y 20.0) | Pendiente Fase 2 |
| `biquad` | Cambios de parámetros sin discontinuidades | Pendiente Fase 2 |
| `crossover` | Respuesta de fase completa | Pendiente Fase 2 |
| `crossover` | Impulso y barrido | Pendiente Fase 2 |
| `compressor` | Threshold, ratio, attack, release, knee, makeup | Pendiente Fase 2 |
| `gate` | Threshold, ratio, attack, release | Pendiente Fase 2 |
| `limiter` | Sample peak vs true peak | Pendiente Fase 2 |
| `limiter` | Oversampling polifásico | Pendiente Fase 2 |
| `limiter` | Transitorios | Pendiente Fase 2 |
| `reverb` | Dry/wet, cola, decay, estabilidad | Pendiente Fase 2 |
| `reverb` | Respuesta estéreo | Pendiente Fase 2 |
| `widener` | Bass Mono protección | Pendiente Fase 2 |
| `engine` | Concurrencia y cambios de parámetros durante reproducción | Pendiente Fase 3 |
| `engine` | Stress testing (1M+ muestras) | Pendiente Fase 6 |
| `FFI` | Sanitizers (ASan, MSan) en host | Pendiente Fase 3 |

---

## 3. EJECUCIÓN

```bash
cd /home/c0o0c/Desktop/AuraLine/rust
cargo test
```

**Resultado actual:** 7 pruebas pasadas, 0 fallidas.
