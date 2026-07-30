/*
 * JNI bridge to LAME. Thin on purpose: one encoder handle per file, 16-bit
 * interleaved PCM in, MP3 frames out. All policy (bitrate, resampling) is
 * decided on the Kotlin side and passed in here.
 *
 * Copyright (C) 2026  MP4 to MP3 contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */
#include <jni.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <android/log.h>

#include "lame.h"

#define TAG "lame_jni"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define EXPORT __attribute__((visibility("default"))) JNIEXPORT

typedef struct {
    lame_global_flags *gf;
    int channels;
} encoder_t;

EXPORT jlong JNICALL
Java_app_mp4tomp3_core_Lame_nativeOpen(JNIEnv *env, jclass clazz,
                                       jint inSampleRate, jint channels,
                                       jint outSampleRate, jint bitrateKbps,
                                       jstring title) {
    (void) clazz;
    encoder_t *enc = calloc(1, sizeof(encoder_t));
    if (enc == NULL) return 0;

    enc->gf = lame_init();
    if (enc->gf == NULL) {
        free(enc);
        return 0;
    }
    enc->channels = channels;

    lame_set_in_samplerate(enc->gf, inSampleRate);
    lame_set_num_channels(enc->gf, channels);
    lame_set_out_samplerate(enc->gf, outSampleRate);
    lame_set_mode(enc->gf, channels == 1 ? MONO : JOINT_STEREO);

    /* Constant bit rate, exactly the requested kbps. */
    lame_set_VBR(enc->gf, vbr_off);
    lame_set_brate(enc->gf, bitrateKbps);
    lame_set_quality(enc->gf, 2); /* near-best psychoacoustics, still fast */
    lame_set_bWriteVbrTag(enc->gf, 0); /* no Xing/LAME header for CBR */

    if (title != NULL) {
        const char *t = (*env)->GetStringUTFChars(env, title, NULL);
        if (t != NULL) {
            id3tag_init(enc->gf);
            id3tag_add_v2(enc->gf);
            id3tag_set_title(enc->gf, t);
            (*env)->ReleaseStringUTFChars(env, title, t);
        }
    }

    if (lame_init_params(enc->gf) < 0) {
        LOGE("lame_init_params failed (in=%d ch=%d out=%d br=%d)",
             inSampleRate, channels, outSampleRate, bitrateKbps);
        lame_close(enc->gf);
        free(enc);
        return 0;
    }
    return (jlong) (intptr_t) enc;
}

/*
 * Encodes `samplesPerChannel` frames of interleaved 16-bit PCM.
 * Returns bytes written into `out`, or a negative LAME error code.
 */
EXPORT jint JNICALL
Java_app_mp4tomp3_core_Lame_nativeEncode(JNIEnv *env, jclass clazz, jlong handle,
                                         jshortArray pcm, jint samplesPerChannel,
                                         jbyteArray out) {
    (void) clazz;
    encoder_t *enc = (encoder_t *) (intptr_t) handle;
    if (enc == NULL) return -1;

    jshort *in = (*env)->GetShortArrayElements(env, pcm, NULL);
    jbyte *mp3 = (*env)->GetByteArrayElements(env, out, NULL);
    if (in == NULL || mp3 == NULL) {
        if (in) (*env)->ReleaseShortArrayElements(env, pcm, in, JNI_ABORT);
        if (mp3) (*env)->ReleaseByteArrayElements(env, out, mp3, JNI_ABORT);
        return -1;
    }
    jsize outCapacity = (*env)->GetArrayLength(env, out);

    int written;
    if (enc->channels == 1) {
        written = lame_encode_buffer(enc->gf, in, in, samplesPerChannel,
                                     (unsigned char *) mp3, outCapacity);
    } else {
        written = lame_encode_buffer_interleaved(enc->gf, in, samplesPerChannel,
                                                 (unsigned char *) mp3, outCapacity);
    }

    (*env)->ReleaseShortArrayElements(env, pcm, in, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, out, mp3, written > 0 ? 0 : JNI_ABORT);
    return written;
}

/* Emits whatever is left in LAME's internal buffers. */
EXPORT jint JNICALL
Java_app_mp4tomp3_core_Lame_nativeFlush(JNIEnv *env, jclass clazz, jlong handle,
                                        jbyteArray out) {
    (void) clazz;
    encoder_t *enc = (encoder_t *) (intptr_t) handle;
    if (enc == NULL) return -1;

    jbyte *mp3 = (*env)->GetByteArrayElements(env, out, NULL);
    if (mp3 == NULL) return -1;
    jsize outCapacity = (*env)->GetArrayLength(env, out);

    int written = lame_encode_flush(enc->gf, (unsigned char *) mp3, outCapacity);

    (*env)->ReleaseByteArrayElements(env, out, mp3, written > 0 ? 0 : JNI_ABORT);
    return written;
}

EXPORT void JNICALL
Java_app_mp4tomp3_core_Lame_nativeClose(JNIEnv *env, jclass clazz, jlong handle) {
    (void) env;
    (void) clazz;
    encoder_t *enc = (encoder_t *) (intptr_t) handle;
    if (enc == NULL) return;
    lame_close(enc->gf);
    free(enc);
}
