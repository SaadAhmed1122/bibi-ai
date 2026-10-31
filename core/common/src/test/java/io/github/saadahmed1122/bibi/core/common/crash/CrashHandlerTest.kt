package io.github.saadahmed1122.bibi.core.common.crash

import org.junit.After
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CrashHandlerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val original = Thread.getDefaultUncaughtExceptionHandler()

    @After
    fun restore() = Thread.setDefaultUncaughtExceptionHandler(original)

    private class RecordingHandler : Thread.UncaughtExceptionHandler {
        var thread: Thread? = null
        var error: Throwable? = null
        override fun uncaughtException(t: Thread, e: Throwable) {
            thread = t
            error = e
        }
    }

    @Test
    fun writesTheRecordThenDelegatesToPreviousHandler() {
        val writer = CrashLogWriter(File(tmp.root, "crash.log"), appVersion = "1")
        val previous = RecordingHandler()
        val handler = CrashHandler(writer, "proc:inference", previous)
        val error = RuntimeException("crash now")

        handler.uncaughtException(Thread.currentThread(), error)

        val log = writer.read()
        assertTrue(log, log.contains("process=proc:inference"))
        assertTrue(log, log.contains("java.lang.RuntimeException: crash now"))
        assertSame(error, previous.error)
        assertSame(Thread.currentThread(), previous.thread)
    }

    @Test
    fun stillDelegatesWhenTheLogCannotBeWritten() {
        // A directory where the file should be makes every write fail.
        val blocked = File(tmp.root, "crash.log").apply { mkdirs() }
        val previous = RecordingHandler()
        val error = RuntimeException("x")

        CrashHandler(CrashLogWriter(blocked, appVersion = "1"), "proc", previous)
            .uncaughtException(Thread.currentThread(), error)

        assertSame(error, previous.error)
    }

    @Test
    fun installWrapsTheCurrentDefaultHandler() {
        val previous = RecordingHandler()
        Thread.setDefaultUncaughtExceptionHandler(previous)
        val writer = CrashLogWriter(File(tmp.root, "crash.log"), appVersion = "1")

        CrashHandler.install(writer, "proc")
        val error = RuntimeException("y")
        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), error)

        assertSame(error, previous.error)
        assertTrue(writer.read().contains("RuntimeException: y"))
    }
}
