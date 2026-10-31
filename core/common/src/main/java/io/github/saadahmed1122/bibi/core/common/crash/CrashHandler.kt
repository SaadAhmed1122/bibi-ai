package io.github.saadahmed1122.bibi.core.common.crash

/** Logs an uncaught exception locally, then hands it to the previous handler (which kills the process). */
class CrashHandler(
    private val writer: CrashLogWriter,
    private val processName: String,
    private val previous: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, error: Throwable) {
        try {
            writer.writeException(processName, thread.name, error)
        } catch (_: Throwable) {
            // Logging must never stop the crash from reaching the system handler.
        }
        previous?.uncaughtException(thread, error)
    }

    companion object {
        /** Installs the handler as the process default; call once per process at startup. */
        fun install(writer: CrashLogWriter, processName: String) {
            Thread.setDefaultUncaughtExceptionHandler(
                CrashHandler(writer, processName, Thread.getDefaultUncaughtExceptionHandler()),
            )
        }
    }
}
