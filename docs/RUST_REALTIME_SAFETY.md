# ⚡ SEGURIDAD EN TIEMPO REAL DEL MOTOR RUST DSP

**Proyecto:** AuraLine  
**Idioma:** Español  

---

## 1. REGLAS INVIOLABLES DEL CALLBACK DE AUDIO

1. **Cero asignaciones dinámicas en el ciclo de renderizado:**
   - Prohibido `Vec::push`, `Box::new` o reallocation en el hilo de audio.
   - Buffers preasignados estáticamente o durante `new(sample_rate)`.
2. **Cero llamadas bloqueantes o Mutex de sistema:**
   - Comunicación de parámetros desde UI mediante atómicos de memoria relajada (`Ordering::Relaxed`).
3. **Control estricto de denormales:**
   - `sanitize_denormal(val)` fuerza a cero cualquier número flotante con magnitud `< 1e-15` para evitar caídas de rendimiento por excepciones de coma flotante en hardware ARM.
4. **Protección contra NaN / Inf:**
   - Todos los parámetros de entrada se limitan con `.clamp()` antes del cálculo de coeficientes.
5. **Cero JNI en el callback:**
   - Las llamadas a Kotlin/Java se realizan exclusivamente desde hilos de control.
   - El callback `process_block` nunca invoca `Java_*`.
6. **Cero I/O en el callback:**
   - No se realizan operaciones de disco, red ni logs en el hilo de audio.
7. **Cero inicialización de módulos:**
   - Todos los módulos se inicializan en `new(sample_rate)`.

---

## 2. EVIDENCIA EN EL CÓDIGO

### 2.1. Buffers preasignados

- `LookaheadTruePeakLimiter::delay_buffer`: `[f32; 256 * 2]` asignado en `new()`.
- `StereoFDNReverb::delay_buffers`: `[[f32; 4800]; 4]` asignado en `new()`.
- `EqualizerCascade::bands`: `[SmoothedBiquad; 25]` asignado en `new()`.

### 2.2. Atómicos para parámetros

- `AtomicBool` para `is_bypass`, `subsonic_enabled`, `enabled`.
- `AtomicF64` (wrapper sobre `AtomicU64`) para `preamp_gain`, `room_size`, `damping`, `mix`, `width`, `threshold_db`, `ratio`.
- Lectura con `Ordering::Relaxed` en el callback (suficiente para audio).

### 2.3. Sanitización de denormales

```rust
#[inline(always)]
pub fn sanitize_denormal(val: f32) -> f32 {
    if val.abs() < 1e-15 { 0.0 } else { val }
}
```

Se aplica en `SmoothedBiquad::process_l/r` y `StereoFDNReverb::process`.

### 2.4. Validación de parámetros

- Frecuencias: `.clamp(10.0, sample_rate * 0.49)` (protección contra Nyquist).
- Q: `.clamp(0.1, 20.0)` (protección contra inestabilidad).
- Gain: `.clamp(-36.0, 36.0)` (protección contra overflow).
- Sample rate: fallback a 48000 Hz si es inválido.

---

## 3. PRUEBAS DE SEGURIDAD

| Prueba | Verifica |
|---|---|
| `test_denormal_sanitization` | Los denormales se convierten a cero. |
| `test_biquad_flat_pass` | Los coeficientes normalizados no producen NaN. |
| `test_lr4_complementary_sum` | El crossover no produce inestabilidad tras 2000 muestras. |
| `test_limiter_no_clipping_at_low_level` | El limitador no activa clipping con señal baja. |
| `test_c_ffi_lifecycle` | El ciclo de vida FFI no produce panics. |
