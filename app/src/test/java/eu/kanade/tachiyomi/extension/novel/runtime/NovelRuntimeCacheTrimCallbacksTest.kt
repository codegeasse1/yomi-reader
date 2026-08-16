package eu.kanade.tachiyomi.extension.novel.runtime

import eu.kanade.tachiyomi.extension.novel.NovelPluginSourceFactory
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Lightweight tests for [NovelRuntimeCacheTrimCallbacks].
 *
 * Verifies that the callback forwards trim and low-memory events
 * to the source factory.  Real `ComponentCallbacks2` dispatch is
 * tested by the Android framework; here we check only the contract.
 */
class NovelRuntimeCacheTrimCallbacksTest {

    /** Records invocations of [NovelPluginSourceFactory.clearRuntimeCaches]. */
    private class RecordingSourceFactory : NovelPluginSourceFactory {
        var clearCount = 0

        override fun create(plugin: tachiyomi.domain.extension.novel.model.NovelPlugin.Installed): Nothing? = null

        override fun clearRuntimeCaches() {
            clearCount++
        }
    }

    @Test
    fun `onTrimMemory below TRIM_MEMORY_RUNNING_LOW does not call clearRuntimeCaches`() {
        val factory = RecordingSourceFactory()
        val callbacks = NovelRuntimeCacheTrimCallbacks(factory)

        // Only levels below TRIM_MEMORY_RUNNING_LOW (10) are ignored
        callbacks.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE) // 5
        callbacks.onTrimMemory(0)
        factory.clearCount shouldBe 0
    }

    @Test
    fun `onTrimMemory at TRIM_MEMORY_RUNNING_LOW calls clearRuntimeCaches`() {
        val factory = RecordingSourceFactory()
        val callbacks = NovelRuntimeCacheTrimCallbacks(factory)

        callbacks.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        factory.clearCount shouldBe 1
    }

    @Test
    fun `onTrimMemory at TRIM_MEMORY_RUNNING_CRITICAL calls clearRuntimeCaches`() {
        val factory = RecordingSourceFactory()
        val callbacks = NovelRuntimeCacheTrimCallbacks(factory)

        callbacks.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        factory.clearCount shouldBe 1
    }

    @Test
    fun `onTrimMemory at TRIM_MEMORY_MODERATE calls clearRuntimeCaches`() {
        val factory = RecordingSourceFactory()
        val callbacks = NovelRuntimeCacheTrimCallbacks(factory)

        callbacks.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE)
        factory.clearCount shouldBe 1
    }

    @Test
    fun `redundant high level calls fire once each`() {
        val factory = RecordingSourceFactory()
        val callbacks = NovelRuntimeCacheTrimCallbacks(factory)

        callbacks.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        callbacks.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        callbacks.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE)

        factory.clearCount shouldBe 3
    }

    @Test
    fun `onLowMemory calls clearRuntimeCaches`() {
        val factory = RecordingSourceFactory()
        val callbacks = NovelRuntimeCacheTrimCallbacks(factory)

        callbacks.onLowMemory()
        factory.clearCount shouldBe 1
    }

    @Test
    fun `onConfigurationChanged does not call clearRuntimeCaches`() {
        val factory = RecordingSourceFactory()
        val callbacks = NovelRuntimeCacheTrimCallbacks(factory)

        callbacks.onConfigurationChanged(android.content.res.Configuration())
        factory.clearCount shouldBe 0
    }
}
