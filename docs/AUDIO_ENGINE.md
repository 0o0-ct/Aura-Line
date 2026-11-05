# ESPECIFICACIÓN TÉCNICA DEL MOTOR DE AUDIO DSP
**Proyecto Aura-Line — Documento de Arquitectura y Cadena de Señal**

---

## 1. Arquitectura de Doble Capa (Hybrid Dual Engine)

Aura-Line implementa una arquitectura híbrida desacoplada gobernada por un único estado reactivo:

```
                          ┌───────────────────────────────────────────────┐
                          │   Jetpack Compose UI / AuraLineViewModel      │
                          │   (EqualizerState reactivo via StateFlow)     │
                          └──────────────────────┬────────────────────────┘
                                                 │
                                     Parámetros DSP sincronizados
                                                 │
                        ┌────────────────────────┴────────────────────────┐
                        ▼                                                 ▼
        ┌───────────────────────────────┐                 ┌───────────────────────────────┐
        │   MODALIDAD A:                │                 │   MODALIDAD B:                │
        │   AudioEffect / Framework     │                 │   Motor Nativo C++ / Oboe     │
        │   (DynamicsProcessing + EQ)   │                 │   (AudioPlaybackCapture /     │
        │                               │                 │    Generador Interno A/B)     │
        ├───────────────────────────────┤                 ├───────────────────────────────┤
        │ • Escucha sesiones abiertas   │                 │ • Procesamiento C++ real-time │
        │   por Spotify, Poweramp, etc. │                 │ • Oboe salida de baja latencia│
        │ • Configura PreEq, MBC,       │                 │ • Generador: Rosa/Blanco/Sweep│
        │   PostEq y Limiter.           │                 │ • True-Peak Limiter, FDN      │
        │ • Sesión 0 con verificación   │                 │   Reverb, Linkwitz-Riley LR4  │
        │   de hasControl().            │                 │ • Captura PCM via Projection  │
        └───────────────┬───────────────┘                 └───────────────┬───────────────┘
                        │                                                 │
                        ▼                                                 ▼
        ┌─────────────────────────────────────────────────────────────────────────────────┐
        │                 Gestor de Rutas y Salida de Audio (AudioDeviceCallback)         │
        │   Bluetooth A2DP / LE Audio ── Auriculares Cable ── USB DAC ── Altavoz          │
        └─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Diagrama de la Cadena de Señal DSP Unificada

```
[ Fuente de Entrada: Sesión de App / Captura PCM / Generador Interno ]
                            │
                            ▼
              [ 1. Ganancia de Entrada (Preamp) ]
               + Compensación Auto-Headroom (-36 dB .. 0 dB)
                            │
                            ▼
           [ 2. Filtro Subsónico / DC Blocker ]
               Linkwitz-Riley 4.º orden (Corte: 20–40 Hz)
                            │
                            ▼
           [ 3. Ecualizador de 25 Bandas (Biquad Peaking) ]
               Fórmulas RBJ, Factor Q proporcional, Suavizado
                            │
                            ▼
           [ 4. Sección de Tono y Crossovers Linkwitz-Riley (LR4) ]
               División en 3 Bandas: Graves (<f1) | Medios (f1..f2) | Agudos (>f2)
                            │
                            ▼
           [ 5. Realzador de Graves (15 Perfiles Acústicos) ]
               Armónicos, Cuerpo, Definición, Mud-Cut según instrumento
                            │
                            ▼
           [ 6. Procesadores Dinámicos ]
               ├─ Puerta de Ruido y Expansor (Detección de envolvente suave)
               ├─ Compresor Multibanda 3-Bandas (LR4 Crossover Feed)
               └─ Compresor Principal (Threshold, Ratio, Attack, Release, Makeup)
                            │
                            ▼
           [ 7. Procesamiento Espacial ]
               ├─ Reverb FDN Estéreo (Damping, Size, PreDelay, Mix)
               └─ Ampliador Estéreo Mid/Side (M/S) con Bass Mono (<120 Hz)
                            │
                            ▼
           [ 8. Limitador True-Peak de Salida (Lookahead) ]
               Techo: -1.0 dBFS (Cable) / -1.5 dBFS (Bluetooth)
                            │
                            ▼
              [ 9. Ganancia de Salida y Rampa Fade-in ]
                            │
                            ▼
           [ 10. Salida de Audio Física (A2DP / LE / DAC / Altavoz) ]
```

---

## 3. Tabla de Diagnóstico Técnico

| Efecto / Módulo | Causa Raíz del Fallo Inicial | Solución Implementada | Mecanismo de Fallback |
| :--- | :--- | :--- | :--- |
| **Cadena Oboe C++** | El callback `onAudioReady` ejecutaba `memset(..., 0)` llenando el buffer con ceros absolutos sin audio. | Implementación de generador interno multiseñal (Rosa/Blanco/Barrido) y motor de procesamiento de PCM entrante. | Si Oboe falla en abrir stream, recurre a `AudioTrack` en modo estéreo flotante. |
| **DynamicsProcessing** | Sesión 0 bloqueada en Android 10+ en muchos fabricantes (Samsung One UI); pérdida silenciosa de control con prioridad 0. | Receptor dinámico de `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` (cadenas canónicas) y `EffectPanelActivity`, enlazando un `SessionNode` completo con prioridad 100 (`EFFECT_PRIORITY = 100`). | Si el reproductor no emite broadcast, se mantiene sesión 0 documentando en UI la necesidad de activar "Ecualizador" en el reproductor. |
| **Headroom en Graves** | Ganancia acumulada de graves (+24 dB) con atenuación fija insuficiente capada a 6 dB produciendo *hard clipping*. | Cálculo dinámico del pico acumulado real y atenuación de entrada proporcional con 2.0 dB de margen True-Peak. | Limitador Brickwall con ataque de 1.0 ms y ratio 50:1. |
| **Reverberación** | `EnvironmentalReverb` en sesión 0 requiere `setAuxEffectSendLevel` en la pista emisora, siendo 0 para apps ajenas. | Creación dinámica de `EnvironmentalReverb` directamente en la sesión externa (`SessionNode`) y algoritmo nativo de reverberación FDN en C++. | En modo sesión externa, creación directa sobre la sesión del reproductor con prioridad 100. |
| **Ampliación Estéreo** | `Virtualizer` del sistema desactivado por el SO en altavoces y amarrado a sesión 0 en versiones previas. | `Virtualizer` instanciado por sesión externa en `SessionNode` y matriz Mid/Side (M/S) matemática nativa con filtro *Bass Mono* (< 120 Hz). | En modo framework, `Virtualizer` solo se aplica en auriculares cableados y Bluetooth. |
| **Bluetooth A2DP/LE** | Desfase y distorsión al cambiar el sample rate de 48 kHz a 44.1 kHz o LDAC 96 kHz. | Detección dinámica de cambio de `PROPERTY_OUTPUT_SAMPLE_RATE` y reconfiguración limpia de los filtros biquad. | Reconstrucción automática del grafo `DynamicsProcessing`. |
| **Crossovers** | Cruces con filtros simples desfasados generaban picos o valles de $\pm 3$ dB en la recombinación. | Filtros divisores Linkwitz-Riley de 4.º orden (LR4, 24 dB/octava) con suma de potencia acústica perfectamente plana. | Mapeo en las frecuencias de corte de las 3 bandas del `MBC`. |

---

## 4. Fundamentos Matemáticos de los Filtros y Algoritmos

### 4.1. Filtros Biquad Peaking EQ (Robert Bristow-Johnson Audio EQ Cookbook)
Para cada banda con frecuencia central $f_0$, ganancia $G$ en dB y factor de calidad $Q$:
$$A = 10^{\frac{G}{40}}$$
$$\omega_0 = 2\pi \frac{f_0}{f_s}, \quad \alpha = \frac{\sin(\omega_0)}{2Q}$$
Coeficientes de la función de transferencia $H(z) = \frac{b_0 + b_1 z^{-1} + b_2 z^{-2}}{a_0 + a_1 z^{-1} + a_2 z^{-2}}$:
$$b_0 = 1 + \alpha A, \quad b_1 = -2\cos(\omega_0), \quad b_2 = 1 - \alpha A$$
$$a_0 = 1 + \frac{\alpha}{A}, \quad a_1 = -2\cos(\omega_0), \quad a_2 = 1 - \frac{\alpha}{A}$$
Normalizando dividiendo entre $a_0$:
$$y[n] = \frac{b_0}{a_0}x[n] + \frac{b_1}{a_0}x[n-1] + \frac{b_2}{a_0}x[n-2] - \frac{a_1}{a_0}y[n-1] - \frac{a_2}{a_0}y[n-2]$$

### 4.2. Crossovers Linkwitz-Riley de 4.º Orden (LR4, 24 dB/octava)
Un filtro LR4 se compone de dos filtros Butterworth de 2.º orden en cascada:
$$H_{LR4\_LP}(s) = \left( \frac{\omega_c^2}{s^2 + \sqrt{2}\omega_c s + \omega_c^2} \right)^2$$
$$H_{LR4\_HP}(s) = \left( \frac{s^2}{s^2 + \sqrt{2}\omega_c s + \omega_c^2} \right)^2$$
Propiedad crítica:
$$|H_{LR4\_LP}(j\omega) + H_{LR4\_HP}(j\omega)|^2 = 1 \quad \forall \omega$$
La suma en magnitud es estrictamente plana (0 dB en la frecuencia de cruce), y la diferencia de fase relativa entre ambas ramas es exactamente $360^\circ$ ($0^\circ$ módulo $2\pi$), eliminando cancelaciones de fase al recombinar el espectro.

### 4.3. Procesamiento Espacial Mid/Side con Bass Mono
Transformación a dominio suma/diferencia:
$$M = \frac{L + R}{2}, \quad S = \frac{L - R}{2}$$
Para evitar pérdida de potencia en graves y garantizar compatibilidad mono, el componente Side ($S$) se filtra con un paso-altos a 120 Hz ($S_{filtrado} = HPF_{120}(S)$), de modo que por debajo de 120 Hz la señal sea estrictamente monofónica (en fase).
La ampliación se aplica como:
$$S_{mod} = S_{filtrado} \times \text{Width}$$
Reconstrucción a estéreo L/R:
$$L_{out} = M + S_{mod}, \quad R_{out} = M - S_{mod}$$
