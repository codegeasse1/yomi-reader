package mihon.core.archive

import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import me.zhanghai.android.libarchive.ArchiveException
import java.io.Closeable
import java.io.InputStream
import kotlin.concurrent.Volatile

class ArchiveReader(pfd: ParcelFileDescriptor) : Closeable {
    private val size = pfd.statSize
    private val address = Os.mmap(0, size, OsConstants.PROT_READ, OsConstants.MAP_PRIVATE, pfd.fileDescriptor, 0)
    private val lock = Any()

    @Volatile
    private var isClosed = false

    fun <T> useEntries(block: (Sequence<ArchiveEntry>) -> T): T = synchronized(lock) {
        check(!isClosed) { "ArchiveReader is closed" }
        ArchiveInputStream(address, size).use {
            block(generateSequence { it.getNextEntry() })
        }
    }

    fun getInputStream(entryName: String): InputStream? = synchronized(lock) {
        if (isClosed) return null
        val archive = ArchiveInputStream(address, size)
        try {
            while (true) {
                val entry = archive.getNextEntry() ?: break
                if (entry.name == entryName) {
                    return archive
                }
            }
        } catch (e: ArchiveException) {
            archive.close()
            throw e
        }
        archive.close()
        null
    }

    override fun close() {
        synchronized(lock) {
            if (isClosed) return
            isClosed = true
        }
        Os.munmap(address, size)
    }
}
