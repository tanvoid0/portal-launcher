package com.tanvoid0.portallauncher.data

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Keeps the last crash on disk so the next launch can show it.
 *
 * A launcher failing is not like other apps failing: the user is left with no home
 * screen and, on many devices, no obvious route to Settings. "It just stopped working
 * and I had to factory reset" is the review that follows, and without a record there is
 * nothing to act on either.
 *
 * Local and dependency-free on purpose. Crashlytics or Sentry belong here eventually,
 * but both need an account and a key that cannot live in this repository, and a
 * half-wired reporter that silently drops crashes is worse than a file. This works with
 * no network, no consent prompt and nothing leaving the device unless the user shares it.
 */
object CrashRecorder {

    private const val FILE_NAME = "last-crash.txt"

    /** Chains to the existing handler so this never replaces the platform's own report. */
    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(context, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
        // Overwritten each time rather than appended: the newest crash is the one worth
        // reading, and an unbounded log in a launcher's data directory is its own bug.
        file(context).writeText(
            buildString {
                appendLine("thread: ${thread.name}")
                appendLine("android: ${android.os.Build.VERSION.SDK_INT}")
                appendLine("device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                appendLine()
                append(stack.toString())
            }
        )
    }

    /** The last recorded crash, or null when there is none. */
    fun lastCrash(context: Context): String? =
        file(context).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)
}
