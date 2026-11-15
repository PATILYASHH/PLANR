package com.exosynk.planr.data

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PLANR has no internet, so crashes are kept on the phone (last [KEEP]) and only
 * leave it if the user taps "Share" in About.
 */
object CrashLog {

    private const val KEEP = 5
    private fun dir(context: Context) = File(context.filesDir, "crashes").apply { mkdirs() }

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun reports(context: Context): List<File> =
        dir(context).listFiles { f -> f.name.endsWith(".txt") }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun clear(context: Context) = reports(context).forEach { it.delete() }

    /** All reports as one text, ready to paste into a GitHub issue. */
    fun shareText(context: Context): String = reports(context).joinToString("\n\n----------\n\n") { it.readText() }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val text = buildString {
            appendLine("PLANR $version crash · $stamp")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(stack.take(20_000))
        }
        File(dir(context), "crash-${System.currentTimeMillis()}.txt").writeText(text)
        reports(context).drop(KEEP).forEach { it.delete() }
    }
}
