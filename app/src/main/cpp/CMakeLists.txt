cmake_minimum_required(VERSION 3.22.1)
project(mp3enc C)

set(LAME "${CMAKE_CURRENT_SOURCE_DIR}/lame")

# LAME 3.100, vendored verbatim (LGPL-2.1-or-later). See lame/COPYING.
file(GLOB LAME_SOURCES
        "${LAME}/libmp3lame/*.c"
        "${LAME}/mpglib/*.c")

add_library(mp3enc SHARED ${LAME_SOURCES} lame_jni.c)

target_include_directories(mp3enc PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}"   # our config.h
        "${LAME}/include"
        "${LAME}/libmp3lame"
        "${LAME}/mpglib")

target_compile_definitions(mp3enc PRIVATE HAVE_CONFIG_H)

target_compile_options(mp3enc PRIVATE
        -O3
        -fvisibility=hidden
        # LAME's own source is old and noisy; silence what we cannot fix.
        -Wno-unused-but-set-variable
        -Wno-unused-variable
        -Wno-unused-function
        -Wno-sometimes-uninitialized
        -Wno-shift-negative-value
        -Wno-absolute-value
        -Wno-pointer-sign)

find_library(log-lib log)
target_link_libraries(mp3enc ${log-lib} m)
