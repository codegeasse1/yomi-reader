package mihon.domain.extensionrepo.anime.interactor

import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import mihon.domain.extensionstore.anime.repository.AnimeExtensionStoreRepository
import org.junit.jupiter.api.Test

class UpdateAnimeExtensionRepoTest {

    @Test
    fun `awaitAll refreshes all stores`() = runTest {
        val repository = mockk<AnimeExtensionStoreRepository>(relaxed = true)
        val interactor = UpdateAnimeExtensionRepo(repository)

        interactor.awaitAll()

        coVerify(exactly = 1) { repository.refreshAll() }
    }
}
