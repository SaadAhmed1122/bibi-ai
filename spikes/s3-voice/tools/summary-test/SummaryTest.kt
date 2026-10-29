package io.github.saadahmed1122.bibi.spike.s3

import java.io.File
import java.nio.file.Files
import kotlin.math.abs

// JVM-only tests (no Android, no sherpa-onnx). Run with tools/summary-test/run.sh.
// Real BenchRunner + RunLog with fake engines, a fake clock and a fake memory probe, so the CSVs
// under test have exactly the format the app writes.

val tmp: File = Files.createTempDirectory("s3test").toFile()
var runNo = 0

/** Fake clock: engines advance it by the compute time they "spend". */
class FakeClock { var nanos = 0L }

class FakeMem(var pssKb: Long, var hwmKb: Long) : MemProbe {
    override fun beginWindow() {}
    override fun endWindowPeakKb() = pssKb
    override fun vmHwmKb() = hwmKb
}

/**
 * TTS: 0.05 s of audio per character at 24 kHz; each call costs rtf(call) × audio duration.
 * STT: each call costs rtf(call) × clip duration. [failLoad] makes engine creation throw.
 */
class FakeFactory(val clock: FakeClock, val rtf: (Int) -> Double, val failLoad: Boolean = false) : EngineFactory {
    var calls = 0
    override val runtimeName = "fake"
    override fun tts(spec: ModelSpec, files: Map<String, String>, threads: Int): TtsEngine {
        if (failLoad) throw IllegalStateException("model missing")
        return object : TtsEngine {
            override fun synthesize(text: String, sid: Int): TtsAudio {
                val audio = TtsAudio(FloatArray((text.length * 0.05 * 24_000).toInt()), 24_000)
                clock.nanos += (rtf(calls++) * audio.seconds * 1e9).toLong()
                return audio
            }
            override fun close() {}
        }
    }
    override fun stt(spec: ModelSpec, files: Map<String, String>, threads: Int): SttEngine {
        if (failLoad) throw IllegalStateException("model missing")
        return object : SttEngine {
            override fun transcribe(clip: PcmClip): String {
                clock.nanos += (rtf(calls++) * clip.seconds * 1e9).toLong()
                return "hello, world"
            }
            override fun close() {}
        }
    }
}

val sentences = listOf(
    Sentence("short", "Hi, I am Bibi."),
    Sentence("medium", "I can read your notes aloud and keep everything on this phone."),
    Sentence("long", "This is a longer sentence. It has two parts, so the engine has to work a little harder."),
)
val clips = listOf(
    Clip("clip1", PcmClip(FloatArray(16_000 * 3), 16_000), "one"),
    Clip("clip2", PcmClip(FloatArray(16_000 * 8), 16_000), "two"),
    Clip("clip3", PcmClip(FloatArray(16_000 * 15), 16_000), "three"),
)

/** Runs one fake benchmark and returns the CSV text. */
fun bench(
    modelId: String,
    rtf: (Int) -> Double,
    pssMb: Long = 300,
    hwmMb: Long = pssMb,
    rounds: Int = 3,
    failLoad: Boolean = false,
    device: String = "vivo-Y17s",
): String {
    val spec = Models.byId(modelId)!!
    val task = spec.family.task
    val clock = FakeClock()
    val file = File(tmp, "run${runNo++}.csv")
    val log = RunLog(file, "id", device, task, spec, 2)
    val runner = BenchRunner(FakeFactory(clock, rtf, failLoad), log, FakeMem(pssMb * 1024, hwmMb * 1024), { clock.nanos })
    if (task == Task.TTS) runner.runTts(spec, emptyMap(), 2, 0, rounds, sentences) else runner.runStt(spec, emptyMap(), 2, rounds, clips)
    return file.readText()
}

fun check(name: String, ok: Boolean, detail: String) {
    if (!ok) throw AssertionError("FAIL $name: $detail")
    println("PASS $name")
}

fun near(a: Double?, b: Double, eps: Double = 1e-3) = a != null && abs(a - b) <= eps

fun main() {
    // Statistics
    check("median odd", Summary.median(listOf(3.0, 1.0, 2.0)) == 2.0, "")
    check("median even", Summary.median(listOf(4.0, 1.0, 2.0, 3.0)) == 2.5, "")
    val twenty = (1..20).map { it.toDouble() }
    check("p95 nearest rank of 1..20 = 19", Summary.p95(twenty) == 19.0, "${Summary.p95(twenty)}")
    check("p95 of 9 items = max", Summary.p95((1..9).map { it.toDouble() }) == 9.0, "")
    check("median empty = null", Summary.median(emptyList()) == null, "")

    // CSV format: header, start/load/warmup/items/stop, warmup excluded
    val kokoroPass = bench("kokoro-en-v0_19", { 0.6 })
    val lines = kokoroPass.trim().lines()
    check("csv header", lines.first() == RunLog.HEADER, lines.first())
    val kinds = lines.drop(1).map { it.split(",")[3] }
    check("csv row kinds", kinds.first() == "start" && kinds[1] == "load" && kinds[2] == "warmup" &&
        kinds.count { it == "item" } == 9 && kinds.last() == "stop", kinds.toString())

    // S3a TTS
    val a = Summary.from(listOf(kokoroPass)).results.single()
    check("tts pass: RTF 0.6", a.verdict == Verdict.PASS && near(a.medianRtf, 0.6) && a.items == 9, "${a.verdict} ${a.medianRtf} ${a.reasons}")

    val b = Summary.from(listOf(bench("kokoro-en-v0_19", { 1.4 }))).results.single()
    check("tts fail: RTF 1.4", b.verdict == Verdict.FAIL && b.reasons.any { "median RTF above 1.0" in it }, "${b.verdict} ${b.reasons}")

    // call 0 is the warmup; one slow measured call out of 9 pushes p95 over the gate
    val c = Summary.from(listOf(bench("kokoro-en-v0_19", { if (it == 5) 1.5 else 0.8 }))).results.single()
    check("tts borderline: median ok, p95 over", c.verdict == Verdict.BORDERLINE && near(c.p95Rtf, 1.5), "${c.verdict} ${c.p95Rtf}")

    val w = Summary.from(listOf(bench("kokoro-en-v0_19", { if (it == 0) 5.0 else 0.5 }))).results.single()
    check("warmup excluded from stats", w.verdict == Verdict.PASS && near(w.p95Rtf, 0.5), "${w.verdict} ${w.p95Rtf}")

    val few = Summary.from(listOf(bench("kokoro-en-v0_19", { 0.5 }, rounds = 1))).results.single()
    check("too few items: incomplete", few.verdict == Verdict.INCOMPLETE, "${few.verdict} ${few.reasons}")

    val loadFail = Summary.from(listOf(bench("kokoro-en-v0_19", { 0.5 }, failLoad = true))).results.single()
    check("load failure: incomplete with error", loadFail.verdict == Verdict.INCOMPLETE && loadFail.errors == 1, "${loadFail.verdict} ${loadFail.reasons}")

    // Two runs of the same model and threads merge
    val merged = Summary.from(listOf(bench("kokoro-en-v0_19", { 0.5 }), bench("kokoro-en-v0_19", { 0.7 }))).results.single()
    check("runs merge", merged.runs == 2 && merged.items == 18 && near(merged.medianRtf, 0.6), "${merged.runs} ${merged.items} ${merged.medianRtf}")

    // S3b STT: RTF and memory
    val d = Summary.from(listOf(bench("streaming-zipformer-en-20M", { 0.3 }, pssMb = 250))).results.single()
    check("stt pass: RTF 0.3, 250 MB", d.verdict == Verdict.PASS && near(d.medianRtf, 0.3), "${d.verdict} ${d.reasons}")

    val e = Summary.from(listOf(bench("streaming-zipformer-en-20M", { 0.9 }))).results.single()
    check("stt fail: RTF 0.9 > 0.7", e.verdict == Verdict.FAIL && e.reasons.any { "median RTF above 0.7" in it }, "${e.verdict} ${e.reasons}")

    val f = Summary.from(listOf(bench("parakeet-tdt-0.6b-v2-int8", { 0.5 }, pssMb = 900))).results.single()
    check("stt memory fail: 900 MB PSS", f.verdict == Verdict.FAIL && f.reasons.any { "peak PSS above 600 MB" in it }, "${f.verdict} ${f.reasons}")

    val g = Summary.from(listOf(bench("parakeet-tdt-0.6b-v2-int8", { 0.5 }, pssMb = 550, hwmMb = 650))).results.single()
    check("stt memory borderline: PSS 550, RSS 650", g.verdict == Verdict.BORDERLINE, "${g.verdict} ${g.reasons}")

    check("stt memory exactly 600 MB passes",
        Summary.from(listOf(bench("parakeet-tdt-0.6b-v2-int8", { 0.5 }, pssMb = 600))).results.single().verdict == Verdict.PASS, "")

    // Process death (main process appends process_died after the binder dies) and missing stop
    val partial = bench("parakeet-tdt-0.6b-v2-int8", { 0.5 }).trim().lines().dropLast(1)
    val died = (partial + partial.last().split(",").toMutableList().also { it[3] = RunLog.PROCESS_DIED }.joinToString(",")).joinToString("\n")
    val h = Summary.from(listOf(died)).results.single()
    check("process died: fail", h.verdict == Verdict.FAIL && h.reasons.any { "died" in it }, "${h.verdict} ${h.reasons}")
    val i = Summary.from(listOf(partial.joinToString("\n"))).results.single()
    check("no stop: incomplete", i.verdict == Verdict.INCOMPLETE && i.reasons.any { "clean stop" in it }, "${i.verdict} ${i.reasons}")

    // Recommendations (PRD 0.7 "if it fails")
    val dev = "vivo-Y17s"
    val r1 = Summary.from(listOf(bench("kokoro-en-v0_19", { 1.6 }), bench("vits-piper-en_US-amy-low", { 0.3 })))
    check("rec: Kokoro fails, Piper passes -> lighter voice", "switch to the lighter voice" in r1.recommendationTts(dev), r1.recommendationTts(dev))
    val r2 = Summary.from(listOf(bench("kokoro-en-v0_19", { 1.6 }), bench("vits-piper-en_US-amy-low", { 1.2 })))
    check("rec: both fail -> no spoken replies", "Neither voice" in r2.recommendationTts(dev), r2.recommendationTts(dev))
    val r3 = Summary.from(listOf(kokoroPass))
    check("rec: Kokoro passes", "keep Kokoro" in r3.recommendationTts(dev), r3.recommendationTts(dev))
    val r4 = Summary.from(listOf(bench("kokoro-en-v0_19", { 1.6 })))
    check("rec: Piper missing -> measure", "measure both" in r4.recommendationTts(dev), r4.recommendationTts(dev))
    val r5 = Summary.from(listOf(bench("streaming-zipformer-en-20M", { 0.9 }), bench("parakeet-tdt-0.6b-v2-int8", { 0.5 }, pssMb = 900)))
    check("rec: STT fails -> push-to-talk + typed", "push-to-talk short clips; typed input fallback" in r5.recommendationStt(dev), r5.recommendationStt(dev))
    val r6 = Summary.from(listOf(bench("streaming-zipformer-en-20M", { 0.3 }), bench("parakeet-tdt-0.6b-v2-int8", { 0.5 }, pssMb = 900)))
    check("rec: streaming passes, Parakeet off", "use it as the default" in r6.recommendationStt(dev) && "keep it off" in r6.recommendationStt(dev), r6.recommendationStt(dev))
    val two = Summary.from(listOf(bench("kokoro-en-v0_19", { 0.5 }), bench("kokoro-en-v0_19", { 0.5 }, device = "SM-A235F")))
    check("devices kept apart", two.results.size == 2 && two.devices().size == 2, "${two.devices()}")

    // WAV I/O
    val wav = File(tmp, "t.wav")
    WavIo.writeMono16(wav, shortArrayOf(0, 16384, -16384, 32767), 4, 16_000)
    val clip = WavIo.read(wav)
    check("wav roundtrip", clip.sampleRate == 16_000 && clip.samples.size == 4 && abs(clip.samples[1] - 0.5f) < 1e-4, clip.samples.toList().toString())
    val stereo = wav.readBytes().also {
        it[22] = 2 // channels
        it[32] = 4 // block align
    }
    check("wav stereo -> mono average", WavIo.parse(stereo).samples.size == 2 && abs(WavIo.parse(stereo).samples[0] - 0.25f) < 1e-4, "")
    val eightBit = wav.readBytes().also { it[34] = 8 }
    check("wav 8-bit rejected", runCatching { WavIo.parse(eightBit) }.exceptionOrNull()?.message?.contains("16-bit") == true, "")
    check("not wav rejected", runCatching { WavIo.parse(ByteArray(64)) }.isFailure, "")

    // Model resolution: first existing candidate wins, missing roles listed
    val root = File(tmp, "models")
    val dir = File(root, "kokoro-int8-multi-lang-v1_1").apply { mkdirs() }
    listOf("model.onnx", "voices.bin", "tokens.txt").forEach { File(dir, it).writeText("x") }
    val res = Models.resolve(Models.byId("kokoro-int8-multi-lang-v1_1")!!, root)
    check("resolve: fallback candidate + missing lexicon", !res.ready && res.paths["model"]!!.endsWith("model.onnx") &&
        res.missing.single().startsWith("lexicon"), "${res.paths} ${res.missing}")

    println()
    println(r1.describe())
    tmp.deleteRecursively()
    println("ALL PASS")
}
