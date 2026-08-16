package eu.kanade.domain.entries.novel

import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.SNovel
import eu.kanade.tachiyomi.novelsource.model.SNovelChapter
import eu.kanade.tachiyomi.ui.library.novel.compileLocalBookArtifact
import eu.kanade.tachiyomi.ui.library.novel.shouldCompileLocalBookArtifact
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.source.local.entries.novel.LocalNovelSource

class LocalNovelAutoCompileGateTest {

    // --- Import gate (preference decides whether the artifact is compiled after import) ---

    @Test
    fun `import does not compile artifact when auto-compile preference is disabled`() {
        shouldCompileLocalBookArtifact(insertedId = 42L, autoCompileEnabled = false) shouldBe false
    }

    @Test
    fun `import compiles artifact when auto-compile preference is enabled and insert succeeded`() {
        shouldCompileLocalBookArtifact(insertedId = 42L, autoCompileEnabled = true) shouldBe true
    }

    @Test
    fun `import does not compile artifact when novel insert failed`() {
        shouldCompileLocalBookArtifact(insertedId = null, autoCompileEnabled = true) shouldBe false
    }

    // --- Compile flow (the injected builder seam from NovelLibraryScreenModel) ---

    @Test
    fun `compile step invokes artifact builder with stored novel and synced chapters`() = runTest {
        val novel = Novel.create().copy(id = 42L, source = LocalNovelSource.ID)
        val source = FakeNovelSource()
        val sourceManager = mockk<NovelSourceManager> {
            every { get(LocalNovelSource.ID) } returns source
        }
        val built = mutableListOf<Pair<Novel, List<NovelChapter>>>()

        compileLocalBookArtifact(
            novel = novel,
            sourceManager = sourceManager,
            syncChapters = { rawSourceChapters, novelToSync, _ ->
                rawSourceChapters.map { chapter ->
                    NovelChapter.create().copy(novelId = novelToSync.id, url = chapter.url)
                }
            },
            compile = { novelToCompile, syncedChapters ->
                built += novelToCompile to syncedChapters
                true
            },
        )

        built.size shouldBe 1
        built[0].first shouldBe novel
        built[0].second.map { it.url } shouldBe listOf("chapter-1")
    }

    @Test
    fun `compile step skips artifact builder when local source is unavailable`() = runTest {
        val novel = Novel.create().copy(id = 42L, source = LocalNovelSource.ID)
        val sourceManager = mockk<NovelSourceManager> {
            every { get(LocalNovelSource.ID) } returns null
        }
        var compiled = false

        compileLocalBookArtifact(
            novel = novel,
            sourceManager = sourceManager,
            syncChapters = { _, _, _ -> emptyList() },
            compile = { _, _ ->
                compiled = true
                true
            },
        )

        compiled shouldBe false
    }

    @Test
    fun `compile step swallows chapter sync failures and does not invoke builder`() = runTest {
        val novel = Novel.create().copy(id = 42L, source = LocalNovelSource.ID)
        val sourceManager = mockk<NovelSourceManager> {
            every { get(LocalNovelSource.ID) } returns FakeNovelSource()
        }
        var compiled = false

        compileLocalBookArtifact(
            novel = novel,
            sourceManager = sourceManager,
            syncChapters = { _, _, _ -> error("sync failed") },
            compile = { _, _ ->
                compiled = true
                true
            },
        )

        compiled shouldBe false
    }

    private class FakeNovelSource : NovelSource {
        override val id: Long = LocalNovelSource.ID
        override val name: String = "Local Novel Source"

        override suspend fun getChapterList(novel: SNovel): List<SNovelChapter> {
            return listOf(
                SNovelChapter.create().apply { url = "chapter-1" },
            )
        }
    }
}
