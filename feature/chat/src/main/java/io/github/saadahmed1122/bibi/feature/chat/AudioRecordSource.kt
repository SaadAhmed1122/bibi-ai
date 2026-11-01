package io.github.saadahmed1122.bibi.feature.chat

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

/**
 * The phone microphone: 16 kHz mono float, read in 0.1 s pieces on its own thread (SPEC-voice.md).
 * Only while the mic button is held; nothing is recorded in the background or saved.
 * The caller checks RECORD_AUDIO first; without it [start] returns false.
 */
class AudioRecordSource : AudioSource {
    @Volatile private var running = false
    private var thread: Thread? = null
    private var record: AudioRecord? = null

    @SuppressLint("MissingPermission")
    override fun start(onChunk: (FloatArray) -> Unit): Boolean {
        if (running) return false
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
        val r = try {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT, maxOf(min, RATE * 4))
        } catch (e: SecurityException) {
            return false
        }
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            return false
        }
        record = r
        r.startRecording()
        running = true
        thread = Thread({
            val buf = FloatArray(RATE / 10)
            while (running) {
                val n = r.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
                if (n > 0) onChunk(buf.copyOf(n))
            }
        }, "mic").also { it.start() }
        return true
    }

    override fun stop() {
        if (!running) return
        running = false
        record?.stop()
        thread?.join(1_000)
        thread = null
        record?.release()
        record = null
    }

    private companion object {
        const val RATE = 16_000
    }
}
