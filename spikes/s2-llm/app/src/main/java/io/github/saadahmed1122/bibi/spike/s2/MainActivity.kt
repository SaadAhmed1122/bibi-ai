package io.github.saadahmed1122.bibi.spike.s2

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * Plain-View control panel for S2 (UI process). Lists model files, starts and stops runs in
 * `:inference` via [InferenceService], shows the newest run's [Summary], and shares the logs.
 */
class MainActivity : Activity() {

    private lateinit var log: BenchLog
    private lateinit var status: TextView
    private lateinit var progress: TextView
    private lateinit var models: LinearLayout
    private lateinit var results: TextView

    private var service: Messenger? = null
    private var busy = false
    private var runFile: String? = null

    private val replies = Messenger(
        Handler(Looper.getMainLooper()) { msg ->
            if (msg.what == InferenceService.MSG_STATUS) onStatus(msg.data)
            true
        },
    )

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = Messenger(binder)
            send(InferenceService.MSG_REGISTER)
        }

        /** Only called when `:inference` crashed or was killed, not on unbind. */
        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            val file = runFile?.let { File(log.runsDir, it) }
            if (busy && file != null && file.exists()) {
                log.write(file, "", "inference_died", "service disconnected", "", "")
                progress.text = "The inference process died during the run. Checking exit reasons …"
            }
            busy = false
            keepScreenOn(false)
            // ActivityManager may record the exit a moment after the disconnect.
            progress.postDelayed({
                log.logNewExitReasons()
                refresh()
            }, EXIT_REASON_DELAY_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log = BenchLog(this)
        log.logNewExitReasons()
        modelDirs().forEach { it.mkdirs() }

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
            setOnClickListener { onClick() }
        }.also { column.addView(it, fullWidth()) }

        text(22f, bold = true).text = "Bibi — Spike S2 (local LLM)"
        status = text(14f)
        models = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }.also { column.addView(it, fullWidth()) }
        button("Rescan model files") { refresh() }
        button("Stop run") { if (busy) send(InferenceService.MSG_STOP) else toast("Not running") }
        progress = text(14f, bold = true)
        button("Share newest run (CSV)") { shareNewestRun() }
        button("Share exit reasons") { shareExitReasons() }
        button("Share summary") { shareText("Bibi S2 summary — ${Build.MODEL}", results.text.toString()) }
        results = text(13f).apply { typeface = Typeface.MONOSPACE }

        setContentView(ScrollView(this).apply { addView(column) })
        bindService(Intent(this, InferenceService::class.java), connection, Context.BIND_AUTO_CREATE)
    }

    override fun onResume() {
        super.onResume()
        log.logNewExitReasons()
        refresh()
    }

    override fun onDestroy() {
        unbindService(connection)
        super.onDestroy()
    }

    private fun onStatus(data: Bundle) {
        val wasBusy = busy
        busy = data.getBoolean(InferenceService.KEY_BUSY)
        runFile = data.getString(InferenceService.KEY_RUN_FILE) ?: runFile
        progress.text = data.getString(InferenceService.KEY_STATUS).orEmpty()
        keepScreenOn(busy)
        if (wasBusy != busy) refresh()
    }

    /** `files/models` on shared storage (adb push target) and in private storage (fallback). */
    private fun modelDirs(): List<File> = listOfNotNull(getExternalFilesDir("models"), File(filesDir, "models"))

    private fun modelFiles(): List<File> = modelDirs()
        .flatMap { dir -> dir.listFiles { f -> f.isFile && f.name.endsWith(".litertlm") }?.toList().orEmpty() }
        .sortedBy { it.name }

    private fun start(model: File, mode: String) {
        if (busy) {
            toast("A run is going — stop it first")
            return
        }
        val msg = Message.obtain(null, InferenceService.MSG_START)
        msg.data = Bundle().apply {
            putString(InferenceService.KEY_PATH, model.absolutePath)
            putString(InferenceService.KEY_MODE, mode)
        }
        send(msg)
    }

    private fun send(what: Int) = send(Message.obtain(null, what))

    private fun send(msg: Message) {
        val s = service
        if (s == null) {
            toast("Inference service not connected yet")
            return
        }
        msg.replyTo = replies
        try {
            s.send(msg)
        } catch (e: RemoteException) {
            toast("Inference service gone: ${e.javaClass.simpleName}")
        }
    }

    private fun refresh() {
        val files = modelFiles()
        status.text = buildString {
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Model folder (adb push here):")
            appendLine("  ${modelDirs().first().absolutePath}")
            append("Model files: ${files.size}")
        }
        models.removeAllViews()
        for (f in files) {
            val mb = f.length() / (1024 * 1024)
            models.addView(Button(this).apply {
                text = "Soak 50: ${f.name} ($mb MB)"
                setOnClickListener { start(f, InferenceService.MODE_SOAK) }
            }, fullWidth())
            models.addView(Button(this).apply {
                text = "Chat soak 50: ${f.name}"
                setOnClickListener { start(f, InferenceService.MODE_CHAT) }
            }, fullWidth())
            models.addView(Button(this).apply {
                text = "Smoke 3: ${f.name}"
                setOnClickListener { start(f, InferenceService.MODE_SMOKE) }
            }, fullWidth())
        }
        val newest = log.runFiles().lastOrNull()
        val exitCsv = log.readExitReasons()
        val exits = exitCsv.lineSequence().drop(1).filter { it.isNotBlank() }.toList()
        results.text = buildString {
            appendLine("Newest run: ${newest?.name ?: "none"}")
            appendLine()
            appendLine(Summary.from(newest?.readText().orEmpty(), exitCsv).describe())
            appendLine()
            appendLine("Last exit reasons (${exits.size} total; ${BenchLog.EXIT_HEADER}):")
            if (exits.isEmpty()) appendLine("  none")
            exits.takeLast(5).forEach { appendLine("  $it") }
        }
    }

    private fun keepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun shareNewestRun() {
        val newest = log.runFiles().lastOrNull() ?: return toast("No run files yet")
        shareText("Bibi S2 run ${newest.name}", newest.readText())
    }

    private fun shareExitReasons() {
        val csv = log.readExitReasons()
        if (csv.isEmpty()) return toast("No exit reasons recorded")
        shareText("Bibi S2 exit reasons — ${Build.MODEL}", csv)
    }

    private fun shareText(subject: String, body: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        startActivity(Intent.createChooser(send, subject))
    }

    private fun fullWidth() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    companion object {
        private const val EXIT_REASON_DELAY_MS = 2_000L
    }
}
