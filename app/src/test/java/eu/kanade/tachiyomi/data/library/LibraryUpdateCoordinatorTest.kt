package eu.kanade.tachiyomi.data.library

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkContinuation
import androidx.work.WorkManager
import eu.kanade.tachiyomi.util.system.isRunning
import eu.kanade.tachiyomi.util.system.workManager
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class LibraryUpdateCoordinatorTest {

    private val context = mockk<Context>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)

    @BeforeEach
    fun setUp() {
        mockkStatic("eu.kanade.tachiyomi.util.system.WorkManagerExtensionsKt")
        every { context.workManager } returns workManager
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `startAll returns false if updates are already running`() {
        // Given
        every { workManager.isRunning("AnimeLibraryUpdate") } returns true
        every { workManager.isRunning("LibraryUpdate") } returns false
        every { workManager.isRunning("NovelLibraryUpdate") } returns false

        // When
        val result = LibraryUpdateCoordinator.startAll(
            context = context,
            updateAnime = true,
            updateManga = true,
            updateNovel = true,
            workManager = workManager,
        )

        // Then
        result shouldBe false
    }

    @Test
    fun `startAll chains enabled update jobs sequentially`() {
        // Given
        every { workManager.isRunning("AnimeLibraryUpdate") } returns false
        every { workManager.isRunning("LibraryUpdate") } returns false
        every { workManager.isRunning("NovelLibraryUpdate") } returns false

        val continuation1 = mockk<WorkContinuation>(relaxed = true)
        val continuation2 = mockk<WorkContinuation>(relaxed = true)

        every {
            workManager.beginUniqueWork(
                "LibraryUpdate-chain",
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>(),
            )
        } returns continuation1

        every {
            continuation1.then(any<OneTimeWorkRequest>())
        } returns continuation2

        // When
        val result = LibraryUpdateCoordinator.startAll(
            context = context,
            updateAnime = true,
            updateManga = true,
            updateNovel = false,
            workManager = workManager,
        )

        // Then
        result shouldBe true
        verify {
            workManager.beginUniqueWork(
                "LibraryUpdate-chain",
                ExistingWorkPolicy.KEEP,
                match<OneTimeWorkRequest> { it.tags.contains("AnimeLibraryUpdate") },
            )
            continuation1.then(
                match<OneTimeWorkRequest> { it.tags.contains("LibraryUpdate") },
            )
            continuation2.enqueue()
        }
    }

    @Test
    fun `stop cancels the chain work request`() {
        // When
        LibraryUpdateCoordinator.stop(context, workManager)

        // Then
        verify {
            workManager.cancelAllWorkByTag("LibraryUpdate-chain")
        }
    }
}
