cmake_minimum_required(VERSION 3.22.1)
project("auralinedsp")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)

add_library(auralinedsp SHARED
    src/main/cpp/dsp_engine.cpp
)

find_library(log-lib log)
target_link_libraries(auralinedsp ${log-lib} oboe::oboe)
