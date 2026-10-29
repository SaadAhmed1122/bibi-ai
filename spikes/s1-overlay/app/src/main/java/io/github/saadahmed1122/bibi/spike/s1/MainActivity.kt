package io.github.saadahmed1122.bibi.spike.s1

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Plain-View control panel for the S1 spike: permissions, run controls, results, log sharing. */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var log: SpikeLog
    private lateinit var status: TextView
    private lateinit var results: TextView
    private lateinit var calmButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        log = SpikeLog(this)
        log.logNewExitReasons(prefs)

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
        }.also { column.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }

        text(22f, bold = true).text = "Bibi — Spike S1 (overlay)"
        status = text(15f)

        button("1. Allow drawing over other apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        button("2. Allow notifications") {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        button("Start Bibi") { start(OverlayService.ACTION_START) }
        button("Start baseline (no Bibi)") { start(OverlayService.ACTION_BASELINE) }
        calmButton = button("") { toggleCalm() }
        button("Stop") { send(OverlayService.ACTION_STOP) }
        button("Refresh results") { refresh() }
        button("Share log (CSV)") { shareLog() }
        button("Clear log") {
            if (OverlayService.isRunning) {
                toast("Stop the run first")
            } else {
                log.clear()
                refresh()
            }
        }
        results = text(13f).apply { typeface = Typeface.MONOSPACE }

        setContentView(ScrollView(this).apply { addView(column) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    private fun start(action: String) {
        if (action == OverlayService.ACTION_START && !Settings.canDrawOverlays(this)) {
            toast("Allow drawing over other apps first (step 1)")
            return
        }
        if (OverlayService.isRunning) {
            toast("Already running — stop first to switch modes")
            return
        }
        startForegroundService(Intent(this, OverlayService::class.java).setAction(action))
        results.postDelayed({ refresh() }, 500)
    }

    private fun send(action: String) {
        if (!OverlayService.isRunning) {
            toast("Not running")
            return
        }
        startService(Intent(this, OverlayService::class.java).setAction(action))
        results.postDelayed({ refresh() }, 500)
    }

    private fun toggleCalm() {
        val calm = !prefs.calm
        if (OverlayService.isRunning) {
            startService(
                Intent(this, OverlayService::class.java)
                    .setAction(OverlayService.ACTION_SET_CALM)
                    .putExtra(OverlayService.EXTRA_CALM, calm),
            )
        } else {
            prefs.calm = calm
        }
        results.postDelayed({ refresh() }, 300)
    }

    private fun refresh() {
        val overlay = Settings.canDrawOverlays(this)
        val notifications = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        status.text = buildString {
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Overlay permission: ${if (overlay) "yes" else "NO"}")
            appendLine("Notifications: ${if (notifications) "yes" else "NO"}")
            append("Service: ${if (OverlayService.isRunning) "running (${if (prefs.baseline) "baseline" else "Bibi"})" else "stopped"}")
        }
        calmButton.text = "Calm mode: ${if (prefs.calm) "ON (2 fps)" else "off (12 fps)"}"
        results.text = Summary.from(log.read()).describe()
    }

    private fun shareLog() {
        val csv = log.read()
        if (csv.isEmpty()) {
            toast("Log is empty")
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Bibi S1 log — ${Build.MODEL}")
            putExtra(Intent.EXTRA_TEXT, csv)
        }
        startActivity(Intent.createChooser(send, "Share S1 log"))
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
