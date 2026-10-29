package io.github.saadahmed1122.bibi.spike.s3

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.PowerManager
import android.os.RemoteException
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * Bound service in the `:inference` process (manifest `android:process=":inference"`), so the
 * native runtime's memory and any OOM kill belong to this process, not the UI. Messenger IPC as
 * in developer.android.com/develop/background-work/services/bound-services ("Use a Messenger").
 *
 * One run per process: after [MSG_DONE] the process exits, so the next run starts with a fresh
 * process and VmHWM (peak RSS) belongs to one model only. ApplicationExitInfo records that exit as
 * EXIT_SELF for `<package>:inference`; that row is expected after every run.
 */
class InferenceService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private val busy = AtomicBoolean(false)
    private lateinit var messenger: Messenger

    override fun onCreate() {
        super.onCreate()
        messenger = Messenger(Handler(Looper.getMainLooper()) { msg -> handle(msg); true })
    }

    override fun onBind(intent: Intent): IBinder = messenger.binder

    private fun handle(msg: Message) {
        if (msg.what != MSG_RUN) return
        val reply = msg.replyTo ?: return
        val args = Bundle(msg.data)
        if (!busy.compareAndSet(false, true)) {
            send(reply, MSG_DONE, "busy: a run is already going")
            return
        }
        Thread({ runJob(args, reply) }, "bench").start()
    }

    private fun runJob(args: Bundle, reply: Messenger) {
        var result = "done"
        val spec = Models.byId(args.getString(ARG_MODEL).orEmpty())
        val threads = args.getInt(ARG_THREADS, 2)
        if (spec == null) {
            finish(reply, "unknown model ${args.getString(ARG_MODEL)}")
            return
        }
        val log = RunLog(
            file = File(args.getString(ARG_RUN_FILE)!!),
            runId = args.getString(ARG_RUN_ID).orEmpty(),
            device = args.getString(ARG_DEVICE).orEmpty(),
            task = spec.family.task,
            spec = spec,
            threads = threads,
            conditions = ::conditions,
        )
        val mem = AndroidMemProbe()
        try {
            val resolution = Models.resolve(spec, File(args.getString(ARG_MODELS_ROOT)!!))
            if (!resolution.ready) {
                log.row(RunLog.START, detail = "runtime=${SherpaEngines.runtimeName}")
                log.row(RunLog.ERROR, item = "files", detail = "missing: ${resolution.missing.joinToString("; ")}")
                log.row(RunLog.STOP, detail = "model files missing")
                result = "model files missing: ${resolution.missing.joinToString("; ")}"
                return
            }
            val runner = BenchRunner(SherpaEngines, log, mem, progress = { send(reply, MSG_PROGRESS, it) })
            val rounds = args.getInt(ARG_ROUNDS, 3)
            when (spec.family.task) {
                Task.TTS -> runner.runTts(
                    spec, resolution.paths, threads, args.getInt(ARG_SID, spec.defaultSid), rounds,
                    readSentences(), if (args.getBoolean(ARG_PLAY)) ::play else null,
                )
                Task.STT -> {
                    val paths = args.getStringArray(ARG_CLIP_PATHS).orEmpty()
                    val refs = args.getStringArray(ARG_CLIP_REFS).orEmpty()
                    val clips = paths.mapIndexed { i, p ->
                        Clip(File(p).nameWithoutExtension, WavIo.read(File(p)), refs.getOrElse(i) { "" })
                    }
                    runner.runStt(spec, resolution.paths, threads, rounds, clips)
                }
            }
        } catch (t: Throwable) {
            log.row(RunLog.ERROR, detail = "${t.javaClass.simpleName}: ${t.message}")
            log.row(RunLog.STOP, detail = "aborted")
            result = "error: ${t.message}"
        } finally {
            mem.close()
            finish(reply, result)
        }
    }

    /** Replies, then exits the process shortly after so the reply is delivered first. */
    private fun finish(reply: Messenger, text: String) {
        send(reply, MSG_DONE, text)
        main.postDelayed({ exitProcess(0) }, EXIT_DELAY_MS)
    }

    /** "id|text" lines from assets/tts_sentences.txt; '#' starts a comment. */
    private fun readSentences(): List<Sentence> =
        assets.open("tts_sentences.txt").bufferedReader().readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && '|' in it }
            .map { Sentence(it.substringBefore('|').trim(), it.substringAfter('|').trim()) }

    /** Optional playback after a measured synthesis (never inside the timed section). */
    private fun play(audio: TtsAudio) {
        if (audio.samples.isEmpty()) return
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(audio.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(audio.samples.size * 4)
            .build()
        try {
            // MODE_STATIC: data is copied once, before play() (AudioTrack reference).
            track.write(audio.samples, 0, audio.samples.size, AudioTrack.WRITE_BLOCKING)
            track.play()
            Thread.sleep((audio.seconds * 1000).toLong() + 100)
        } finally {
            track.release()
        }
    }

    private fun conditions(): Conditions {
        val power = getSystemService(PowerManager::class.java)
        val battery = getSystemService(BatteryManager::class.java)
        return Conditions(
            thermal = power.currentThermalStatus.toString(),
            batteryPct = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).toString(),
            charging = battery.isCharging.toString(),
        )
    }

    private fun send(to: Messenger, what: Int, text: String) {
        try {
            to.send(Message.obtain(null, what).apply { data = Bundle().apply { putString(ARG_TEXT, text) } })
        } catch (e: RemoteException) {
            // The UI process is gone; the run file still has every row.
        }
    }

    companion object {
        const val MSG_RUN = 1
        const val MSG_PROGRESS = 2
        const val MSG_DONE = 3

        const val ARG_MODEL = "model"
        const val ARG_THREADS = "threads"
        const val ARG_ROUNDS = "rounds"
        const val ARG_SID = "sid"
        const val ARG_PLAY = "play"
        const val ARG_RUN_FILE = "run_file"
        const val ARG_RUN_ID = "run_id"
        const val ARG_DEVICE = "device"
        const val ARG_MODELS_ROOT = "models_root"
        const val ARG_CLIP_PATHS = "clip_paths"
        const val ARG_CLIP_REFS = "clip_refs"
        const val ARG_TEXT = "text"

        private const val EXIT_DELAY_MS = 500L
    }
}
