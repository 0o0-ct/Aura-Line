# ⏱️ LATENCIA Y PERFORMANCE DEL MOTOR RUST

**Proyecto:** AuraLine  
**Idioma:** Español  

---

## 1. DESGLOSE HONESTO DE LATENCIA

1. **Latencia DSP Rust Callback:**
   - Lookahead buffer: 256 muestras / 48000 Hz = **5.33 ms**.
   - Tiempo de cálculo por bloque: **0.005 ms** (negligible).
2. **Latencia del Stream Oboe (AAudio Low Latency Exclusive):**
   - Burst size: 192 muestras = **4.0 ms**.
3. **Latencia total estimada del motor nativo:** **~9.33 ms** round-trip interno.

---

## 2. MÉTRICAS DIFERENCIADAS

Según las directrices del proyecto, se diferencian claramente las siguientes métricas:

| Métrica | Valor | Medida o Estimada |
|---|---|---|
| Tiempo de procesamiento DSP | ~0.005ms por bloque | Estimada (escalar) |
| Latencia lookahead del limitador | 5.33ms (256 muestras a 48kHz) | Calculada |
| Latencia del callback Oboe/AAudio | 4.0ms (burst size 192) | Estimada |
| Latencia de captura | No medida | Requiere hardware de loopback |
| Latencia de buffers | Variable según dispositivo | Depende de OEM |
| Latencia del framework Android | 10-50ms | Depende de ruta de audio |
| Latencia del códec Bluetooth | 100-300ms | Depende de códec (SBC/AAC/LDAC) |
| Latencia round-trip total | No medida | Requiere hardware de loopback |

**Nota importante:** La latencia round-trip no se ha medido porque no existe hardware de loopback disponible en el entorno de desarrollo actual. No se inventan cifras.

---

## 3. FACTORES QUE AFECTAN LA LATENCIA

### 3.1. Ruta de audio
- **Altavoz integrado:** Latencia framework ~10-20ms.
- **Auriculares cableados:** Latencia framework ~5-15ms.
- **USB DAC:** Latencia variable según dispositivo (~5-30ms).
- **Bluetooth A2DP:** Latencia de códec 100-300ms adicional.

### 3.2. Modo de integración
- **Framework Session:** Latencia determinada por Android AudioEffect.
- **Rust Owned PCM:** Latencia = Oboe burst + lookahead + DSP.
- **System Effects HAL:** Latencia determinada por AudioFlinger.

### 3.3. Método de medición planificado
Para medir la latencia round-trip real se requiere:
1. Hardware de loopback acústico o eléctrico.
2. Generador de impulso desde AuraLine.
3. Captura del impulso procesado.
4. Análisis de la diferencia temporal.

Esto se ejecutará durante la **Fase 7 (Pruebas de Hardware)**.
