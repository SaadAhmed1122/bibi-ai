package io.github.saadahmed1122.bibi.spike.s3

import android.os.Debug
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Memory readings for the `:inference` process, alive only while a benchmark runs.
 * - PSS: `Debug.getPss()` ("the PSS memory used by the process as given by the smaps",
 *   developer.android.com/reference/android/os/Debug), sampled every [intervalMs] on one thread.
 *   Reading smaps costs CPU, so the interval is a trade-off: a peak shorter than it can be
 *   missed; [vmHwmKb] covers that case.
 * - VmHWM: the kernel's peak resident set size for the process (/proc/self/status, see
 *   `man 5 proc`). It never misses a peak, but counts shared pages in full, so it reads higher
 *   than PSS. The process exits after each run, so VmHWM belongs to one model only.
 */
class AndroidMemProbe(private val intervalMs: Long = 500) : MemProbe, AutoCloseable {
    private val peak = AtomicLong(0)

    @Volatile
    private var running = true

    private val sampler = Thread({
        while (running) {
            record(Debug.getPss())
            try {
                Thread.sleep(intervalMs)
            } catch (e: InterruptedException) {
                return@Thread
            }
        }
    }, "pss-sampler").apply {
        isDaemon = true
        start()
    }

    private fun record(kb: Long) {
        peak.accumulateAndGet(kb) { a, b -> maxOf(a, b) }
    }

    override fun beginWindow() = peak.set(0)

    override fun endWindowPeakKb(): Long {
        record(Debug.getPss())
        return peak.get()
    }

    override fun vmHwmKb(): Long? = try {
        File("/proc/self/status").useLines { lines ->
            lines.firstOrNull { it.startsWith("VmHWM:") }
                ?.removePrefix("VmHWM:")?.trim()?.substringBefore(' ')?.toLongOrNull()
        }
    } catch (e: Exception) {
        null
    }

    override fun close() {
        running = false
        sampler.interrupt()
    }
}
