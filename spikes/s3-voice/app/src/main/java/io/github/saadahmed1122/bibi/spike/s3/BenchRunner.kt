package io.github.saadahmed1122.bibi.spike.s3

/** Memory readings for the current process. Android: [AndroidMemProbe]; tests: a fake. */
interface MemProbe {
    /** Starts a new peak window (resets the sampled PSS peak). */
    fun beginWindow()

    /** Peak PSS in kB seen since [beginWindow], including one reading taken now; null if unknown. */
    fun endWindowPeakKb(): Long?

    /** Process-lifetime peak RSS (VmHWM) in kB; null if unknown. */
    fun vmHwmKb(): Long?
}

/** A TTS benchmark sentence from assets/tts_sentences.txt. */
class Sentence(val id: String, val text: String)

/** An STT clip; [reference] is the text the owner was asked to read ("" for picked files). */
class Clip(val id: String, val pcm: PcmClip, val reference: String)

/**
 * Runs one benchmark (one model, one thread count) and writes every measurement to [log].
 * Order: `start`, `load` (engine creation), one `warmup` (not counted), then [rounds] passes over
 * all items (`item` rows), then `stop`. Timing covers only the engine call: no file I/O, logging
 * or playback inside the timed section. Pure Kotlin; the clock and memory probe are injected.
 */
class BenchRunner(
    private val factory: EngineFactory,
    private val log: RunLog,
    private val mem: MemProbe,
    private val nanoTime: () -> Long = System::nanoTime,
    private val progress: (String) -> Unit = {},
) {

    fun runTts(
        spec: ModelSpec,
        files: Map<String, String>,
        threads: Int,
        sid: Int,
        rounds: Int,
        sentences: List<Sentence>,
        play: ((TtsAudio) -> Unit)? = null,
    ) {
        log.row(RunLog.START, detail = "runtime=${factory.runtimeName}; sid=$sid; rounds=$rounds; play=${play != null}")
        val engine = load(spec) { factory.tts(spec, files, threads) } ?: return
        try {
            val warm = sentences.minByOrNull { it.text.length }
            if (warm != null) ttsOnce(engine, warm, sid, RunLog.WARMUP, 0, null)
            for (round in 1..rounds) {
                for (s in sentences) {
                    progress("TTS ${spec.id}: round $round/$rounds, ${s.id}")
                    ttsOnce(engine, s, sid, RunLog.ITEM, round, play)
                }
            }
        } finally {
            engine.close()
            log.row(RunLog.STOP, vmHwmKb = mem.vmHwmKb())
        }
    }

    fun runStt(spec: ModelSpec, files: Map<String, String>, threads: Int, rounds: Int, clips: List<Clip>) {
        log.row(RunLog.START, detail = "runtime=${factory.runtimeName}; rounds=$rounds; clips=${clips.size}")
        val engine = load(spec) { factory.stt(spec, files, threads) } ?: return
        try {
            clips.firstOrNull()?.let { sttOnce(engine, it, RunLog.WARMUP, 0) }
            for (round in 1..rounds) {
                for (c in clips) {
                    progress("STT ${spec.id}: round $round/$rounds, ${c.id}")
                    sttOnce(engine, c, RunLog.ITEM, round)
                }
            }
        } finally {
            engine.close()
            log.row(RunLog.STOP, vmHwmKb = mem.vmHwmKb())
        }
    }

    /** Creates the engine and logs a `load` row; on failure logs `error` + `stop` and returns null. */
    private fun <E> load(spec: ModelSpec, create: () -> E): E? {
        progress("Loading ${spec.id}…")
        mem.beginWindow()
        val t0 = nanoTime()
        val engine = try {
            create()
        } catch (t: Throwable) {
            log.row(RunLog.ERROR, item = "load", detail = "${t.javaClass.simpleName}: ${t.message}")
            log.row(RunLog.STOP, pssPeakKb = mem.endWindowPeakKb(), vmHwmKb = mem.vmHwmKb(), detail = "load failed")
            return null
        }
        val ms = (nanoTime() - t0) / 1e6
        log.row(RunLog.LOAD, computeMs = ms, pssPeakKb = mem.endWindowPeakKb(), vmHwmKb = mem.vmHwmKb())
        return engine
    }

    private fun ttsOnce(engine: TtsEngine, s: Sentence, sid: Int, kind: String, round: Int, play: ((TtsAudio) -> Unit)?) {
        mem.beginWindow()
        try {
            val t0 = nanoTime()
            val audio = engine.synthesize(s.text, sid)
            val ms = (nanoTime() - t0) / 1e6
            val peak = mem.endWindowPeakKb()
            val secs = audio.seconds
            log.row(
                kind, item = s.id, round = round, chars = s.text.length, audioS = secs, computeMs = ms,
                rtf = if (secs > 0) ms / 1000.0 / secs else null, pssPeakKb = peak, vmHwmKb = mem.vmHwmKb(),
            )
            play?.invoke(audio)
        } catch (t: Throwable) {
            log.row(RunLog.ERROR, item = s.id, round = round, detail = "${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun sttOnce(engine: SttEngine, c: Clip, kind: String, round: Int) {
        mem.beginWindow()
        try {
            val t0 = nanoTime()
            val text = engine.transcribe(c.pcm)
            val ms = (nanoTime() - t0) / 1e6
            val peak = mem.endWindowPeakKb()
            val secs = c.pcm.seconds
            log.row(
                kind, item = c.id, round = round, audioS = secs, computeMs = ms,
                rtf = if (secs > 0) ms / 1000.0 / secs else null, pssPeakKb = peak, vmHwmKb = mem.vmHwmKb(),
                detail = text.trim(), reference = c.reference,
            )
        } catch (t: Throwable) {
            log.row(RunLog.ERROR, item = c.id, round = round, detail = "${t.javaClass.simpleName}: ${t.message}")
        }
    }
}
