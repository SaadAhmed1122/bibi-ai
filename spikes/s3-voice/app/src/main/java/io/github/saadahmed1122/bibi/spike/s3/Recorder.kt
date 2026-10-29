package io.github.saadahmed1122.bibi.spike.s3

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File

/**
 * Push-to-talk clip recorder (main process, foreground activity only; no background listening,
 * PRD 0.9). 16 kHz mono 16-bit PCM from the VOICE_RECOGNITION source (PRD §15.2), saved as WAV.
 * The buffer for [MAX_SECONDS] is allocated once per recording; nothing is allocated per read.
 * AudioRecord reference: developer.android.com/reference/android/media/AudioRecord (the Builder
 * throws UnsupportedOperationException on build() without RECORD_AUDIO).
 */
class Recorder(private val onFinished: (File, Double) -> Unit, private val onError: (String) -> Unit) {

    @Volatile
    private var recording = false
    private var thread: Thread? = null

    val isRecording: Boolean get() = recording

    /** The caller must hold RECORD_AUDIO (MainActivity requests it on first use). */
    @SuppressLint("MissingPermission")
    fun start(target: File) {
        if (recording) return
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) {
            onError("AudioRecord.getMinBufferSize failed ($minBuf)")
            return
        }
        val record = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(minBuf * 4)
                .build()
        } catch (e: Exception) {
            onError("AudioRecord: ${e.message}")
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            onError("AudioRecord not initialized (mic busy?)")
            return
        }
        recording = true
        thread = Thread({
            val pcm = ShortArray(RATE * MAX_SECONDS)
            var n = 0
            try {
                record.startRecording()
                while (recording && n < pcm.size) {
                    val read = record.read(pcm, n, minOf(minBuf / 2, pcm.size - n))
                    if (read < 0) {
                        onError("AudioRecord.read error $read")
                        break
                    }
                    n += read
                }
                record.stop()
            } catch (e: IllegalStateException) {
                onError("AudioRecord: ${e.message}")
            } finally {
                record.release()
                recording = false
            }
            if (n > 0) {
                WavIo.writeMono16(target, pcm, n, RATE)
                onFinished(target, n.toDouble() / RATE)
            } else {
                onError("no audio recorded")
            }
        }, "recorder").apply { start() }
    }

    fun stop() {
        recording = false
    }

    companion object {
        const val RATE = 16_000
        const val MAX_SECONDS = 30
    }
}
