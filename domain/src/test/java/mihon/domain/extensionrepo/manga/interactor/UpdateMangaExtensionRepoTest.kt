package mihon.domain.extensionrepo.manga.interactor

import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mihon.domain.extensionstore.manga.repository.MangaExtensionStoreRepository
import org.junit.jupiter.api.Test

class UpdateMangaExtensionRepoTest {

    @Test
    fun `awaitAll refreshes all stores`() = runTest {
        val repository = mockk<MangaExtensionStoreRepository>(relaxed = true)
        val interactor = UpdateMangaExtensionRepo(repository)

        interactor.awaitAll()

        coVerify(exactly = 1) { repository.refreshAll() }
    }
}
