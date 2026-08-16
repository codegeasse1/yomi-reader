package mihon.domain.extensionrepo.novel.interactor

import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mihon.domain.extensionstore.novel.repository.NovelExtensionStoreRepository
import org.junit.jupiter.api.Test

class UpdateNovelExtensionRepoTest {

    @Test
    fun `awaitAll refreshes all stores`() = runTest {
        val repository = mockk<NovelExtensionStoreRepository>(relaxed = true)
        val interactor = UpdateNovelExtensionRepo(repository)

        interactor.awaitAll()

        coVerify(exactly = 1) { repository.refreshAll() }
    }
}
