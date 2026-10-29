cmake_minimum_required(VERSION 3.22.1)
project("auralinedsp" LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Oboe se enlaza ya para el futuro camino de salida propia (captura + render), pero el motor
# actual NO abre streams: ver la cabecera de dsp_engine.cpp.
find_package(oboe REQUIRED CONFIG)

add_library(auralinedsp SHARED
    src/main/cpp/dsp_engine.cpp
)

target_compile_options(auralinedsp PRIVATE -Wall -Wextra -fno-exceptions -fno-rtti)

find_library(log-lib log)
target_link_libraries(auralinedsp ${log-lib} oboe::oboe)
