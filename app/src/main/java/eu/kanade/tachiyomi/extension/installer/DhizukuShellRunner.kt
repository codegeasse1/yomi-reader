package eu.kanade.tachiyomi.extension.installer

import com.rosan.dhizuku.api.Dhizuku
import java.io.BufferedReader
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Shared shell runner for Dhizuku-based extension installers.
 *
 * Centralises process creation, stream draining, and timeout handling so that
 * DhizukuApkInstallBackendAdapter, DhizukuInstallerAnime, and DhizukuInstallerManga
 * don't duplicate the same code.
 */
internal object DhizukuShellRunner {

    private const val TIMEOUT_SECONDS = 120L

    data class ShellResult(val resultCode: Int, val out: String, val err: String) {
        val combinedOutput: String
            get() = listOf(out, err).filter { it.isNotBlank() }.joinToString("\n")
    }

    /**
     * Executes [cmdarray] through a Dhizuku-managed process.
     *
     * @param cmdarray Command array, e.g. `arrayOf("pm", "install-commit", sessionId)`.
     * @param stdin    Optional input stream piped to the process stdin (e.g. APK bytes).
     * @return [ShellResult] with exit code and captured stdout/stderr.
     */
    fun exec(cmdarray: Array<String>, stdin: InputStream? = null): ShellResult {
        val process = Dhizuku.newProcess(cmdarray, null, null)
        var stdout = ""
        var stderr = ""

        val stdoutThread = thread(start = true) {
            stdout = process.inputStream.bufferedReader().use(BufferedReader::readText)
        }
        val stderrThread = thread(start = true) {
            stderr = process.errorStream.bufferedReader().use(BufferedReader::readText)
        }

        if (stdin != null) {
            process.outputStream.use { stdin.copyTo(it) }
        } else {
            process.outputStream.close()
        }

        val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            // Forcefully close streams to unblock reader threads waiting on read()
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
            runCatching { process.outputStream.close() }
            // Interrupt stuck reader threads so they unblock from pending reads
            stdoutThread.interrupt()
            stderrThread.interrupt()
            stdoutThread.join(2_000)
            stderrThread.join(2_000)
            return ShellResult(-1, stdout, stderr.ifBlank { "Command timed out: ${cmdarray.joinToString(" ")}" })
        }

        stdoutThread.join()
        stderrThread.join()
        return ShellResult(process.exitValue(), stdout, stderr)
    }

    /** Regex that extracts the session id from `pm install-create` output, e.g. `[12345]`. */
    val SESSION_ID_REGEX = Regex("""(?<=\[).+?(?=])""")
}
