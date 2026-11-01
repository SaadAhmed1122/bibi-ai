package io.github.saadahmed1122.bibi.ai.inference.api

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/**
 * Audio over the Messenger protocol (strings only): 16-bit little-endian PCM in Base64. 0.5 s at
 * 16 kHz is 16,000 bytes, about 21 KB of text per message, well under the binder limit.
 */
object Pcm16 {
    fun encode(samples: FloatArray): String {
        val buf = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) buf.putShort((s.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
        return Base64.getEncoder().encodeToString(buf.array())
    }

    fun decode(text: String): FloatArray {
        val buf = ByteBuffer.wrap(Base64.getDecoder().decode(text)).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(buf.remaining() / 2) { buf.short / Short.MAX_VALUE.toFloat() }
    }
}
