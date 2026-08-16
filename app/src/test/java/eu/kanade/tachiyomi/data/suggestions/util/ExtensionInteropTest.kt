package eu.kanade.tachiyomi.data.suggestions.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExtensionInteropTest {

    @Test
    fun `runInterop returns block result on success`() = runTest {
        val result = ExtensionInterop.runInterop("Test", "ok") { "value" }
        assertEquals("value", result)
    }

    @Test
    fun `runInterop returns null on LinkageError`() = runTest {
        val result = ExtensionInterop.runInterop("Test", "abi") {
            throw NoSuchMethodError("No static method runBlockingK")
        }
        assertNull(result)
    }

    @Test
    fun `runInterop returns null on Exception`() = runTest {
        val result = ExtensionInterop.runInterop("Test", "fail") {
            error("boom")
        }
        assertNull(result)
    }

    @Test
    fun `runInterop rethrows CancellationException`() = runTest {
        val thrown = runCatching {
            ExtensionInterop.runInterop("Test", "cancel") {
                throw CancellationException("cancelled")
            }
        }.exceptionOrNull()
        assertTrue(thrown is CancellationException)
    }

    @Test
    fun `runInterop does not swallow OutOfMemoryError`() = runTest {
        val thrown = runCatching {
            ExtensionInterop.runInterop("Test", "oom") {
                throw OutOfMemoryError("simulated")
            }
        }.exceptionOrNull()
        assertTrue(thrown is OutOfMemoryError)
    }
}
