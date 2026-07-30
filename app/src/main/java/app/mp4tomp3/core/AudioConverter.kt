package app.mp4tomp3.core

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ConversionException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Decodes the audio track of a video with the platform decoder (MediaExtractor +
 * MediaCodec) and re-encodes it to MP3 with LAME. Nothing is buffered to disk:
 * PCM flows straight from the decoder into the encoder.
 */
object AudioConverter {

    const val BITRATE_KBPS = 320

    private const val TIMEOUT_US = 10_000L

    /** MPEG-1 Layer III — the only rates that allow a 320 kbps stream. */
    private val MPEG1_RATES = intArrayOf(32_000, 44_100, 48_000)

    /**
     * @param title written as the ID3v2 title tag.
     * @param onProgress called with 0f..1f; best-effort, based on timestamps.
     * @param isCancelled polled between buffers.
     */
    fun convert(
        context: Context,
        source: Uri,
        title: String,
        output: OutputStream,
        onProgress: (Float) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var handle = 0L
        val sink = BufferedOutputStream(output, 1 shl 16)

        try {
            try {
                extractor.setDataSource(context, source, null)
            } catch (e: Exception) {
                throw ConversionException("Could not read this file", e)
            }

            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: throw ConversionException("This video has no audio track")

            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
            val durationUs = inputFormat.longOrNull(MediaFormat.KEY_DURATION) ?: 0L

            codec = try {
                MediaCodec.createDecoderByType(mime).apply {
                    configure(inputFormat, null, null, 0)
                    start()
                }
            } catch (e: Exception) {
                throw ConversionException("No decoder for $mime", e)
            }

            val info = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false

            // Set once the decoder reports its real output format.
            var srcChannels = 0
            var outChannels = 0
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var pcm = ShortArray(0)
            var mp3 = ByteArray(0)
            var lastReported = -1f

            while (!sawOutputEos) {
                if (isCancelled()) throw InterruptedException()

                if (!sawInputEos) {
                    val index = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit

                    else -> if (index >= 0) {
                        if (handle == 0L) {
                            // The decoder's own format is authoritative: containers
                            // routinely lie about channel count and sample rate.
                            val format = codec.outputFormat
                            srcChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            pcmEncoding = format.intOrNull(MediaFormat.KEY_PCM_ENCODING)
                                ?: AudioFormat.ENCODING_PCM_16BIT

                            // More than stereo (5.1 and friends): fold to mono rather
                            // than guess at a channel layout we cannot see.
                            outChannels = if (srcChannels > 2) 1 else srcChannels
                            handle = Lame.nativeOpen(
                                inSampleRate = sampleRate,
                                channels = outChannels,
                                outSampleRate = pickOutputRate(sampleRate),
                                bitrateKbps = BITRATE_KBPS,
                                title = title.ifBlank { null },
                            )
                            if (handle == 0L) throw ConversionException("Could not start the MP3 encoder")
                        }

                        if (info.size > 0) {
                            val buffer = codec.getOutputBuffer(index)!!
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)

                            val frames = readPcm(buffer, pcmEncoding, srcChannels, outChannels) { needed ->
                                if (pcm.size < needed) pcm = ShortArray(needed)
                                pcm
                            }
                            if (frames > 0) {
                                val needed = Lame.outputBufferSize(frames)
                                if (mp3.size < needed) mp3 = ByteArray(needed)
                                val written = Lame.nativeEncode(handle, pcm, frames, mp3)
                                if (written < 0) throw ConversionException("MP3 encoding failed ($written)")
                                if (written > 0) sink.write(mp3, 0, written)
                            }
                        }

                        codec.releaseOutputBuffer(index, false)

                        if (durationUs > 0) {
                            val p = (info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f)
                            if (p - lastReported >= 0.01f) {
                                lastReported = p
                                onProgress(p)
                            }
                        }

                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEos = true
                        }
                    }
                }
            }

            if (handle == 0L) throw ConversionException("This video has no decodable audio")

            val tail = ByteArray(Lame.outputBufferSize(0))
            val written = Lame.nativeFlush(handle, tail)
            if (written > 0) sink.write(tail, 0, written)
            sink.flush()
            onProgress(1f)
        } finally {
            if (handle != 0L) Lame.nativeClose(handle)
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            codec?.release()
            extractor.release()
        }
    }

    /** LAME resamples for us; we only have to land on a rate that allows 320 kbps. */
    private fun pickOutputRate(sampleRate: Int): Int = when {
        sampleRate in MPEG1_RATES -> sampleRate
        sampleRate > 48_000 -> 48_000
        else -> 44_100
    }

    /**
     * Converts one decoder buffer to interleaved 16-bit PCM, downmixing to mono
     * when the source has more than two channels.
     *
     * @return frames (samples per channel) written into the array from [buffer].
     */
    private inline fun readPcm(
        buffer: ByteBuffer,
        pcmEncoding: Int,
        srcChannels: Int,
        outChannels: Int,
        ensure: (Int) -> ShortArray,
    ): Int {
        buffer.order(ByteOrder.nativeOrder())
        val downmix = srcChannels != outChannels

        return when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val floats = buffer.asFloatBuffer()
                val total = floats.remaining()
                val frames = total / srcChannels
                val pcm = ensure(frames * outChannels)
                if (downmix) {
                    var o = 0
                    for (f in 0 until frames) {
                        var sum = 0f
                        for (c in 0 until srcChannels) sum += floats.get()
                        pcm[o++] = clamp(sum / srcChannels)
                    }
                } else {
                    for (i in 0 until frames * srcChannels) pcm[i] = clamp(floats.get())
                }
                frames
            }

            AudioFormat.ENCODING_PCM_16BIT -> {
                val shorts = buffer.asShortBuffer()
                val total = shorts.remaining()
                val frames = total / srcChannels
                val pcm = ensure(frames * outChannels)
                if (downmix) {
                    var o = 0
                    for (f in 0 until frames) {
                        var sum = 0
                        for (c in 0 until srcChannels) sum += shorts.get().toInt()
                        pcm[o++] = (sum / srcChannels).toShort()
                    }
                } else {
                    shorts.get(pcm, 0, frames * srcChannels)
                }
                frames
            }

            else -> throw ConversionException("Unsupported audio format ($pcmEncoding)")
        }
    }

    private fun clamp(v: Float): Short {
        val scaled = v * 32767f
        return when {
            scaled >= 32767f -> Short.MAX_VALUE
            scaled <= -32768f -> Short.MIN_VALUE
            else -> scaled.toInt().toShort()
        }
    }

    private fun MediaFormat.longOrNull(key: String): Long? =
        if (containsKey(key)) runCatching { getLong(key) }.getOrNull() else null

    private fun MediaFormat.intOrNull(key: String): Int? =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null
}
