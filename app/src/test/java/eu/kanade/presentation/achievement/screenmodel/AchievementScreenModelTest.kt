package eu.kanade.presentation.achievement.screenmodel

import eu.kanade.domain.easteregg.aurora.AuroraHeartManager
import eu.kanade.domain.easteregg.aurora.AuroraHeartState
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.achievement.handler.PointsManager
import tachiyomi.data.achievement.loader.AchievementLoader
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.domain.achievement.model.DayActivity
import tachiyomi.domain.achievement.model.MonthStats
import tachiyomi.domain.achievement.model.UserPoints
import tachiyomi.domain.achievement.repository.AchievementRepository
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import java.time.LocalDate

class AchievementScreenModelTest {

    private val repository: AchievementRepository = mockk()
    private val loader: AchievementLoader = mockk()
    private val pointsManager: PointsManager = mockk()
    private val activityDataRepository: ActivityDataRepository = mockk()
    private val auroraHeartManager: AuroraHeartManager = mockk()
    private val activeScreenModels = mutableListOf<AchievementScreenModel>()

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        coEvery { repository.getAll() } returns flowOf(emptyList())
        coEvery { repository.getAllProgress() } returns flowOf(emptyList())
        coEvery { loader.loadAchievements() } returns Result.success(0)
        every { pointsManager.subscribeToPoints() } returns flowOf(UserPoints())
        coEvery { activityDataRepository.getActivityData(any()) } returns flowOf(emptyList())
        coEvery { activityDataRepository.getCurrentMonthStats() } returns MonthStats(0, 0, 0, 0)
        coEvery { activityDataRepository.getPreviousMonthStats() } returns MonthStats(0, 0, 0, 0)
        coEvery { activityDataRepository.getLastTwelveMonthsStats() } returns emptyList()
        every { auroraHeartManager.state } returns kotlinx.coroutines.flow.MutableStateFlow(
            AuroraHeartState(hintRevealed = false, stageIndex = 0, totalStages = 0, unlocked = false),
        )
    }

    @AfterEach
    fun tearDown() {
        activeScreenModels.forEach { it.onDispose() }
        activeScreenModels.clear()
        runBlocking {
            repeat(5) { yield() }
        }
        Dispatchers.resetMain()
    }

    @Test
    fun `should load activity data into Success state`() {
        runTest {
            // Given
            val activity =
                listOf(DayActivity(LocalDate.now(), 1, tachiyomi.domain.achievement.model.ActivityType.APP_OPEN))
            coEvery { activityDataRepository.getActivityData(365) } returns flowOf(activity)

            val screenModel = AchievementScreenModel(
                repository,
                loader,
                pointsManager,
                activityDataRepository,
                auroraHeartManager,
            ).also(activeScreenModels::add)

            // WhileSubscribed stateIn needs an active collector to drive the upstream flow
            val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                screenModel.state.collect()
            }

            // When
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            val state = screenModel.state.value
            state.shouldBeInstanceOf<AchievementScreenState.Success>()
            (state as AchievementScreenState.Success).activityData shouldBe activity

            collectJob.cancel()
        }
    }

    @Test
    fun `refreshAchievements delegates to loader`() = runTest {
        val screenModel = AchievementScreenModel(
            repository,
            loader,
            pointsManager,
            activityDataRepository,
            auroraHeartManager,
        ).also(activeScreenModels::add)

        screenModel.refreshAchievements()

        testDispatcher.scheduler.advanceUntilIdle()

        // loadAchievements is invoked by refreshAchievements and on model init
        coVerify { loader.loadAchievements() }
    }

    @Test
    fun `filtered achievements include NOVEL and BOTH for NOVEL tab`() {
        val novelAchievement = Achievement(
            id = "novel_1",
            type = AchievementType.EVENT,
            category = AchievementCategory.NOVEL,
            title = "Novel",
        )
        val bothAchievement = Achievement(
            id = "both_1",
            type = AchievementType.EVENT,
            category = AchievementCategory.BOTH,
            title = "Both",
        )
        val mangaAchievement = Achievement(
            id = "manga_1",
            type = AchievementType.EVENT,
            category = AchievementCategory.MANGA,
            title = "Manga",
        )

        val state = AchievementScreenState.Success(
            achievements = listOf(novelAchievement, bothAchievement, mangaAchievement),
            selectedCategory = AchievementCategory.NOVEL,
        )

        state.filteredAchievements shouldBe listOf(novelAchievement, bothAchievement)
    }

    @Test
    fun `aurora heart card is hidden until the quest is started and visible after the first trigger`() {
        val aurora = Achievement(
            id = "aurora_heart",
            type = AchievementType.SECRET,
            category = AchievementCategory.SECRET,
            threshold = 1,
            title = "Aurora Heart",
            isHidden = true,
            isSecret = true,
        )

        // Before the first trigger: completely hidden.
        val hiddenState = AchievementScreenState.Success(
            achievements = listOf(aurora),
            auroraQuestStarted = false,
        )
        hiddenState.filteredAchievements shouldBe emptyList()
        hiddenState.totalCount shouldBe 0

        // After the first trigger (hint revealed): card appears so the quest can be resumed.
        val startedState = AchievementScreenState.Success(
            achievements = listOf(aurora),
            auroraQuestStarted = true,
        )
        startedState.filteredAchievements shouldBe listOf(aurora)
        startedState.totalCount shouldBe 1
    }
}
