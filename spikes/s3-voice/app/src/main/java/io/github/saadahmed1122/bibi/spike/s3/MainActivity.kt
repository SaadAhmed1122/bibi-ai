package io.github.saadahmed1122.bibi.spike.s3

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.text.InputType
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * Plain-View control panel for spike S3 (TTS + STT benchmarks). Runs happen in
 * [InferenceService] in the `:inference` process; this process records clips, starts runs,
 * notices if the inference process dies, and shows and shares the results.
 */
class MainActivity : Activity() {

    private lateinit var exitLog: ExitLog
    private lateinit var status: TextView
    private lateinit var progress: TextView
    private lateinit var clipsView: TextView
    private lateinit var results: TextView
    private lateinit var threadsField: EditText
    private lateinit var roundsField: EditText
    private lateinit var sidField: EditText
    private lateinit var playBox: CheckBox
    private val recordButtons = mutableMapOf<String, Button>()

    private val device = RunLog.safe("${Build.MANUFACTURER}-${Build.MODEL}")
    private val runsDir by lazy { File(filesDir, "runs") }
    private val clipsDir by lazy { File(filesDir, "clips") }
    private val modelsRoot by lazy { getExternalFilesDir("models") ?: File(filesDir, "models") }
    private val prompts by lazy { readPairs("stt_prompts.txt") }

    private val recorder = Recorder(
        onFinished = { file, secs -> runOnUiThread { recordingDone("Saved ${file.name} (%.1f s)".format(secs)) } },
        onError = { msg -> runOnUiThread { recordingDone("Recording error: $msg") } },
    )
    private var recordingClip: String? = null
    private var pendingClip: String? = null

    /** The run in progress, or null. */
    private var run: ActiveRun? = null

    private class ActiveRun(val log: RunLog, val connection: ServiceConnection) {
        var done = false
    }

    private val incoming = Messenger(
        Handler(Looper.getMainLooper()) { msg ->
            val text = msg.data?.getString(InferenceService.ARG_TEXT).orEmpty()
            when (msg.what) {
                InferenceService.MSG_PROGRESS -> progress.text = text
                InferenceService.MSG_DONE -> finishRun("Finished: $text")
            }
            true
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exitLog = ExitLog(this)
        exitLog.logNewExitReasons()

        val pad = (16 * resources.displayMetrics.density).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
        fun text(size: Float, bold: Boolean = false) = TextView(this).apply {
            textSize = size
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, pad / 2, 0, pad / 2)
        }.also { column.addView(it) }
        fun button(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        }.also { column.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
        fun numberField(hint: String, value: String) = EditText(this).apply {
            this.hint = hint
            setText(value)
            inputType = InputType.TYPE_CLASS_NUMBER
        }.also { column.addView(it) }

        text(22f, bold = true).text = "Bibi — Spike S3 (voice)"
        status = text(13f)

        text(17f, bold = true).text = "Settings (apply to the next run)"
        text(13f).text = "CPU threads (try 2, then 4)"
        threadsField = numberField("threads", "2")
        text(13f).text = "Rounds over all sentences or clips (after one warmup)"
        roundsField = numberField("rounds", "3")
        text(13f).text = "TTS speaker ID (blank = model default)"
        sidField = numberField("speaker id", "")
        playBox = CheckBox(this).apply { text = "Play each sentence after measuring it (TTS)" }.also { column.addView(it) }

        text(17f, bold = true).text = "S3a — TTS (gate: RTF ≤ ${Gates.TTS_RTF_MAX})"
        for (spec in Models.forTask(Task.TTS)) button("Run TTS: ${spec.label}") { startRun(spec) }

        text(17f, bold = true).text = "S3b — STT clips (record once, re-used for every model)"
        for ((id, prompt) in prompts) {
            text(13f).text = "$id — read aloud: \"$prompt\""
            recordButtons[id] = button("Record $id") { toggleRecording(id) }
        }
        button("Add a WAV file (16-bit PCM)…") { pickWav() }
        clipsView = text(13f)

        text(17f, bold = true).text = "S3b — STT (gate: RTF ≤ ${Gates.STT_RTF_MAX}, peak ≤ ${Gates.STT_PEAK_MB_MAX.toInt()} MB)"
        for (spec in Models.forTask(Task.STT)) button("Run STT: ${spec.label}") { startRun(spec) }

        progress = text(14f, bold = true)
        text(17f, bold = true).text = "Results"
        button("Refresh results") { refresh() }
        button("Share newest run (CSV)") { shareNewest() }
        button("Share all runs (CSV)") { shareAll() }
        button("Share summary") { shareText("Bibi S3 summary — $device", summary().describe()) }
        button("Share exit reasons") { shareExitReasons() }
        results = text(12f).apply { typeface = Typeface.MONOSPACE }

        setContentView(ScrollView(this).apply { addView(column) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroy() {
        recorder.stop()
        run?.let {
            if (!it.done) exitLog.diagnostic("run_abandoned", "activity destroyed during ${it.log.file.name}")
            unbindService(it.connection)
        }
        run = null
        super.onDestroy()
    }

    // ---- Runs -------------------------------------------------------------------------------

    private fun startRun(spec: ModelSpec) {
        if (run != null) return toast("A run is already going")
        if (recorder.isRecording) return toast("Stop the recording first")
        val resolution = Models.resolve(spec, modelsRoot)
        if (!resolution.ready) {
            results.text = "Missing files for ${spec.id} in ${File(modelsRoot, spec.dirName)}:\n" +
                resolution.missing.joinToString("\n") { "  $it" } + "\nSource: ${spec.source}\nSee README.md, \"Model files\"."
            return
        }
        val clips = clipFiles()
        if (spec.family.task == Task.STT && clips.isEmpty()) return toast("Record or add at least one clip first")
        val threads = threadsField.text.toString().toIntOrNull()?.coerceIn(1, 8) ?: 2
        val rounds = roundsField.text.toString().toIntOrNull()?.coerceIn(1, 20) ?: 3
        val sid = sidField.text.toString().toIntOrNull() ?: spec.defaultSid

        val now = System.currentTimeMillis()
        val file = File(runsDir, RunLog.fileName(now, device, spec.family.task, spec, threads))
        val log = RunLog(file, now.toString(), device, spec.family.task, spec, threads)
        val args = Bundle().apply {
            putString(InferenceService.ARG_MODEL, spec.id)
            putInt(InferenceService.ARG_THREADS, threads)
            putInt(InferenceService.ARG_ROUNDS, rounds)
            putInt(InferenceService.ARG_SID, sid)
            putBoolean(InferenceService.ARG_PLAY, playBox.isChecked)
            putString(InferenceService.ARG_RUN_FILE, file.absolutePath)
            putString(InferenceService.ARG_RUN_ID, now.toString())
            putString(InferenceService.ARG_DEVICE, device)
            putString(InferenceService.ARG_MODELS_ROOT, modelsRoot.absolutePath)
            putStringArray(InferenceService.ARG_CLIP_PATHS, clips.map { it.absolutePath }.toTypedArray())
            putStringArray(InferenceService.ARG_CLIP_REFS, clips.map { prompts[it.nameWithoutExtension].orEmpty() }.toTypedArray())
        }
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                val msg = Message.obtain(null, InferenceService.MSG_RUN).apply {
                    data = args
                    replyTo = incoming
                }
                try {
                    Messenger(service).send(msg)
                } catch (e: RemoteException) {
                    inferenceDied("send failed: ${e.message}")
                }
            }

            // Called when the service's process dies (bound-services guide), i.e. before MSG_DONE.
            override fun onServiceDisconnected(name: ComponentName) = inferenceDied("service disconnected")

            override fun onBindingDied(name: ComponentName) = inferenceDied("binding died")
        }
        run = ActiveRun(log, connection)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        progress.text = "Starting ${spec.id} with $threads threads…"
        if (!bindService(Intent(this, InferenceService::class.java), connection, Context.BIND_AUTO_CREATE)) {
            finishRun("Could not bind the inference service")
        }
    }

    /** The inference process went away before reporting MSG_DONE: OOM kill, crash, or ANR. */
    private fun inferenceDied(how: String) {
        val current = run ?: return
        if (current.done) return
        current.log.row(RunLog.PROCESS_DIED, detail = how)
        exitLog.diagnostic("inference_died", "${current.log.file.name}: $how")
        // ApplicationExitInfo may appear a moment later.
        progress.postDelayed({ exitLog.logNewExitReasons(); refresh() }, 2_000)
        finishRun("Inference process died ($how). See exit reasons.")
    }

    private fun finishRun(message: String) {
        val current = run ?: return
        current.done = true
        run = null
        try {
            unbindService(current.connection)
        } catch (e: IllegalArgumentException) {
            // Not bound (bindService returned false).
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        progress.text = message
        refresh()
    }

    // ---- Clips ------------------------------------------------------------------------------

    private fun toggleRecording(id: String) {
        if (recorder.isRecording) {
            if (recordingClip == id) recorder.stop() else toast("Stop the current recording first")
            return
        }
        if (run != null) return toast("Wait for the run to finish")
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // Asked only on first use of the microphone (PRD 0.9).
            pendingClip = id
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            return
        }
        recordingClip = id
        recordButtons[id]?.text = "Stop recording $id"
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        recorder.start(File(clipsDir, "$id.wav"))
    }

    private fun recordingDone(message: String) {
        recordingClip?.let { recordButtons[it]?.text = "Record $it" }
        recordingClip = null
        if (run == null) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        toast(message)
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val clip = pendingClip
        pendingClip = null
        if (requestCode != REQ_MIC) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && clip != null) {
            toggleRecording(clip)
        } else {
            toast("Microphone denied: add WAV files instead")
        }
    }

    private fun pickWav() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("audio/*")
        startActivityForResult(intent, REQ_PICK)
    }

    @Deprecated("Framework Activity result API; the spike has no androidx")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || uri == null) return
        try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return toast("Can't read file")
            val clip = WavIo.parse(bytes) // validate before keeping it
            val n = (clipFiles().count { it.name.startsWith("picked_") } + 1)
            val target = File(clipsDir, "picked_$n.wav")
            clipsDir.mkdirs()
            target.writeBytes(bytes)
            toast("Added ${target.name} (%.1f s, %d Hz)".format(clip.seconds, clip.sampleRate))
        } catch (e: Exception) {
            toast("Not usable: ${e.message}")
        }
        refresh()
    }

    private fun clipFiles(): List<File> =
        (clipsDir.listFiles { f -> f.isFile && f.name.endsWith(".wav") } ?: emptyArray()).sortedBy { it.name }

    // ---- Results ----------------------------------------------------------------------------

    private fun runFiles(): List<File> =
        (runsDir.listFiles { f -> f.isFile && f.name.endsWith(".csv") } ?: emptyArray())
            .sortedWith(compareBy<File>({ it.lastModified() }, { it.name }))

    private fun summary() = Summary.from(runFiles().map { it.readText() })

    private fun refresh() {
        val sherpa = try {
            Class.forName("com.k2fsa.sherpa.onnx.OfflineTts", false, classLoader)
            "sherpa-onnx ${SherpaEngines.VERSION} classes present"
        } catch (e: ClassNotFoundException) {
            "sherpa-onnx classes MISSING (AAR not in the build)"
        }
        status.text = buildString {
            appendLine("Device: $device, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Runtime: $sherpa")
            appendLine("Models folder: $modelsRoot")
            for (spec in Models.all) {
                val r = Models.resolve(spec, modelsRoot)
                appendLine("  ${spec.id}: ${if (r.ready) "ready" else "missing ${r.missing.size} file(s)"}")
            }
            append("Microphone: ${if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) "granted" else "not granted (asked on first recording)"}")
        }
        clipsView.text = clipFiles().joinToString("\n") { f ->
            val secs = runCatching { WavIo.read(f).seconds }.getOrNull()
            "${f.name}: ${secs?.let { "%.1f s".format(it) } ?: "unreadable"}"
        }.ifEmpty { "No clips yet." }
        val exits = exitLog.read().lineSequence().drop(1).filter { it.isNotBlank() }.toList()
        results.text = buildString {
            appendLine("Run files: ${runFiles().size}")
            appendLine()
            appendLine(summary().describe())
            appendLine()
            appendLine("Last exit reasons (${exits.size} total; EXIT_SELF of :inference after a run is expected):")
            if (exits.isEmpty()) appendLine("  none")
            exits.takeLast(5).forEach { appendLine("  $it") }
        }
    }

    private fun shareNewest() {
        val newest = runFiles().lastOrNull() ?: return toast("No run files yet")
        shareText("Bibi S3 run ${newest.name}", newest.readText())
    }

    /** All run files in one text, each preceded by a `# file: <name>` line. */
    private fun shareAll() {
        val files = runFiles()
        if (files.isEmpty()) return toast("No run files yet")
        shareText("Bibi S3 runs — $device (${files.size} files)", files.joinToString("\n") { "# file: ${it.name}\n${it.readText()}" })
    }

    private fun shareExitReasons() {
        val csv = exitLog.read()
        if (csv.isEmpty()) return toast("No exit reasons recorded")
        shareText("Bibi S3 exit reasons — $device", csv)
    }

    private fun shareText(subject: String, body: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        startActivity(Intent.createChooser(send, subject))
    }

    private fun readPairs(asset: String): Map<String, String> =
        assets.open(asset).bufferedReader().readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && '|' in it }
            .associate { it.substringBefore('|').trim() to it.substringAfter('|').trim() }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        private const val REQ_MIC = 1
        private const val REQ_PICK = 2
    }
}
