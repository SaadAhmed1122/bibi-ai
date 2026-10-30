package io.github.saadahmed1122.bibi.spike.s4

import android.app.Activity
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.AlarmClock
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * S4 spike screen (plain views, throwaway). One path for every action:
 * text → ShortcutParser or FakeLlm + ToolRouter → PermissionEngine → (confirmation) → ToolExecutor.
 * With "Real LLM path" on, a shortcut miss (and only a miss: the shortcut path is unchanged and
 * never waits for a model) goes Decision Maker → Gemma tool call (one tool exposed) →
 * LlmToolRouter (parse, validate, one repair, exposed-subset check) → the same PermissionEngine.
 *
 * Timing (SystemClock.elapsedRealtimeNanos, see SPEC.md §3):
 * t0 = submit (button tap handler entered) → parse done → policy done → intent/torch call built →
 * dispatch = startActivity() returned (real) or resolveActivity() returned (dry run) or
 * setTorchMode() returned (torch). total_ms = dispatch − t0, except torch: total_ms = the matching
 * onTorchModeChanged callback − t0. After a confirmation tap, t0 restarts at the tap.
 */
class MainActivity : Activity() {

    private lateinit var log: SpikeLog
    private lateinit var executor: ToolExecutor
    private lateinit var torch: TorchController
    private lateinit var keyguard: KeyguardManager
    private val engine = PermissionEngine(nowMs = { SystemClock.elapsedRealtime() })
    private val fakeLlm = FakeLlm()
    private val handler = Handler(Looper.getMainLooper())
    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private val scope = MainScope()
    private lateinit var pipeline: LlmPipeline
    private var llmJob: Job? = null
    private var llmBenchJob: Job? = null
    private var loadJob: Job? = null
    private var loadLine = "not loaded"

    private var apps: AppResolver? = null
    private var handlersLine = ""

    private lateinit var input: EditText
    private lateinit var fakeLlmBox: CheckBox
    private lateinit var realLlmBox: CheckBox
    private lateinit var modelStatus: TextView
    private lateinit var llmBenchButton: Button
    private lateinit var taintBox: CheckBox
    private lateinit var dryRunBox: CheckBox
    private lateinit var killBox: CheckBox
    private lateinit var status: TextView
    private lateinit var lastResult: TextView
    private lateinit var results: TextView
    private lateinit var benchButton: Button

    private val benchSteps = ArrayDeque<Request>()
    private var benchRunning = false
    private var torchTimeout: Runnable? = null
    private var dialog: AlertDialog? = null

    /** One submitted command. [ctx] null = from the checkboxes; [onDone] gets success. */
    private data class Request(
        val text: String,
        val kind: String,
        val path: String,
        val dryRun: Boolean,
        val autoConfirm: Boolean,
        val ctx: PolicyContext?,
        val onDone: (Boolean) -> Unit = {},
        /** On a shortcut miss, try the real LLM path instead of stopping at no_match. */
        val realLlm: Boolean = false,
    )

    private class Timing(val t0: Long) {
        var parse = t0
        var policy = t0
        var build = t0
        var dispatch = t0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log = SpikeLog(this)
        log.beginRun()
        executor = ToolExecutor(this)
        torch = TorchController(this)
        keyguard = getSystemService(KeyguardManager::class.java)
        pipeline = LlmPipeline(this)
        setContentView(buildUi())
        refreshModelStatus()
        loadAppIndex()
        refreshResults()
    }

    override fun onStart() {
        super.onStart()
        torch.start()
    }

    override fun onStop() {
        if (benchRunning) abortBenchmark("activity stopped")
        if (llmBenchJob?.isActive == true) stopLlmBenchmark("activity stopped")
        torchTimeout?.let { handler.removeCallbacks(it) }
        torchTimeout = null
        torch.stop()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        dialog?.dismiss()
        io.shutdownNow()
        scope.cancel()
        pipeline.close()
        super.onDestroy()
    }

    // ---- UI -----------------------------------------------------------------------------------

    private fun buildUi(): View {
        val pad = dp(12)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun text(s: String, mono: Boolean = false) = TextView(this).apply {
            text = s
            if (mono) typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        fun button(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
        }
        fun box(label: String, checked: Boolean = false) = CheckBox(this).apply {
            text = label
            isChecked = checked
        }

        col.addView(text("Bibi S4 spike — five actions through one Permission Engine").apply { textSize = 18f })
        status = text("Loading launcher apps…")
        col.addView(status)

        input = EditText(this).apply {
            hint = "e.g. set a timer for 5 minutes"
            setSingleLine()
        }
        col.addView(input)
        col.addView(button("Submit") { submitLive(input.text.toString()) })

        fakeLlmBox = box("Fake LLM path (fixed mappings, baseline; text skips the shortcut matcher)")
        realLlmBox = box("Real LLM path (on a shortcut miss: Decision Maker → Gemma 3 1B tool call)")
        // One model path at a time.
        fakeLlmBox.setOnCheckedChangeListener { _, on -> if (on) realLlmBox.isChecked = false }
        realLlmBox.setOnCheckedChangeListener { _, on -> if (on) fakeLlmBox.isChecked = false }
        taintBox = box("Untrusted content in context (simulated notification text → taint)")
        dryRunBox = box("Dry run (resolve the intent, don't launch)")
        killBox = box("Disable all actions (kill switch)")
        listOf(fakeLlmBox, realLlmBox, taintBox, dryRunBox, killBox).forEach(col::addView)
        modelStatus = text("", mono = true).apply { textSize = 11f }
        col.addView(modelStatus)
        col.addView(button("Load models") { loadModels() })
        col.addView(button("Rescan model files") { refreshModelStatus() })

        col.addView(text("Quick commands:"))
        for (cmd in QUICK) {
            col.addView(button(cmd) {
                input.setText(cmd)
                submitLive(cmd)
            })
        }
        col.addView(text("Fake LLM phrases: " + FakeLlm.PHRASES.joinToString(" · ")))

        lastResult = text("", mono = true)
        col.addView(lastResult)

        benchButton = button("Benchmark (20× per action, shortcut path, dry run + torch)") { startBenchmark() }
        col.addView(benchButton)
        llmBenchButton = button(LLM_BENCH_LABEL) { toggleLlmBenchmark() }
        col.addView(llmBenchButton)
        col.addView(button("Refresh results") { refreshResults() })
        col.addView(button("Share log (CSV)") { shareLog() })
        col.addView(button("New run file") {
            log.beginRun()
            refreshResults()
            toast("New run: ${log.current?.name}")
        })
        results = text("", mono = true).apply { textSize = 11f }
        col.addView(results)

        return ScrollView(this).apply { addView(col) }
    }

    private fun show(line: String) {
        lastResult.text = line
    }

    // ---- App index and handler check ------------------------------------------------------------

    private fun loadAppIndex() {
        io.execute {
            val t0 = SystemClock.elapsedRealtimeNanos()
            val list = AppIndex.load(packageManager)
            val ms = (SystemClock.elapsedRealtimeNanos() - t0) / 1e6
            handler.post {
                if (isDestroyed) return@post
                apps = AppResolver(list)
                log.event("app_index", "${list.size} launcher apps", totalMs = ms)
                handlersLine = handlerCheck()
                status.text = "Launcher apps: ${list.size} (indexed in ${"%.0f".format(ms)} ms)\n$handlersLine"
            }
        }
    }

    /** Which app handles each intent on this phone (logged once; answers the Samsung SET_ALARM [VERIFY]). */
    private fun handlerCheck(): String {
        val alarm = executor.resolve(Intent(AlarmClock.ACTION_SET_ALARM)) ?: "NONE"
        val timer = executor.resolve(Intent(AlarmClock.ACTION_SET_TIMER)) ?: "NONE"
        val dial = executor.resolve(Intent(Intent.ACTION_DIAL, android.net.Uri.fromParts("tel", "0123456789", null))) ?: "NONE"
        val flash = torch.cameraId?.let { "camera $it" } ?: "NONE"
        val line = "SET_ALARM → $alarm · SET_TIMER → $timer · DIAL → $dial · flash → $flash"
        log.event("handlers", line)
        return line
    }

    // ---- Submit path ------------------------------------------------------------------------------

    private fun submitLive(text: String) {
        if (benchRunning || llmBenchJob?.isActive == true) {
            toast("Benchmark running")
            return
        }
        submit(
            Request(
                text = text,
                kind = "live",
                path = if (fakeLlmBox.isChecked) "fake_llm" else "shortcut",
                dryRun = dryRunBox.isChecked,
                autoConfirm = false,
                ctx = null,
                realLlm = realLlmBox.isChecked && !fakeLlmBox.isChecked,
            ),
        )
    }

    private fun policyContext(r: Request) = r.ctx ?: PolicyContext(
        provenance = if (taintBox.isChecked) setOf(Provenance.USER_TYPED, Provenance.NOTIFICATION) else setOf(Provenance.USER_TYPED),
        deviceLocked = keyguard.isDeviceLocked,
        actionsEnabled = !killBox.isChecked,
    )

    private fun submit(r: Request) {
        val t = Timing(SystemClock.elapsedRealtimeNanos())
        val call: ToolCall = if (r.path == "fake_llm") {
            when (val o = ToolRouter.fromModel(fakeLlm.complete(r.text)) { fakeLlm.repair(it) }) {
                is RouterOutcome.Call -> {
                    if (o.attempts > 1) log.event("router", "repaired after one repair message", path = r.path, action = o.call.tool.wire)
                    o.call
                }
                is RouterOutcome.AskUser -> {
                    log.event("router", "ask_user: ${o.errors.joinToString("; ")}", path = r.path)
                    show("LLM path: rejected twice → ask user.\n${o.question}\nErrors: ${o.errors.joinToString("; ")}")
                    return r.onDone(false)
                }
                RouterOutcome.NoToolCall -> {
                    log.event("router", "no_tool_call", path = r.path)
                    show("LLM path: the model answered with text, no tool call.")
                    return r.onDone(false)
                }
            }
        } else {
            ShortcutParser.parse(r.text) ?: run {
                // The Decision Maker and the LLM run only after a miss, never before or inside the matcher.
                if (r.realLlm) return startLlm(r.copy(path = "llm"), t)
                log.event("no_match", "shortcut matcher: no match", path = r.path)
                show("No shortcut matched. In the app this would go to the LLM.")
                return r.onDone(false)
            }
        }
        t.parse = SystemClock.elapsedRealtimeNanos()
        handleDecision(call, evaluate(call, t, r), t, r)
    }

    /** Permission Engine decision for [call]; sets [Timing.policy]. */
    private fun evaluate(call: ToolCall, t: Timing, r: Request): Decision {
        val target = (call as? ToolCall.OpenApp)?.let { apps?.resolve(it.app) }?.let { "${it.label} (${it.packageName})" }
        val decision = engine.evaluate(call, policyContext(r), target)
        t.policy = SystemClock.elapsedRealtimeNanos()
        return decision
    }

    private fun handleDecision(call: ToolCall, decision: Decision, t: Timing, r: Request) {
        when (decision) {
            is Decision.Allow -> execute(call, t, r, note = "")
            is Decision.Deny -> {
                log.event("denied", "${call.redacted()}: ${decision.reason}", action = call.tool.wire, path = r.path)
                show("Denied: ${decision.reason}")
                r.onDone(false)
            }
            is Decision.NeedsConfirmation -> {
                if (r.autoConfirm) {
                    // Benchmark only: the machine confirms at once, so no human time is in the number.
                    when (val res = engine.respond(decision.request.id, UserResponse.CONFIRM, keyguard.isDeviceLocked)) {
                        is Resolution.Execute -> execute(res.call, t, r, note = "auto_confirmed")
                        is Resolution.Cancelled -> {
                            log.event("confirm_result", res.reason, action = call.tool.wire, path = r.path)
                            r.onDone(false)
                        }
                    }
                } else {
                    showConfirmation(decision.request, t, r)
                }
            }
        }
    }

    private fun showConfirmation(req: ConfirmationRequest, t: Timing, r: Request) {
        val shown = SystemClock.elapsedRealtimeNanos()
        val action = req.call.tool.wire
        log.event("confirm_shown", "${req.call.redacted()}; ${req.reasons.joinToString("; ")}", totalMs = ms(t.t0, shown), action = action, path = r.path)
        var handled = false
        val timeout = Runnable {
            if (handled) return@Runnable
            handled = true
            engine.expire()
            dialog?.dismiss()
            log.event("confirm_result", "timeout (cancelled)", action = action, path = r.path)
            show("Confirmation timed out after ${engine.timeoutMs / 1000} s: cancelled.")
            r.onDone(false)
        }
        fun answer(response: UserResponse) {
            if (handled) return
            handled = true
            handler.removeCallbacks(timeout)
            val tap = SystemClock.elapsedRealtimeNanos()
            when (val res = engine.respond(req.id, response, keyguard.isDeviceLocked)) {
                is Resolution.Execute -> {
                    log.event("confirm_result", "confirmed", totalMs = ms(shown, tap), action = action, path = r.path)
                    execute(res.call, Timing(tap), r, note = "after_confirm")
                }
                is Resolution.Cancelled -> {
                    log.event("confirm_result", res.reason, action = action, path = r.path)
                    show("Cancelled: ${res.reason}")
                    r.onDone(false)
                }
            }
        }
        dialog = AlertDialog.Builder(this)
            .setTitle("Confirm")
            .setMessage(req.summary + "\n\nWhy: " + req.reasons.joinToString("; "))
            .setPositiveButton(if (req.call is ToolCall.Dial) "Open dialer" else "Do it") { _, _ -> answer(UserResponse.CONFIRM) }
            .setNegativeButton("Cancel") { _, _ -> answer(UserResponse.CANCEL) }
            .setOnCancelListener { answer(UserResponse.CANCEL) }
            .show()
        handler.postDelayed(timeout, engine.timeoutMs)
    }

    private fun execute(call: ToolCall, t: Timing, r: Request, note: String) {
        val built = executor.build(call, apps)
        t.build = SystemClock.elapsedRealtimeNanos()
        val action = call.tool.wire
        fun row(mode: String, ok: Boolean, end: Long, detail: String) = log.action(
            kind = r.kind, action = action, path = r.path, mode = mode, ok = ok,
            totalMs = ms(t.t0, end), parseMs = ms(t.t0, t.parse), policyMs = ms(t.parse, t.policy),
            buildMs = ms(t.policy, t.build), dispatchMs = ms(t.build, t.dispatch),
            detail = listOf(call.redacted(), note, detail).filter { it.isNotEmpty() }.joinToString("; "),
        )
        when (built) {
            is ToolExecutor.Built.Error -> {
                t.dispatch = SystemClock.elapsedRealtimeNanos()
                row(if (r.dryRun) "dry_run" else "real", false, t.dispatch, built.reason)
                show("$action FAILED: ${built.reason}")
                r.onDone(false)
            }
            is ToolExecutor.Built.Launch -> {
                val mode = if (r.dryRun) "dry_run" else "real"
                val err: String?
                val okDetail: String
                if (r.dryRun) {
                    val pkg = executor.resolve(built.intent)
                    t.dispatch = SystemClock.elapsedRealtimeNanos()
                    err = if (pkg == null) "no handler resolved" else null
                    okDetail = "resolved=$pkg"
                } else {
                    err = executor.launch(built.intent)
                    t.dispatch = SystemClock.elapsedRealtimeNanos()
                    okDetail = "target=${built.target}"
                }
                row(mode, err == null, t.dispatch, err ?: okDetail)
                show("$action via ${r.path} ($mode): ${if (err == null) "OK" else "FAILED $err"} in ${"%.1f".format(ms(t.t0, t.dispatch))} ms")
                r.onDone(err == null)
            }
            is ToolExecutor.Built.Torch -> executeTorch(built.on, t, r, ::row)
        }
    }

    private fun executeTorch(on: Boolean, t: Timing, r: Request, row: (String, Boolean, Long, String) -> Unit) {
        if (torch.isOn == on) {
            // No state change → no callback to wait for. Logged as a failed attempt so it never counts as timed.
            t.dispatch = SystemClock.elapsedRealtimeNanos()
            row("torch", false, t.dispatch, "already ${if (on) "on" else "off"}; not timed")
            show("Flashlight is already ${if (on) "on" else "off"}.")
            return r.onDone(false)
        }
        val timeout = Runnable {
            torchTimeout = null
            torch.cancelWait()
            row("torch", false, SystemClock.elapsedRealtimeNanos(), "no onTorchModeChanged within $TORCH_TIMEOUT_MS ms")
            show("flashlight FAILED: no torch callback")
            r.onDone(false)
        }
        val err = torch.set(on) { callbackNs ->
            torchTimeout?.let { handler.removeCallbacks(it) }
            torchTimeout = null
            row("torch", true, callbackNs, "setTorchMode returned at ${"%.3f".format(ms(t.t0, t.dispatch))} ms")
            show("flashlight ${if (on) "on" else "off"} via ${r.path}: OK, callback in ${"%.1f".format(ms(t.t0, callbackNs))} ms")
            r.onDone(true)
        }
        t.dispatch = SystemClock.elapsedRealtimeNanos()
        if (err != null) {
            row("torch", false, t.dispatch, err)
            show("flashlight FAILED: $err")
            return r.onDone(false)
        }
        torchTimeout = timeout
        handler.postDelayed(timeout, TORCH_TIMEOUT_MS)
    }

    // ---- Benchmark -------------------------------------------------------------------------------

    private fun startBenchmark() {
        if (benchRunning) return
        if (apps == null) return toast("Launcher apps still loading")
        // Model work on other cores would skew the gate numbers.
        if (llmBenchJob?.isActive == true || llmJob?.isActive == true || loadJob?.isActive == true) {
            return toast("Wait for the LLM path to finish")
        }
        val ctx = PolicyContext(setOf(Provenance.USER_TYPED), deviceLocked = keyguard.isDeviceLocked)
        fun req(text: String, path: String) =
            Request(text, kind = "bench", path = path, dryRun = true, autoConfirm = true, ctx = ctx, onDone = { nextBenchStep() })
        benchSteps.clear()
        for (path in listOf("shortcut", "fake_llm")) {
            val n = if (path == "shortcut") BENCH_RUNS else LLM_RUNS
            for (cmd in BENCH_LAUNCH) repeat(n) { benchSteps.addLast(req(cmd, path)) }
            // Alternate the torch from its current state; an even count leaves it as it was.
            var on = torch.isOn != true
            repeat(n) {
                benchSteps.addLast(req(if (on) "flashlight on" else "flashlight off", path))
                on = !on
            }
        }
        benchRunning = true
        benchButton.isEnabled = false
        log.event("bench_start", "${benchSteps.size} steps; dry run for intents; torch real; dial auto-confirmed")
        nextBenchStep()
    }

    private fun nextBenchStep() {
        if (!benchRunning) return
        val step = benchSteps.removeFirstOrNull()
        if (step == null) {
            benchRunning = false
            benchButton.isEnabled = true
            log.event("bench_end", "done")
            refreshResults()
            return
        }
        // A short gap between runs so each one starts from an idle main thread.
        handler.postDelayed({ if (benchRunning) submit(step) }, BENCH_GAP_MS)
    }

    private fun abortBenchmark(reason: String) {
        benchRunning = false
        benchSteps.clear()
        handler.removeCallbacksAndMessages(null)
        torch.cancelWait()
        benchButton.isEnabled = true
        log.event("bench_aborted", reason)
    }

    // ---- Real LLM path ---------------------------------------------------------------------------

    private fun refreshModelStatus() {
        fun line(f: java.io.File?, name: String) =
            if (f?.isFile == true) "  $name: present (${f.length() / (1024 * 1024)} MB)" else "  $name: MISSING"
        modelStatus.text = buildString {
            appendLine("Real LLM path models (not in the APK). adb push both files into:")
            appendLine("  ${pipeline.modelsDir?.absolutePath ?: "external storage unavailable"}")
            appendLine(line(pipeline.dmFile, DecisionRouter.MODEL_FILE))
            appendLine(line(pipeline.llmFile, LiteRtToolLlm.MODEL_FILE))
            append("Models: $loadLine")
        }
    }

    private fun loadModels() {
        if (pipeline.loaded) return toast("Models already loaded")
        if (loadJob?.isActive == true) return
        if (!pipeline.filesPresent()) return toast("Model files missing; see the model status")
        loadJob = scope.launch { loadAndReport() }
    }

    /** Loads both models once (off the main thread) and logs load time and PSS. False if loading failed. */
    private suspend fun loadAndReport(): Boolean {
        loadLine = "loading…"
        refreshModelStatus()
        return try {
            pipeline.load()?.let { rep ->
                loadLine = String.format(
                    Locale.US, "loaded: Decision Maker %.0f ms, Gemma %.0f ms, PSS %d MB",
                    rep.dmMs, rep.llmMs, rep.pssKb / 1024,
                )
                log.event("llm_load", "dm_ms=${SpikeLog.ms(rep.dmMs)}; llm_ms=${SpikeLog.ms(rep.llmMs)}; pss_kb=${rep.pssKb}", totalMs = rep.dmMs + rep.llmMs)
            }
            true
        } catch (e: kotlinx.coroutines.CancellationException) {
            loadLine = "not loaded (cancelled)"
            throw e
        } catch (e: Exception) {
            loadLine = "load FAILED: ${e.javaClass.simpleName}: ${e.message}"
            log.event("llm_load_failed", "${e.javaClass.simpleName}: ${e.message}")
            false
        } finally {
            if (!isDestroyed) refreshModelStatus()
        }
    }

    /** Live request after a shortcut miss. [t] started at submit. */
    private fun startLlm(r: Request, t: Timing) {
        if (llmJob?.isActive == true || loadJob?.isActive == true) {
            show("LLM path busy (a request or the model load is still running).")
            return r.onDone(false)
        }
        if (!pipeline.filesPresent()) {
            log.event("no_match", "shortcut matcher: no match; LLM model files missing", path = r.path)
            show("No shortcut matched, and the model files are missing (see the model status).")
            return r.onDone(false)
        }
        show(if (pipeline.loaded) "LLM path: thinking…" else "LLM path: loading models (first use)…")
        llmJob = scope.launch {
            // A first-use load is not part of the request time: t0 restarts after it.
            val timing = if (pipeline.loaded) t else if (loadAndReport()) Timing(SystemClock.elapsedRealtimeNanos()) else t
            onLlmResult(r, timing, pipeline.run(r.text), prompt = null)
        }
    }

    /**
     * Runs on the main thread after the pipeline returned. A validated call goes to the Permission
     * Engine. Live: then on to confirmation/executor as for any call. Benchmark ([prompt] set): it
     * stops at the decision (a confirmation is cancelled at once), so nothing is launched.
     */
    private suspend fun onLlmResult(r: Request, t: Timing, run: LlmRun, prompt: BenchPrompt?) {
        t.parse = SystemClock.elapsedRealtimeNanos()
        val bench = prompt != null
        val result = run.route
        val route = result?.route
        val selection = run.selection
        val exposed = (selection as? ToolSelection.Expose)?.exposed.orEmpty()
        val call = (route as? LlmRoute.Call)?.call
        var decision: Decision? = null
        val outcome: String
        val note: String
        when {
            run.error != null -> {
                outcome = "error"
                note = run.error
                show("LLM path error: ${run.error}")
            }
            selection is ToolSelection.NoAction -> {
                outcome = "no_action"
                note = selection.reason
                show("No action (${selection.reason}). In the app this goes to the clarify/chat path.")
            }
            route is LlmRoute.Rejected -> {
                outcome = "rejected"
                note = route.reason
                show("LLM path: REJECTED, nothing runs. ${route.reason}")
            }
            route is LlmRoute.AskUser -> {
                outcome = "ask_user"
                note = route.errors.joinToString("; ")
                show("LLM path: invalid twice → ask user.\n${route.question}\nErrors: $note")
            }
            route is LlmRoute.NoToolCall -> {
                outcome = "no_tool_call"
                note = "text: " + route.text.take(160)
                show("LLM path: the model answered in text, no action.\n${route.text.take(300)}")
            }
            call != null -> {
                outcome = "call"
                note = call.redacted() + if (result?.repaired == true) "; repaired" else ""
                decision = evaluate(call, t, r)
            }
            else -> {
                outcome = "error"
                note = "no route"
                show("LLM path error: no route")
            }
        }
        val e2eMs = ms(t.t0, if (decision != null) t.policy else t.parse)
        val decisionText = when (decision) {
            null -> ""
            is Decision.Allow -> "allow"
            is Decision.NeedsConfirmation -> "confirm"
            is Decision.Deny -> "deny: ${decision.reason}"
        }
        if (call != null && decision != null) {
            if (bench) {
                // Dry run: consume the confirmation so nothing stays pending, never execute.
                if (decision is Decision.NeedsConfirmation) engine.respond(decision.request.id, UserResponse.CANCEL, keyguard.isDeviceLocked)
            } else {
                handleDecision(call, decision, t, r)
            }
        } else if (!bench) {
            r.onDone(false)
        }

        // After the timed section: PSS (slow, off the main thread), then the row.
        val pss = pipeline.pssKb()
        val routeLabel = when (selection) {
            is ToolSelection.Expose -> selection.tool.wire
            is ToolSelection.NoAction -> ToolRouting.NONE
            null -> ""
        }
        val expectedCall = prompt?.expectedCall
        val dialInvolved = ToolName.DIAL in exposed || prompt?.expected == ToolName.DIAL
        val top = run.router?.probabilities?.entries?.sortedByDescending { it.value }?.take(3)
            ?.joinToString(" ") { String.format(Locale.US, "%s=%.2f", it.key, it.value) }.orEmpty()
        val promptNote = prompt?.let { "prompt: " + if (dialInvolved) LogRedaction.maskDigits(it.text) else it.text }
        val detail = listOfNotNull(
            promptNote,
            if (dialInvolved && outcome != "call") LogRedaction.maskDigits(note) else note,
            top.takeIf { it.isNotEmpty() }?.let { "p: $it" },
        ).joinToString(" | ")
        log.llm(
            kind = if (bench) LlmSummary.BENCH_KIND else LlmSummary.LIVE_KIND,
            action = call?.tool?.wire ?: routeLabel.ifEmpty { ToolRouting.NONE },
            mode = if (r.dryRun) "dry_run" else "real",
            ok = outcome == "call" && decision !is Decision.Deny,
            detail = detail,
            llm = mapOf(
                "expected" to (if (prompt == null) "" else prompt.expected?.wire ?: ToolRouting.NONE),
                "dm_label" to run.router?.label.orEmpty(),
                "dm_conf" to run.router?.confidence?.let { String.format(Locale.US, "%.3f", it) }.orEmpty(),
                "dm_ms" to SpikeLog.ms(run.dmMs),
                "route" to routeLabel,
                "llm_ttft_ms" to SpikeLog.ms(run.ttftMs),
                "llm_ms" to SpikeLog.ms(run.llmMs),
                "tool_name" to result?.toolName.orEmpty(),
                "args_json" to result?.argsJsonForLog(exposed).orEmpty(),
                "source" to result?.first?.source?.name?.lowercase(Locale.US).orEmpty(),
                "json_ok" to result?.jsonValid?.toString().orEmpty(),
                "args_ok" to result?.argsValid?.toString().orEmpty(),
                "repaired" to result?.repaired?.toString().orEmpty(),
                "call_tool" to call?.tool?.wire.orEmpty(),
                "args_match" to if (expectedCall == null) "" else (call != null && LlmBenchPrompts.argsMatch(expectedCall, call)).toString(),
                "outcome" to outcome,
                "decision" to decisionText,
                "e2e_ms" to SpikeLog.ms(e2eMs),
                "pss_kb" to pss.toString(),
            ),
        )
    }

    private fun toggleLlmBenchmark() {
        if (llmBenchJob?.isActive == true) return stopLlmBenchmark("stopped by user")
        if (benchRunning) return toast("Shortcut benchmark running")
        if (llmJob?.isActive == true || loadJob?.isActive == true) return toast("LLM path busy")
        if (!pipeline.filesPresent()) return toast("Model files missing; see the model status")
        llmBenchButton.text = "Stop LLM benchmark"
        llmBenchJob = scope.launch {
            try {
                if (!pipeline.loaded && !loadAndReport()) return@launch
                // Fixed context, like the shortcut benchmark: trusted text, actions enabled.
                val ctx = PolicyContext(setOf(Provenance.USER_TYPED), deviceLocked = keyguard.isDeviceLocked)
                val prompts = LlmBenchPrompts.ALL
                log.event("llm_bench_start", "${prompts.size} prompts; dry run: stops at the Permission Engine decision; nothing is launched")
                for ((i, p) in prompts.withIndex()) {
                    show("LLM benchmark ${i + 1}/${prompts.size}…")
                    val r = Request(p.text, kind = "bench", path = "llm", dryRun = true, autoConfirm = false, ctx = ctx)
                    val t = Timing(SystemClock.elapsedRealtimeNanos())
                    onLlmResult(r, t, pipeline.run(p.text), p)
                    delay(BENCH_GAP_MS)
                }
                log.event("llm_bench_end", "done")
                show("LLM benchmark done.")
            } finally {
                if (!isDestroyed) {
                    llmBenchButton.text = LLM_BENCH_LABEL
                    refreshResults()
                }
            }
        }
    }

    private fun stopLlmBenchmark(reason: String) {
        pipeline.cancel()
        llmBenchJob?.cancel()
        log.event("llm_bench_aborted", reason)
        show("LLM benchmark stopped: $reason")
    }

    // ---- Results ---------------------------------------------------------------------------------

    private fun refreshResults() {
        val csv = log.readCurrent()
        results.text = "Run file: ${log.current?.name}\n\n" + Summary.from(csv).describe() + "\n\n" + LlmSummary.from(csv).describe()
    }

    private fun shareLog() {
        val file = log.current ?: return toast("No run file")
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Bibi S4 run ${file.name}")
            putExtra(Intent.EXTRA_TEXT, file.readText())
        }
        startActivity(Intent.createChooser(send, "Share ${file.name}"))
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun ms(from: Long, to: Long) = (to - from) / 1e6

    companion object {
        const val BENCH_RUNS = 20
        const val LLM_RUNS = 6
        const val BENCH_GAP_MS = 150L
        const val TORCH_TIMEOUT_MS = 1_000L
        const val LLM_BENCH_LABEL = "LLM benchmark (110 prompts, real models, dry run; minutes)"

        /** Benchmark commands for the four intent actions (dry run). Settings is on every launcher. */
        val BENCH_LAUNCH = listOf("open settings", "wake me at 7:30", "set a timer for 5 minutes", "call 0123 456 789")

        val QUICK = listOf(
            "open settings",
            "wake me at 7:30",
            "set a timer for 5 minutes",
            "flashlight on",
            "flashlight off",
            "call 0123 456 789",
        )
    }
}
