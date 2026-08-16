package eu.kanade.tachiyomi.data.download.novel

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.repository.NovelRepository
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.domain.storage.service.StorageManager
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

class NovelDownloadCacheTest {

    @field:TempDir
    lateinit var tempDir: Path

    @Test
    fun `late disk restore does not overwrite newer ids`() = runTest {
        writeDiskCache(
            NovelDiskCache(
                data = mapOf(
                    1L to setOf(1L),
                ),
            ),
        )

        val cache = createCache(backgroundScope)
        cache.updateChapterIds(1L, setOf(2L))

        advanceUntilIdle()

        cache.getDownloadedChapterIds(1L) shouldBe setOf(2L)
    }

    @Test
    fun `removing a novel drops it from the cache`() = runTest {
        val cache = createCache(backgroundScope)
        // Let the constructor's restore/renew coroutine settle before seeding, otherwise it races
        // with the test data.
        advanceUntilIdle()

        cache.updateChapterIds(1L, setOf(10L))
        cache.updateChapterIds(2L, setOf(20L))

        cache.onNovelRemoved(Novel.create().copy(id = 1L))

        cache.getDownloadedChapterIds(1L) shouldBe null
        cache.getDownloadedChapterIds(2L) shouldBe setOf(20L)
    }

    @Test
    fun `empty scan clears disk cache entry`() = runTest {
        val cache = createCache(backgroundScope)
        cache.updateChapterIds(1L, setOf(10L))
        advanceUntilIdle()

        cache.updateChapterIds(1L, emptySet())
        advanceUntilIdle()

        cache.getDownloadedChapterIds(1L) shouldBe null
        tempDir.resolve(CACHE_FILE_NAME).exists() shouldBe false
    }

    private fun createCache(scope: CoroutineScope): NovelDownloadCache {
        val storageManager = mockk<StorageManager>()
        every { storageManager.changes } returns MutableSharedFlow()

        val sourceManager = mockk<NovelSourceManager>()
        every { sourceManager.isInitialized } returns MutableStateFlow(false)

        val novelRepository = mockk<NovelRepository>()
        coEvery { novelRepository.getLibraryNovel() } returns emptyList()
        coEvery { novelRepository.getReadNovelNotInLibrary() } returns emptyList()

        return NovelDownloadCache(
            storageManager = storageManager,
            sourceManager = sourceManager,
            novelRepository = novelRepository,
            scope = scope,
            cacheFileProvider = { tempDir.resolve(CACHE_FILE_NAME).toFile() },
            downloadCountLookup = { 0 },
        )
    }

    private fun writeDiskCache(cache: NovelDiskCache) {
        tempDir.resolve(CACHE_FILE_NAME).writeBytes(ProtoBuf.encodeToByteArray(cache))
    }

    private fun readDiskCache(): NovelDiskCache {
        return ProtoBuf.decodeFromByteArray(tempDir.resolve(CACHE_FILE_NAME).toFile().readBytes())
    }

    private companion object {
        const val CACHE_FILE_NAME = "dl_novel_cache_v1"
    }
}
