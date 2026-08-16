package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Persistent backup diagnostics for user-facing log sharing.
 *
 * Events are written to [FILE_NAME] (survives process death / OOM) and mirrored to logcat
 * under [TAG] at ERROR level so [eu.kanade.tachiyomi.util.CrashLogUtil] captures them via `*:E`.
 */
object BackupDiagnosticLog {

    const val TAG = "BackupDiag"

    private const val FILE_NAME = "backup_diag.log"
    private const val MAX_FILE_BYTES = 64 * 1024

    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    fun beginSession(context: Context, isAutoBackup: Boolean, uri: Uri?) {
        log(
            context,
            "session_start",
            "isAuto=$isAutoBackup uri=${uri?.let(::sanitizeUri) ?: "null"}",
        )
    }

    fun endSession(context: Context, success: Boolean, details: String? = null) {
        log(context, if (success) "session_success" else "session_failure", details)
    }

    fun log(context: Context, event: String, details: String? = null) {
        val line = formatLine(event, details)
        Log.e(TAG, line)
        appendToFile(context, line)
    }

    fun logError(context: Context, event: String, throwable: Throwable? = null, details: String? = null) {
        val line = buildString {
            append(formatLine("ERROR:$event", details))
            throwable?.let {
                append('\n')
                append(it.stackTraceToString())
            }
        }
        Log.e(TAG, line, throwable)
        appendToFile(context, line)
    }

    suspend fun <T> measure(context: Context, stage: String, block: suspend () -> T): T {
        log(context, "stage_start", stage)
        val start = System.currentTimeMillis()
        return try {
            val result = block()
            log(context, "stage_end", "$stage durationMs=${System.currentTimeMillis() - start}")
            result
        } catch (e: CancellationException) {
            // A cancelled worker or scope is a normal stop, not a backup failure.
            log(context, "stage_cancelled", "$stage durationMs=${System.currentTimeMillis() - start}")
            throw e
        } catch (e: Throwable) {
            logError(
                context,
                "stage_failed",
                e,
                "$stage durationMs=${System.currentTimeMillis() - start}",
            )
            throw e
        }
    }

    fun readLog(context: Context): String? {
        val file = getFile(context)
        if (!file.exists() || file.length() == 0L) return null
        return file.readText()
    }

    private fun formatLine(event: String, details: String?): String {
        return buildString {
            append(OffsetDateTime.now(ZoneId.systemDefault()).format(timestampFormatter))
            append(' ')
            append(event)
            if (!details.isNullOrBlank()) {
                append(" | ")
                append(details)
            }
        }
    }

    private fun sanitizeUri(uri: Uri): String {
        return "${uri.scheme}://${uri.authority}/…"
    }

    private fun getFile(context: Context): File = File(context.filesDir, FILE_NAME)

    private fun appendToFile(context: Context, line: String) {
        try {
            val file = getFile(context)
            file.appendText("$line\n")
            if (file.length() > MAX_FILE_BYTES) {
                val tail = file.readText().takeLast(MAX_FILE_BYTES / 2)
                file.writeText(tail)
            }
        } catch (_: Exception) {
            // Best-effort only
        }
    }
}
