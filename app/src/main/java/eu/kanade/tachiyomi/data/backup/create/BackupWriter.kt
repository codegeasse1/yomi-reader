package eu.kanade.tachiyomi.data.backup.create

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.backup.BackupContentSummary
import eu.kanade.tachiyomi.data.backup.BackupDecoder
import eu.kanade.tachiyomi.data.backup.BackupDiagnosticLog
import eu.kanade.tachiyomi.data.backup.BackupOrigin
import eu.kanade.tachiyomi.data.backup.BackupWriteReceipt
import eu.kanade.tachiyomi.data.backup.contentSummary
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Writes a backup so that a half-written or silently truncated file can never replace a good one.
 *
 * The bytes are compressed and fully verified in the cache directory first, and only a file that
 * decodes back into exactly the expected content is copied over the destination. The destination is
 * then read back and verified again, because a SAF provider is free to accept a write and store
 * something else (or nothing at all).
 */
class BackupWriter(
    private val context: Context,
) {

    /**
     * @param destination file chosen by the user or created by the auto backup job.
     * @param payload uncompressed protobuf payload.
     * @param expected content the caller believes it serialized.
     * @param expectedOrigin format the caller believes it wrote.
     * @return a receipt describing what is actually stored on disk.
     */
    suspend fun write(
        destination: UniFile,
        payload: ByteArray,
        expected: BackupContentSummary,
        expectedOrigin: BackupOrigin,
    ): BackupWriteReceipt {
        val staging = File.createTempFile("backup-staging", ".tachibk", context.cacheDir)
        try {
            val compressed = BackupDiagnosticLog.measure(context, "stage_gzip") {
                FileOutputStream(staging).use { out ->
                    GZIPOutputStream(out).use { it.write(payload) }
                }
                staging.readBytes()
            }

            // Verify the staged bytes before touching the user's existing backup.
            BackupDiagnosticLog.measure(context, "verify_staged") {
                verify(compressed, expected, expectedOrigin, stage = "staged file")
            }

            BackupDiagnosticLog.measure(context, "write_destination") {
                replaceDestination(destination, compressed)
            }

            val written = BackupDiagnosticLog.measure(context, "read_back") {
                destination.openInputStream().use { it.readBytes() }
            }

            BackupDiagnosticLog.measure(context, "verify_destination") {
                if (!written.contentEquals(compressed)) {
                    throw IOException(
                        "Backup destination does not contain the bytes that were just written " +
                            "(${written.size} of ${compressed.size} bytes)",
                    )
                }
                verify(written, expected, expectedOrigin, stage = "destination")
            }

            val checksum = sha256(written)
            // Counts only: never titles, urls or any quest payload.
            BackupDiagnosticLog.log(
                context,
                "write_receipt",
                "bytes=${written.size} sha256=$checksum origin=$expectedOrigin " +
                    "manga=${expected.mangaCount} anime=${expected.animeCount} " +
                    "novel=${expected.novelCount} categories=${expected.categoriesCount}",
            )

            return BackupWriteReceipt(
                byteLength = written.size.toLong(),
                sha256 = checksum,
                origin = expectedOrigin,
                summary = expected,
            )
        } finally {
            staging.delete()
        }
    }

    /**
     * Overwrite [destination] in place, truncating any previous, longer content.
     *
     * Opening a SAF document in "rwt" mode is the only reliable way to shorten it. Some providers
     * ignore the truncate flag, so the size is checked and, as a last resort, the document is
     * deleted and recreated rather than left with trailing bytes of an older backup.
     */
    private fun replaceDestination(destination: UniFile, bytes: ByteArray) {
        val uri = destination.uri
        val resolver = context.contentResolver

        val wroteInPlace = try {
            resolver.openFileDescriptor(uri, "rwt")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { out ->
                    out.channel.truncate(0)
                    out.write(bytes)
                    out.flush()
                    pfd.fileDescriptor.sync()
                    if (out.channel.size() != bytes.size.toLong()) {
                        throw IOException(
                            "Backup file could not be truncated to the new size " +
                                "(${out.channel.size()} instead of ${bytes.size})",
                        )
                    }
                }
                true
            } ?: false
        } catch (e: IOException) {
            throw e
        } catch (_: Exception) {
            false
        }

        if (wroteInPlace) return

        // Fallback for providers that refuse random access: recreate the document.
        val parent = destination.parentFile
            ?: throw IOException("Backup destination cannot be safely replaced")
        val name = destination.name
            ?: throw IOException("Backup destination cannot be safely replaced")
        if (!destination.delete()) {
            throw IOException("Backup destination could not be replaced")
        }
        val recreated = parent.createFile(name)
            ?: throw IOException("Backup destination could not be recreated")
        recreated.openOutputStream().use { it.write(bytes) }
        if (recreated.length() != bytes.size.toLong()) {
            throw IOException("Backup destination has an unexpected size after writing")
        }
    }

    /** Decode [compressed] again and assert it still describes exactly what we meant to store. */
    private fun verify(
        compressed: ByteArray,
        expected: BackupContentSummary,
        expectedOrigin: BackupOrigin,
        stage: String,
    ) {
        val payload = GZIPInputStream(compressed.inputStream()).use { it.readBytes() }
        val decoded = BackupDecoder(context).decodeBytes(payload)

        if (decoded.origin != expectedOrigin) {
            throw IOException(
                "Backup $stage was written as $expectedOrigin but reads back as ${decoded.origin}",
            )
        }
        val actual = decoded.backup.contentSummary()
        if (actual != expected) {
            throw IOException(
                "Backup $stage is incomplete: expected $expected but it contains $actual",
            )
        }
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var offset = 0
        while (offset < bytes.size) {
            val length = minOf(DIGEST_CHUNK, bytes.size - offset)
            digest.update(bytes, offset, length)
            offset += length
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun File.readBytes(): ByteArray = FileInputStream(this).use { it.readBytes() }

    companion object {
        private const val DIGEST_CHUNK = 64 * 1024
    }
}
