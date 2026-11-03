# PLAN DE PRUEBAS MANUALES EN HARDWARE REAL (MANUAL_TEST_PLAN.md)
**Proyecto Aura-Line — Guía de Validación Física con Auriculares y Dispositivos Android**

---

## 1. Preparación del Entorno
- **Dispositivo**: Teléfono Android con Android 9.0 o superior (Android 11, 12, 13, 14 o 15 recomendado).
- **Herramientas**: Cable USB con depuración USB activa, Android Studio / terminal con `adb`.
- **Accesorios**:
  1. Auriculares Bluetooth (A2DP / SBC / AAC).
  2. Auriculares por cable de 3.5 mm o adaptador USB-C DAC.
- **Instalación del APK Debug**:
  ```bash
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```
- **Monitoreo general de logs**:
  ```bash
  adb logcat -v time -s AuraLine AuraLineDSP_Native AudioForegroundService AudioEffectSessionReceiver
  ```

---

## 2. Las 10 Pruebas Físicas Obligatorias

### Prueba 1: Motor DSP y Generador Interno A/B (Prueba Auditiva Inmediata)
1. Abrir Aura-Line y encender el interruptor principal del motor.
2. Ir a la pestaña **Diagnóstico**.
3. En la sección "Generador de Prueba", seleccionar **Ruido Rosa** o **Barrido Senoidal**.
4. Ajustar el volumen a nivel moderado.
5. Presionar repetidamente el botón **A/B Bypass**.
6. **Resultado esperado**:
   - Con Bypass DESACTIVADO se escucha la señal filtrada con la ecualización y efectos activos.
   - Con Bypass ACTIVADO se escucha la señal limpia sin procesar.
   - El cambio debe ser instantáneo, perceptible y sin clics ni cortes abruptos.

### Prueba 2: Verificación de las 25 Bandas del Ecualizador
1. Con el generador en **Ruido Rosa**, ir a la pestaña **Ecualizador**.
2. Seleccionar resolución de **25 bandas** y rango $\pm 12$ dB.
3. Subir individualmente al máximo (+12 dB) y bajar al mínimo (-12 dB) las siguientes bandas clave:
   - 31 Hz (sub-graves profundos)
   - 125 Hz (graves de pegada)
   - 1.000 Hz (medios vocales)
   - 4.000 Hz (presencia y brillo)
   - 16.000 Hz (aire y sibilancia aguda)
4. **Resultado esperado**: Cada fader modifica claramente la energía espectral de su región respectiva en el oído, y el analizador de espectro de la UI refleja la modificación en tiempo real.

### Prueba 3: 15 Perfiles Acústicos de Instrumentos Graves
1. Ir a la pestaña **Tono** o tarjeta de Fuentes Graves en **Ecualizador**.
2. Conmutar entre los perfiles: *Bombo*, *Bajo eléctrico*, *808 Sub*, *Contrabajo*, *Órgano de pedal*.
3. **Resultado esperado**:
   - *808 Sub* concentra la energía limpia en 35–55 Hz sin saturar.
   - *Bombo* resalta la pegada en 60 Hz y 2.5 kHz con recorte en 400 Hz.
   - *Bajo eléctrico* define el gruñido en 800 Hz–1.2 kHz.

### Prueba 4: Sección de Tono y Crossovers Linkwitz-Riley
1. En la pestaña **Tono**, mover los controles de Sub-bajo, Graves, Medios y Agudos.
2. Modificar la frecuencia de cruce Graves/Medios (ej. de 100 Hz a 250 Hz).
3. **Resultado esperado**: La transición entre bandas es completamente suave, sin cancelaciones de fase ni caídas de volumen acústico en la frecuencia de cruce.

### Prueba 5: Suite de Efectos Dinámicos y Espaciales
1. En la pestaña **Efectos**:
   - **Compresor**: Bajar el umbral a -30 dB con ratio 4:1 y subir la ganancia de compensación. La señal debe sonar con volumen uniforme y mayor pegada sin romper el techo.
   - **Reverb**: Subir la mezcla a 50% y tamaño de sala al 80%. Debe percibirse una cola espacial rica y tridimensional sin acoples metálicos.
   - **Ampliación Estéreo**: Aumentar la amplitud al 150%. El campo estéreo se ensancha perceptiblemente mientras que el bombo/bajo central se mantiene firme y sin cancelaciones gracias a la protección *Bass Mono*.

### Prueba 6: Enrutamiento y Protección Bluetooth A2DP / LE Audio
1. Conectar auriculares Bluetooth.
2. Verificar en la cabecera o en Diagnóstico que la ruta indique `Bluetooth` con el nombre del dispositivo.
3. En la pestaña **Ajustes**, asegurarse de que "Protección de nivel" esté activada.
4. Aplicar un realce agresivo de graves (+15 dB en Sub y Graves).
5. **Resultado esperado**: El limitador y el auto-headroom aplican la atenuación preventiva y el techo en -1.5 dBFS. El audio suena potente en graves sin producir ningún sonido chillón, rasposo ni saturación digital.
6. **Comando de log**:
   ```bash
   adb logcat -d | grep -E "AuraLine.*(Bluetooth|headroom|sampleRate)"
   ```

### Prueba 7: Integración con Aplicaciones Externas (Spotify / Reproductores)
1. Abrir Spotify o un reproductor compatible (Poweramp, Musicolet, VLC).
2. Iniciar reproducción musical.
3. Volver a Aura-Line. Verificar en **Diagnóstico** que aparezca una sesión activa (`audioSessionId > 0`).
4. Mover el fader de graves o cambiar de preset (ej. de *Plano* a *Rock*).
5. **Resultado esperado**: El sonido de la música cambia inmediatamente con la curva seleccionada.
6. Si la app no emite broadcast de sesión, activar el modo opcional **AudioPlaybackCapture** en Ajustes y autorizar el diálogo del sistema.

### Prueba 8: Conexión y Desconexión Bluetooth en Caliente (Hotplug)
1. Reproducir audio a través del altavoz integrado del teléfono.
2. Conectar los auriculares Bluetooth mientras el audio suena.
3. **Resultado esperado**: El motor detecta el cambio de dispositivo mediante `AudioDeviceCallback`, comprueba si varió la frecuencia de muestreo de salida (48 kHz vs 44.1 kHz) y reconstruye la cadena sin congelar la app ni producir ruidos molestos.
4. Desconectar el Bluetooth: la app conmuta de vuelta al altavoz sin cierres forzados.

### Prueba 9: Estabilidad en Cambios Rápidos de Parámetros
1. Deslizar rápidamente varios faders simultáneamente y alternar entre los 15 presets de fábrica a alta velocidad.
2. **Resultado esperado**: Gracias al agrupamiento de eventos y al suavizado de coeficientes en C++, no se generan clics acústicos (*zipper noise*), la UI mantiene 60 fps y no ocurre ningún ANR (*Application Not Responding*).

### Prueba 10: Consumo de Batería, Memoria y Comportamiento en Segundo Plano
1. Dejar la aplicación funcionando con música en segundo plano y apagar la pantalla durante 15 minutos.
2. **Resultado esperado**: El servicio en primer plano `AudioForegroundService` mantiene vivo el motor con consumo mínimo de CPU (< 3% promedio), la notificación persistente permite apagar o abrir la app, y el sistema operativo no mata el proceso en reposo.
3. **Comando de log**:
   ```bash
   adb shell dumpsys cpuinfo | grep com.auraline.app
   ```
