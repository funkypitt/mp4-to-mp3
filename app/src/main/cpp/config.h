/*
 * Hand-written stand-in for the config.h that LAME's ./configure would emit.
 *
 * The vendored LAME sources under lame/ are unmodified upstream 3.100, and
 * several of them expect configure-provided typedefs (notably
 * ieee754_float32_t, which nothing else in the tree declares). Android/NDK is a
 * fixed, known target, so every feature probe has a constant answer and can be
 * written down directly.
 */
#ifndef MP4TOMP3_LAME_CONFIG_H
#define MP4TOMP3_LAME_CONFIG_H

/* The NDK provides all exact-width integer types. */
#include <stdint.h>

typedef float ieee754_float32_t;
typedef double ieee754_float64_t;

#define STDC_HEADERS 1
#define HAVE_LIMITS_H 1
#define HAVE_STDINT_H 1
#define HAVE_INTTYPES_H 1
#define HAVE_STDLIB_H 1
#define HAVE_STRING_H 1
#define HAVE_STRINGS_H 1
#define HAVE_ERRNO_H 1
#define HAVE_FCNTL_H 1
#define HAVE_MEMCPY 1
#define HAVE_STRCHR 1

/* Bundle the decoder half of LAME (mpglib), which lame.c links against. */
#define HAVE_MPGLIB 1

/* We build the library only — no frontend, no NASM, no SSE intrinsics. */
#define LAME_LIBRARY_BUILD 1

#endif /* MP4TOMP3_LAME_CONFIG_H */
