#!/usr/bin/env bash
# Prueba del motor nativo en el PC (sin Android): compila dsp_engine.cpp con stubs de JNI y
# ejecuta las dos pruebas de aceptación anti-saturación:
#   1) 0 dBFS con 25 bandas a +24 dB  -> el pico de salida no debe superar el techo (-1 dBTP).
#   2) Prueba nula con EQ plano        -> la salida debe ser la entrada retrasada (error < -120 dB).
set -euo pipefail
cd "$(dirname "$0")"
g++ -std=c++17 -O2 -Wall -Wextra -Wno-unused-parameter -Istubs dsp_test.cpp -o /tmp/auraline_dsp_test
/tmp/auraline_dsp_test
