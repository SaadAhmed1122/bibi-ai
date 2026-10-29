package io.github.saadahmed1122.bibi.spike.s3

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decoded clip: mono samples in [-1, 1]. */
class PcmClip(val samples: FloatArray, val sampleRate: Int) {
    val seconds: Double get() = samples.size.toDouble() / sampleRate
}

/**
 * Minimal RIFF/WAVE reader and writer for 16-bit PCM. Pure Kotlin, JVM-testable.
 * Reads mono or multi-channel (averaged to mono); rejects other encodings with a clear message.
 */
object WavIo {
    private const val PCM = 1
    private const val EXTENSIBLE = 0xFFFE

    fun read(file: File): PcmClip = parse(file.readBytes())

    fun parse(bytes: ByteArray): PcmClip {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(bytes.size >= 12 && tag(buf, 0) == "RIFF" && tag(buf, 8) == "WAVE") { "not a RIFF/WAVE file" }
        var pos = 12
        var channels = 0
        var rate = 0
        var bits = 0
        var format = 0
        while (pos + 8 <= bytes.size) {
            val id = tag(buf, pos)
            val size = buf.getInt(pos + 4)
            val body = pos + 8
            require(size >= 0) { "bad chunk size in $id" }
            when (id) {
                "fmt " -> {
                    format = buf.getShort(body).toInt() and 0xFFFF
                    channels = buf.getShort(body + 2).toInt()
                    rate = buf.getInt(body + 4)
                    bits = buf.getShort(body + 14).toInt()
                }
                "data" -> {
                    require(format == PCM || format == EXTENSIBLE) { "WAV encoding $format is not PCM" }
                    require(bits == 16) { "WAV is $bits-bit; need 16-bit PCM" }
                    require(channels >= 1 && rate > 0) { "WAV fmt chunk missing or invalid" }
                    val end = minOf(bytes.size, body + size)
                    val frames = (end - body) / (2 * channels)
                    val out = FloatArray(frames)
                    var p = body
                    for (i in 0 until frames) {
                        var sum = 0
                        repeat(channels) { sum += buf.getShort(p).toInt(); p += 2 }
                        out[i] = sum / (channels * 32768f)
                    }
                    return PcmClip(out, rate)
                }
            }
            pos = body + size + (size and 1) // chunks are word-aligned
        }
        throw IllegalArgumentException("WAV has no data chunk")
    }

    /** Writes [count] 16-bit mono samples from [pcm] as a WAV file. */
    fun writeMono16(file: File, pcm: ShortArray, count: Int, sampleRate: Int) {
        val dataBytes = count * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + dataBytes); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(PCM.toShort()); putShort(1)
            putInt(sampleRate); putInt(sampleRate * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(dataBytes)
        }
        val data = ByteBuffer.allocate(dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) data.putShort(pcm[i])
        val out = ByteArrayOutputStream(44 + dataBytes)
        out.write(header.array())
        out.write(data.array())
        file.parentFile?.mkdirs()
        file.writeBytes(out.toByteArray())
    }

    private fun tag(buf: ByteBuffer, at: Int): String =
        String(ByteArray(4) { buf.get(at + it) }, Charsets.US_ASCII)
}
