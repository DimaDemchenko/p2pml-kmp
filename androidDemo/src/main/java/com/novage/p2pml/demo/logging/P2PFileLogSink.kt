package com.novage.p2pml.demo.logging

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.novage.p2pml.api.logging.LogLevel
import com.novage.p2pml.api.logging.P2PLogger
import com.novage.p2pml.api.logging.P2PLogging
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Persists library logs to a per-session file so they can be pulled off the device later
 * (share sheet, `adb pull`, or Android Studio Device Explorer). Chains in front of the
 * library's default sink, so Logcat keeps working.
 */
object P2PFileLogSink {
    private const val LOG_DIR = "p2pml-logs"
    private const val MAX_SESSION_FILES = 5

    private val writeExecutor = Executors.newSingleThreadExecutor()

    // Confined to writeExecutor's single thread (SimpleDateFormat is not thread-safe).
    private val lineTimeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var writer: PrintWriter? = null

    private var logFile: File? = null

    fun install(context: Context) {
        if (logFile != null) return

        val file = createSessionFile(context) ?: return
        logFile = file

        // Queued before the sink is chained, so the single-threaded executor guarantees
        // the writer exists by the time the first writeEntry task runs.
        writeExecutor.execute {
            writer = PrintWriter(file.bufferedWriter())
            writer?.println(
                "--- P2PML demo session, ${Build.MANUFACTURER} ${Build.MODEL}, API ${Build.VERSION.SDK_INT} ---"
            )
        }

        val platformSink = P2PLogging.sink
        P2PLogging.sink = P2PLogger { level, tag, message, throwable ->
            platformSink?.log(level, tag, message, throwable)
            writeEntry(System.currentTimeMillis(), level, tag, message, throwable)
        }
    }

    /** Opens a share sheet for the current session's log file. */
    fun share(context: Context) {
        val file = logFile ?: return

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share P2P logs"))
    }

    private fun createSessionFile(context: Context): File? = runCatching {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, LOG_DIR)
        dir.mkdirs()

        // Timestamped names sort lexicographically — prune all but the newest sessions.
        dir.listFiles()
            ?.sortedByDescending { it.name }
            ?.drop(MAX_SESSION_FILES - 1)
            ?.forEach { it.delete() }

        val sessionStamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        File(dir, "p2pml-$sessionStamp.log")
    }.getOrNull()

    private fun writeEntry(
        timestampMs: Long,
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable?
    ) {
        writeExecutor.execute {
            writer?.apply {
                val time = lineTimeFormat.format(Date(timestampMs))
                println("$time ${level.name.first()} [${tag.removePrefix("P2PML~")}] $message")
                throwable?.let { println(it.stackTraceToString()) }
                flush()
            }
        }
    }
}
