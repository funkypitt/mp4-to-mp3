package app.mp4tomp3.core

/**
 * Minimal binding to the vendored LAME encoder (see `src/main/cpp/`).
 *
 * Usage is strictly: [open] once per file, [encode] repeatedly with interleaved
 * 16-bit PCM, [flush] once, then [close]. Handles are not thread-safe.
 */
object Lame {

    init {
        System.loadLibrary("mp3enc")
    }

    /** Returns an opaque handle, or 0 if the encoder could not be configured. */
    @JvmStatic
    external fun nativeOpen(
        inSampleRate: Int,
        channels: Int,
        outSampleRate: Int,
        bitrateKbps: Int,
        title: String?,
    ): Long

    /** Returns bytes written to [out], or a negative value on error. */
    @JvmStatic
    external fun nativeEncode(
        handle: Long,
        pcm: ShortArray,
        samplesPerChannel: Int,
        out: ByteArray,
    ): Int

    @JvmStatic
    external fun nativeFlush(handle: Long, out: ByteArray): Int

    @JvmStatic
    external fun nativeClose(handle: Long)

    /**
     * Worst-case MP3 output for [samplesPerChannel] input frames, per LAME's
     * own documented bound (1.25 * samples + 7200).
     */
    fun outputBufferSize(samplesPerChannel: Int): Int =
        (samplesPerChannel * 1.25).toInt() + 7200
}
