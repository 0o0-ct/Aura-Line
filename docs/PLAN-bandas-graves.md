# Plan — Bandas graves dedicadas a fuentes concretas

**Estado:** implementado en `feature/bandas-graves-instrumentos` (desde `main`).
Compilado y verificado: `:core:domain`, `:core:ui` y `:app:compileDebugKotlin`.

**Objetivo:** que las primeras bandas del ecualizador estén *dedicadas* a fuentes graves concretas
(sub-bajo, bombo, bajo eléctrico, contrabajo, tuba, fagot…) y que el usuario pueda ver y manipular
cada fuente por separado.

**Rama base:** `main` (v4.0.0) → rama nueva `feature/bandas-graves-instrumentos`

---

## 0. Resultado

| | |
|---|---|
| **Etiqueta bajo el fader** | Las 10 bandas graves muestran el instrumento dueño (`50 → SUB`, `141 → BOMBO`, `244 → BAJO`…). |
| **Tarjeta "Fuentes graves"** | Las 15 fuentes con su banda, ganancia en vivo y botón de foco. |
| **Foco de un toque** | Suma la receta sobre tu curva **sin borrarla**; un segundo toque la retira. |
| **Guía de frecuencias** | Ahora incluye el mapa de graves por banda con los instrumentos que la comparten. |
| **Rejilla** | Sin cambios. Ver §1.2. |

---

## 1. Lo que hay hoy (verificado en el código)

### 1.1 La rejilla de bandas

`core/domain/.../EqBand.kt` → `generateEqualizerBands(count, gainRangeDb)`

Para `count = 25`:

| | cortes (Hz) | etiquetas UI (Hz) |
|---|---|---|
| Graves (10) | 100, 200, 300 … 1000 | **50, 141, 244, 346, 447, 547, 648, 748, 848, 948** |
| Agudos (15) | 1221 → 20000 (log) | 1105, 1349, 1648, 2012, 2456 … 18103 |

- Las etiquetas `50 / 141 / 244 / …` son `bandCenterHz()` = media geométrica entre cortes consecutivos.
- La banda `i` cubre el rango `[corte(i-1), corte(i)]`; el filtro del DSP se sitúa en `corte(i)`.
- **Hay exactamente 25 bandas como máximo** (`count.coerceIn(20, 25)`). El selector "Ventana 8 / 12 / Todas"
  solo *divide* esas 25 en páginas, no añade bandas. → La percepción de "más de 25" viene de ver la lista
  larga de frecuencias, no de un conteo real.

### 1.2 El límite físico del graves ⚠️

`EqBand.kt` (comentario del autor, líneas 128-133):

> `DynamicsProcessing` reparte el espectro en bins de FFT (~47 Hz con bloque de 1024 a 48 kHz)
> y descarta en silencio toda banda cuyo rango quede vacío. Por eso la rejilla es lineal hasta
> 1 kHz (pasos de 100 Hz, dos bins) y logarítmica por encima: así ninguna banda queda muerta.

**Consecuencia:** con el motor activo (`DynamicsProcessing`) el espaciado mínimo en graves es
~100 Hz ≈ 2 bins. **No es posible crear 15 bandas de 20-60 Hz** sin que el DSP las mate por
conflicto de bins.

Además, en `main` el motor C++ nativo (`app/src/main/cpp/dsp_engine.cpp`, biquads reales con Q libre)
**no está cableado**: `NativeEngine` no lo invoca nadie. Solo en la rama del PR #5 está integrado.

| Motor | Resolución de graves | ¿Permite banda por instrumento? |
|---|---|---|
| `DynamicsProcessing` (activo en `main`) | ~100 Hz (2 bins de 47 Hz) | No, ~10 bandas en 0-1 kHz |
| Biquad nativo Oboe (PR #5) | Ilimitada (Q y frecuencia libres) | Sí, se puede afinar al fundamental |

### 1.3 El hueco funcional real

Con 15 instrumentos de graves y 10 bandas disponibles en 0-1 kHz, **no cabe 1 banda = 1 instrumento**
de forma honesta: el pedal de órgano (16 Hz), el sub-bajo (20-60 Hz) y la tuba (D1 ≈ 29 Hz) viven en
el mismo 0-100 Hz y ningún ecualizador los separa. Pero sí se puede conseguir lo que el usuario quiere:

1. Que **cada banda tenga un instrumento dueño** (dedicada de verdad).
2. Que los instrumentos que comparten banda queden **listados como acompañantes**.
3. Que cada instrumento tenga un **control de un toque** que lo aísle sobre la curva del usuario.

---

## 2. Decisión de diseño

**Rejilla: NO se toca.** Se mantiene la de `main` (100 Hz en graves) porque:
- Cambiarla con `DynamicsProcessing` rompe el requisito de 2 bins → bandas muertas.
- La alternativa real (motor nativo) es el PR #5, que está abierto y sin fusionar.

**Mapa de fuentes: tabla nueva en `core/domain`, 100 % datos.**

### 2.1 Asignación implementada

| Banda | Dueño dedicado | Acompañantes (mismo rango) | Fundamental |
|---|---|---|---|
| 50 Hz | **Sub-bajo** (trap, EDM, dubstep) | Órgano de tubos · 808 sub-bass · Bombo legüero · Tuba | 20–60 Hz |
| 141 Hz | **Bombo de batería** | Toms de piso · Timbales · Tambora/taiko · Bajo eléctrico | 50–200 Hz |
| 244 Hz | **Bajo eléctrico** | Bajo acústico · Bajo fretless · Guitarrón · Caja 808 | 41–300 Hz |
| 346 Hz | **Contrabajo** | Bajo acústico · Clarinete bajo · Fagot | 41–400 Hz |
| 447 Hz | **Bajo fretless** | Fagot · Minimoog · Bajo acústico | 41–500 Hz |
| 547 Hz | **Guitarrón mexicano** | Minimoog · Tuba · Bajo eléctrico | 40–600 Hz |
| 648 Hz | **Tuba y sousafón** | Minimoog · Bajo eléctrico · Órgano de tubos | 29–700 Hz |
| 748 Hz | **Clarinete bajo y fagot** | Bajo · Bajo fretless | 58–800 Hz |
| 848 Hz | **Minimoog (synth bass)** | Caja 808 · Bajo eléctrico | 32–900 Hz |
| 948 Hz | **Caja de ritmos (TR-808)** | Bajo eléctrico · Contrabajo · Toms de piso | 40–1000 Hz |

Las 15 fuentes de la lista del usuario están cubiertas: 10 son dueños de banda y las otras 5
(órgano de tubos, toms de piso, timbales, tambores tradicionales, bajo acústico) aparecen como
acompañantes y tienen su propia receta de foco.

Referencias de rango verificadas: pedal de órgano 16 Hz · tuba D1 ≈ 29 Hz · fagot y clarinete bajo
Bb1 ≈ 58 Hz · contrabajo/bajo eléctrico E1 ≈ 41 Hz · tímpanos ≈ 65–120 Hz · taiko 45–120 Hz.

> La tabla es **datos**, no lógica: si el usuario prefiere que el 141 Hz sea del contrabajo y no del bombo,
> se cambia una línea de `LowFrequencyMap.kt`.

### 2.2 Lo que ve el usuario

1. **Etiqueta bajo cada fader** (solo en las 10 bandas graves): el nombre corto del dueño.
   `50 → SUB`, `141 → BOMBO`, `244 → BAJO`, `346 → CONTRA`, `447 → FLUIDO`, `547 → GUITAR`,
   `648 → TUBA`, `748 → VIENTO`, `848 → SYNTH`, `948 → 808`.
2. **Tarjeta nueva "Fuentes graves"**: las 15 fuentes con su banda, su rango de fundamental, su ganancia
   **en vivo** y un botón de foco.
3. **Foco aditivo de un toque**: pulsar una fuente sube su banda(es) **sin borrar el resto de la curva**
   (hoy `applyFactoryPreset` *reemplaza* la curva → hace falta un camino nuevo). Un segundo toque lo retira.
4. **Guía de frecuencias** ampliada con el mapa de fuentes.

---

## 3. Cambios concretos

### 3.1 `core/domain` — nuevo `LowFrequencyMap.kt` ✅
```kotlin
data class LowSource(
    val key: String,            // "kick_drum"
    val name: String,           // "Bombo de batería"
    val shortName: String,      // "BOMBO"  (etiqueta del fader)
    val category: SourceCategory, // PERCUSION | ELECTRONIC | STRING | WIND
    val fundamentalHz: IntRange, // 50..200
    val detail: String,         // "Golpe seco y contundente..."
    val tip: String             // "+3 dB en 141 da cuerpo; corta 244 si suena cartón"
)

data class BandRole(
    val centerWindow: IntRange,  // ventana de frecuencia CENTRO donde aplica
    val owner: LowSource,
    val companions: List<LowSource>
)

data class FocusStep(val centerHz: Int, val deltaDb: Float)
data class SourceFocus(val source: LowSource, val steps: List<FocusStep>)
```
- `LowFrequencySources`: 15 fuentes. `LowFrequencyRoles`: 10 bandas.
- El mapa se define por **ventana de frecuencia centro**, no por índice de banda → sigue siendo
  válido con 20, 22 o 25 bandas (verificado: con 20 bandas las 8 graves conservan su dueño correcto).
- `resolveFocus(bands, focus)` resuelve cada paso contra las bandas reales (la más cercana) y
  descarta los objetivos que no existen en esa rejilla.

### 3.2 `core/domain/AudioRepository.kt` + `core/data/AudioRepositoryImpl.kt` ✅
```kotlin
fun focusLowSource(key: String, scale: Float = 1f): Boolean
```
- Suma la receta sobre la curva actual; **no** la sustituye (a diferencia de `applyFactoryPreset`).
- Alterna: si la banda principal ya está subida al menos tanto como la receta, la resta.
- Respeta `gainRangeDb` como tope y marca el estado como `Personalizado`.

### 3.3 `app/.../AuraLineViewModel.kt` ✅
```kotlin
fun focusLowSource(key: String) = repository.focusLowSource(key)
```

### 3.4 `core/ui/.../AuraComponents.kt` ✅
- `BandFader` → nuevo parámetro `subtitle: String = ""` (8 sp, `textSecondary`, una línea).
  Los nombres cortos caben en 34 dp: SUB · BOMBO · BAJO · CONTRA · FLUIDO · GUITAR · TUBA ·
  VIENTO · SYNTH · 808.

### 3.5 `app/.../EqualizerScreen.kt` ✅
- Subtítulo en los dos caminos de faders (vista "Todas" y `BandPage`).
- Tarjeta **"Fuentes graves"** entre "Bandas" y "Ganancia de entrada".
- Diálogo "Guía": sección "Mapa de graves por banda" antes de la guía de frecuencias.

### 3.6 Arreglo colateral: `gradle/wrapper/gradle-wrapper.jar` ⚠️

En `main` el JAR del wrapper está **corrupto**: son 554 bytes de HTML en vez de un ZIP válido
(lo dejó una descarga fallida). Se ha sustituido por el JAR real de Gradle 8.10 y se ha añadido
`validateDistributionUrl=true` a `gradle-wrapper.properties`, igual que en el PR #5.

> Ojo: `main` usa un `gradlew` a medida que espera un Gradle **vendorizado** en
> `gradle/dist/gradle-8.10` (ignorado por git) y un JDK en `jdk/` o en el JBR de Android Studio.
> Sin ese toolchain el proyecto no arranca; el JAR del wrapper por sí solo no basta.

---

## 4. Fuera de alcance (consciente)

- **No se cambia la rejilla de frecuencias.** Requiere el motor nativo (PR #5).
- **No se tocan `ToneScreen`, `EffectsScreen`, `SettingsScreen`** ni el motor de audio.
- **No se cambia el motor activo** (`DynamicsProcessing` sigue siendo el de `main`).

## 5. Fase 2 (cuando se fusione el PR #5)

Con el motor biquad nativo (Q libre, frecuencia libre) se puede:
1. Bajar la rejilla de graves a 25/40/60/80/100/125/160/200/250/320 Hz (10 bandas, una por instrumento).
2. Convertir la tabla de 15 instrumentos en 15 bandas 1:1 reales.
3. Dejar `LowFrequencyRoles` como la definición canónica: solo cambia la tabla de frecuencias.

## 6. Verificación

- `gradlew :app:assembleDebug` compila (JDK 17-23, Android SDK 35, NDK 26.1).
- En dispositivo: las 10 bandas graves muestran su dueño; el foco de una fuente suma ganancia
  **solo** en su banda y no altera las demás; el rango ±6/12/24 se respeta.
- La tarjeta "Fuentes graves" refleja la ganancia en vivo al mover un fader.

## 7. Publicación

`main` no tiene credenciales de push en este entorno. Para subir:
- Opción A: guardar un PAT (permiso `repo`) como secreto `GITHUB_TOKEN` y configurar el remote → push + PR directo.
- Opción B: se entrega el diff (`git format-patch`) y el usuario lo aplica.
