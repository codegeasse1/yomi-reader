package tachiyomi.data.achievement

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.data.achievement.handler.AchievementCalculator
import tachiyomi.data.achievement.handler.AchievementRuleRegistry
import tachiyomi.data.achievement.handler.FeatureUsageCollector
import tachiyomi.data.achievement.handler.PointsManager
import tachiyomi.data.achievement.handler.checkers.DiversityAchievementChecker
import tachiyomi.data.achievement.handler.checkers.StreakAchievementChecker
import tachiyomi.data.achievement.rules.CompletionCountRule
import tachiyomi.data.achievement.rules.DiversityRule
import tachiyomi.data.achievement.rules.EventRule
import tachiyomi.data.achievement.rules.QuantityRule
import tachiyomi.data.achievement.rules.StreakRule
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.data.handlers.novel.NovelDatabaseHandler
import tachiyomi.domain.achievement.model.Achievement
import tachiyomi.domain.achievement.model.AchievementCategory
import tachiyomi.domain.achievement.model.AchievementType
import tachiyomi.domain.achievement.repository.AchievementRepository
import tachiyomi.domain.achievement.repository.ActivityDataRepository
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.entries.novel.repository.NovelRepository

@Execution(ExecutionMode.CONCURRENT)
class AchievementCalculatorTest : AchievementTestBase() {

    private lateinit var repository: AchievementRepository
    private lateinit var mangaHandler: MangaDatabaseHandler
    private lateinit var animeHandler: AnimeDatabaseHandler
    private lateinit var novelHandler: NovelDatabaseHandler
    private lateinit var diversityChecker: DiversityAchievementChecker
    private lateinit var streakChecker: StreakAchievementChecker
    private lateinit var ruleRegistry: AchievementRuleRegistry
    private lateinit var featureCollector: FeatureUsageCollector
    private lateinit var pointsManager: PointsManager
    private lateinit var mangaRepository: MangaRepository
    private lateinit var animeRepository: AnimeRepository
    private lateinit var novelRepository: NovelRepository
    private lateinit var unlockableManager: tachiyomi.data.achievement.UnlockableManager
    private lateinit var userProfileManager: tachiyomi.data.achievement.UserProfileManager
    private lateinit var activityDataRepository: ActivityDataRepository
    private lateinit var calculator: AchievementCalculator

    @BeforeEach
    override fun setup() {
        super.setup()

        repository = mockk()
        mangaHandler = mockk()
        animeHandler = mockk()
        novelHandler = mockk()
        diversityChecker = mockk(relaxed = true)
        streakChecker = mockk()
        ruleRegistry = mockk(relaxed = true)
        featureCollector = mockk(relaxed = true)
        pointsManager = mockk(relaxed = true)
        mangaRepository = mockk(relaxed = true)
        animeRepository = mockk(relaxed = true)
        novelRepository = mockk(relaxed = true)
        unlockableManager = mockk(relaxed = true)
        userProfileManager = mockk(relaxed = true)
        activityDataRepository = mockk(relaxed = true)

        calculator = AchievementCalculator(
            repository = repository,
            mangaHandler = mangaHandler,
            animeHandler = animeHandler,
            novelHandler = novelHandler,
            diversityChecker = diversityChecker,
            streakChecker = streakChecker,
            achievementsDatabase = database,
            ruleRegistry = ruleRegistry,
            featureCollector = featureCollector,
            pointsManager = pointsManager,
            mangaRepository = mangaRepository,
            animeRepository = animeRepository,
            novelRepository = novelRepository,
            unlockableManager = unlockableManager,
            userProfileManager = userProfileManager,
            activityDataRepository = activityDataRepository,
        )

        // Default stubs
        coEvery { streakChecker.getCurrentStreak() } returns 0
        coEvery { mangaHandler.awaitOneOrNull<Long>(any(), any()) } returns 0L
        coEvery { animeHandler.awaitOneOrNull<Long>(any(), any()) } returns 0L
        coEvery { novelHandler.awaitOneOrNull<Long>(any(), any()) } returns 0L
        coEvery { repository.getAllProgress() } returns flowOf(emptyList())

        coEvery { ruleRegistry.getRule(any()) } answers {
            val id = firstArg<String>()
            when {
                id.startsWith("manga_100") -> QuantityRule(id, AchievementCategory.MANGA)
                id.startsWith("anime_50") -> QuantityRule(id, AchievementCategory.ANIME)
                id == "read_10_novel_chapters" -> QuantityRule(id, AchievementCategory.NOVEL)
                id == "complete_10_manga" -> CompletionCountRule(id, AchievementCategory.MANGA)
                id == "complete_10_anime" -> CompletionCountRule(id, AchievementCategory.ANIME)
                id == "complete_10_novel" -> CompletionCountRule(id, AchievementCategory.NOVEL)
                id == "genre_5" -> DiversityRule(id, AchievementCategory.BOTH)
                id == "manga_source_3" -> DiversityRule(id, AchievementCategory.MANGA)
                id == "streak_7" -> StreakRule(id)
                id == "first_chapter" -> EventRule(id)
                id == "first_episode" -> EventRule(id)
                id == "first_novel_chapter" -> EventRule(id)
                id == "test_ach" -> QuantityRule(id, AchievementCategory.MANGA)
                id.startsWith("ach_") -> {
                    val index = id.removePrefix("ach_").toIntOrNull() ?: 1
                    val category = if (index % 2 == 0) AchievementCategory.MANGA else AchievementCategory.ANIME
                    QuantityRule(id, category)
                }
                else -> null
            }
        }
    }

    @Test
    fun `retroactive calculation works correctly for quantity achievements`() = runTest {
        // Setup test achievements
        val mangaAchievement = Achievement(
            id = "manga_100",
            type = AchievementType.QUANTITY,
            category = AchievementCategory.MANGA,
            threshold = 100,
            points = 100,
            title = "Read 100 Chapters",
        )

        val animeAchievement = Achievement(
            id = "anime_50",
            type = AchievementType.QUANTITY,
            category = AchievementCategory.ANIME,
            threshold = 50,
            points = 50,
            title = "Watch 50 Episodes",
        )

        coEvery { repository.getAll() } returns flowOf(listOf(mangaAchievement, animeAchievement))
        coEvery { repository.insertOrUpdateProgress(any()) } returns Unit

        // Mock database handlers
        coEvery {
            mangaHandler.awaitOneOrNull<Long>(any(), any())
        } returns 150L // 150 chapters read

        coEvery {
            animeHandler.awaitOneOrNull<Long>(any(), any())
        } returns 25L // 25 episodes watched

        // Mock diversity and streak
        coEvery { diversityChecker.getGenreDiversity() } returns 5
        coEvery { diversityChecker.getSourceDiversity() } returns 3
        coEvery { streakChecker.getCurrentStreak() } returns 0

        // Run calculation
        val result = calculator.calculateInitialProgress()

        if (!result.success) {
            System.err.println("Calculation failed: ${result.error}")
        }

        // Verify result
        result.success shouldBe true
        result.achievementsProcessed shouldBe 2
        result.achievementsUnlocked shouldBe 1 // Only manga achievement unlocked
    }

    @Test
    fun `retroactive calculation works correctly for novel quantity achievements`() = runTest {
        val novelAchievement = Achievement(
            id = "read_10_novel_chapters",
            type = AchievementType.QUANTITY,
            category = AchievementCategory.NOVEL,
            threshold = 10,
            points = 25,
            title = "Read 10 Novel Chapters",
        )

        coEvery { repository.getAll() } returns flowOf(listOf(novelAchievement))
        coEvery { repository.insertOrUpdateProgress(any()) } returns Unit

        coEvery { mangaHandler.awaitOneOrNull<Long>(any(), any()) } returns 0L
        coEvery { animeHandler.awaitOneOrNull<Long>(any(), any()) } returns 0L
        coEvery { novelHandler.awaitOneOrNull<Long>(any(), any()) } returns 15L
        coEvery { diversityChecker.getGenreDiversity() } returns 0
        coEvery { diversityChecker.getSourceDiversity() } returns 0
        coEvery { diversityChecker.getMangaGenreDiversity() } returns 0
        coEvery { diversityChecker.getAnimeGenreDiversity() } returns 0
        coEvery { diversityChecker.getNovelGenreDiversity() } returns 0
        coEvery { diversityChecker.getMangaSourceDiversity() } returns 0
        coEvery { diversityChecker.getAnimeSourceDiversity() } returns 0
        coEvery { diversityChecker.getNovelSourceDiversity() } returns 0
        coEvery { streakChecker.getCurrentStreak() } returns 0

        val result = calculator.calculateInitialProgress()

        result.success shouldBe true
        result.achievementsProcessed shouldBe 1
        result.achievementsUnlocked shouldBe 1
    }

    @Test
    fun `retroactive calculation works correctly for diversity achievements`() = runTest {
        val genreAchievement = Achievement(
            id = "genre_5",
            type = AchievementType.DIVERSITY,
            category = AchievementCategory.BOTH,
            threshold = 5,
            points = 200,
            title = "Explore 5 Genres",
        )

        val sourceAchievement = Achievement(
            id = "manga_source_3",
            type = AchievementType.DIVERSITY,
            category = AchievementCategory.MANGA,
            threshold = 3,
            points = 150,
            title = "Use 3 Sources",
        )

        coEvery { repository.getAll() } returns flowOf(listOf(genreAchievement, sourceAchievement))
        coEvery { repository.insertOrUpdateProgress(any()) } returns Unit

        // Mock history
        coEvery { mangaHandler.awaitOneOrNull<Long>(any(), any()) } returns 0L
        coEvery { animeHandler.awaitOneOrNull<Long>(any(), any()) } returns 0L

        // Mock diversity
        coEvery { diversityChecker.getGenreDiversity() } returns 5
        coEvery { diversityChecker.getSourceDiversity() } returns 3
        coEvery { diversityChecker.getMangaGenreDiversity() } returns 3
        coEvery { diversityChecker.getMangaSourceDiversity() } returns 2
        coEvery { diversityChecker.getAnimeGenreDiversity() } returns 2
        coEvery { diversityChecker.getAnimeSourceDiversity() } returns 1

        coEvery { streakChecker.getCurrentStreak() } returns 0

        val result = calculator.calculateInitialProgress()

        result.success shouldBe true
        result.achievementsProcessed shouldBe 2
        result.achievementsUnlocked shouldBe 1 // Genre achievement unlocked
    }

    @Test
    fun `retroactive calculation works correctly for streak achievements`() = runTest {
        val streakAchievement = Achievement(
            id = "streak_7",
            type = AchievementType.STREAK,
            category = AchievementCategory.BOTH,
            threshold = 7,
            points = 300,
            title = "7 Day Streak",
        )

        coEvery { repository.getAll() } returns flowOf(listOf(streakAchievement))
        coEvery { repository.insertOrUpdateProgress(any()) } returns Unit

        // Mock history
        coEvery { mangaHandler.awaitOneOrNull<Long>(any(), any()) } returns 0L
        coEvery { animeHandler.awaitOneOrNull<Long>(any(), any()) } returns 0L

        // Mock diversity
        coEvery { diversityChecker.getGenreDiversity() } returns 0
        coEvery { diversityChecker.getSourceDiversity() } returns 0

        // Mock streak
        coEvery { streakChecker.getCurrentStreak() } returns 7

        val result = calculator.calculateInitialProgress()

        result.success shouldBe true
        result.achievementsProcessed shouldBe 1
        result.achievementsUnlocked shouldBe 1
    }

    @Test
    fun `calculation handles error gracefully`() = runTest {
        // Force an error by returning null from handler
        coEvery { repository.getAll() } throws RuntimeException("Database error")

        val result = calculator.calculateInitialProgress()

        result.success shouldBe false
        result.error shouldBe "Database error"
        result.achievementsProcessed shouldBe 0
    }

    @Test
    fun `calculation processes achievements in batches`() = runTest {
        // Create many achievements to test batch processing
        val achievements = (1..100).map { index ->
            Achievement(
                id = "ach_$index",
                type = AchievementType.QUANTITY,
                category = if (index % 2 == 0) AchievementCategory.MANGA else AchievementCategory.ANIME,
                threshold = index * 10,
                points = 10,
                title = "Achievement $index",
            )
        }

        coEvery { repository.getAll() } returns flowOf(achievements)
        coEvery { repository.insertOrUpdateProgress(any()) } returns Unit

        // Mock handlers
        coEvery { mangaHandler.awaitOneOrNull<Long>(any(), any()) } returns 1000L
        coEvery { animeHandler.awaitOneOrNull<Long>(any(), any()) } returns 500L

        coEvery { diversityChecker.getGenreDiversity() } returns 10
        coEvery { diversityChecker.getSourceDiversity() } returns 5
        coEvery { streakChecker.getCurrentStreak() } returns 0

        val result = calculator.calculateInitialProgress()

        result.success shouldBe true
        result.achievementsProcessed shouldBe 100
    }

    @Test
    fun `event achievements unlock on first action`() = runTest {
        val firstReadAchievement = Achievement(
            id = "first_chapter",
            type = AchievementType.EVENT,
            category = AchievementCategory.MANGA,
            threshold = 1,
            points = 10,
            title = "First Chapter",
        )

        coEvery { repository.getAll() } returns flowOf(listOf(firstReadAchievement))
        coEvery { repository.insertOrUpdateProgress(any()) } returns Unit

        // User has read chapters
        coEvery { mangaHandler.awaitOneOrNull<Long>(any(), any()) } returns 10L
        coEvery { animeHandler.awaitOneOrNull<Long>(any(), any()) } returns 0L

        val result = calculator.calculateInitialProgress()

        result.success shouldBe true
        result.achievementsProcessed shouldBe 1
        result.achievementsUnlocked shouldBe 1
    }

    @Test
    fun `event first achievements are category-scoped`() = runTest {
        val firstManga = Achievement(
            id = "first_chapter",
            type = AchievementType.EVENT,
            category = AchievementCategory.MANGA,
            threshold = 1,
            points = 10,
            title = "First manga chapter",
        )
        val firstAnime = Achievement(
            id = "first_episode",
            type = AchievementType.EVENT,
            category = AchievementCategory.ANIME,
            threshold = 1,
            points = 10,
            title = "First anime episode",
        )
        val firstNovel = Achievement(
            id = "first_novel_chapter",
            type = AchievementType.EVENT,
            category = AchievementCategory.NOVEL,
            threshold = 1,
            points = 10,
            title = "First novel chapter",
        )

        val captured = mutableListOf<tachiyomi.domain.achievement.model.AchievementProgress>()

        coEvery { repository.getAll() } returns flowOf(listOf(firstManga, firstAnime, firstNovel))
        coEvery { repository.insertOrUpdateProgress(capture(captured)) } returns Unit
        coEvery { mangaHandler.awaitOneOrNull<Long>(any(), any()) } returns 1L
        coEvery { animeHandler.awaitOneOrNull<Long>(any(), any()) } returns 0L
        coEvery { novelHandler.awaitOneOrNull<Long>(any(), any()) } returns 0L
        coEvery { diversityChecker.getGenreDiversity() } returns 0
        coEvery { diversityChecker.getSourceDiversity() } returns 0
        coEvery { diversityChecker.getMangaGenreDiversity() } returns 0
        coEvery { diversityChecker.getAnimeGenreDiversity() } returns 0
        coEvery { diversityChecker.getNovelGenreDiversity() } returns 0
        coEvery { diversityChecker.getMangaSourceDiversity() } returns 0
        coEvery { diversityChecker.getAnimeSourceDiversity() } returns 0
        coEvery { diversityChecker.getNovelSourceDiversity() } returns 0
        coEvery { streakChecker.getCurrentStreak() } returns 0

        val result = calculator.calculateInitialProgress()

        result.success shouldBe true
        result.achievementsProcessed shouldBe 3
        result.achievementsUnlocked shouldBe 1

        captured.associateBy { it.achievementId }["first_chapter"]?.isUnlocked shouldBe true
        captured.associateBy { it.achievementId }["first_episode"]?.isUnlocked shouldBe false
        captured.associateBy { it.achievementId }["first_novel_chapter"]?.isUnlocked shouldBe false

        coVerify(atLeast = 3) { repository.insertOrUpdateProgress(any()) }
    }

    @Test
    fun `calculation handles zero history correctly`() = runTest {
        val achievement = Achievement(
            id = "test_ach",
            type = AchievementType.QUANTITY,
            category = AchievementCategory.MANGA,
            threshold = 10,
            points = 100,
            title = "Test",
        )

        coEvery { repository.getAll() } returns flowOf(listOf(achievement))
        coEvery { repository.insertOrUpdateProgress(any()) } returns Unit

        // No history
        coEvery { mangaHandler.awaitOneOrNull<Long>(any(), any()) } returns 0L
        coEvery { animeHandler.awaitOneOrNull<Long>(any(), any()) } returns 0L

        val result = calculator.calculateInitialProgress()

        result.success shouldBe true
        result.achievementsProcessed shouldBe 1
        result.achievementsUnlocked shouldBe 0
    }

    @Test
    fun `calculation duration is tracked`() = runTest {
        val achievement = Achievement(
            id = "test_ach",
            type = AchievementType.QUANTITY,
            category = AchievementCategory.MANGA,
            threshold = 10,
            points = 100,
            title = "Test",
        )

        coEvery { repository.getAll() } returns flowOf(listOf(achievement))
        coEvery { repository.insertOrUpdateProgress(any()) } returns Unit
        coEvery { mangaHandler.awaitOneOrNull<Long>(any(), any()) } returns 10L
        coEvery { animeHandler.awaitOneOrNull<Long>(any(), any()) } returns 0L

        val result = calculator.calculateInitialProgress()

        result.success shouldBe true
        assert(result.duration >= 0L)
    }

    @Test
    fun `retroactive calculation preserves existing unlocked timestamp`() = runTest {
        val originalUnlockedAt = 1_234_567_890L
        val achievement = Achievement(
            id = "manga_100",
            type = AchievementType.QUANTITY,
            category = AchievementCategory.MANGA,
            threshold = 100,
            points = 100,
            title = "Read 100 Chapters",
        )
        val existing = tachiyomi.domain.achievement.model.AchievementProgress(
            achievementId = "manga_100",
            progress = 150,
            maxProgress = 100,
            isUnlocked = true,
            unlockedAt = originalUnlockedAt,
        )

        val captured = mutableListOf<tachiyomi.domain.achievement.model.AchievementProgress>()

        coEvery { repository.getAll() } returns flowOf(listOf(achievement))
        coEvery { repository.getAllProgress() } returns flowOf(listOf(existing))
        coEvery { repository.insertOrUpdateProgress(capture(captured)) } returns Unit
        coEvery { mangaHandler.awaitOneOrNull<Long>(any(), any()) } returns 150L
        coEvery { diversityChecker.getGenreDiversity() } returns 0
        coEvery { diversityChecker.getSourceDiversity() } returns 0
        coEvery { diversityChecker.getMangaGenreDiversity() } returns 0
        coEvery { diversityChecker.getAnimeGenreDiversity() } returns 0
        coEvery { diversityChecker.getNovelGenreDiversity() } returns 0
        coEvery { diversityChecker.getMangaSourceDiversity() } returns 0
        coEvery { diversityChecker.getAnimeSourceDiversity() } returns 0
        coEvery { diversityChecker.getNovelSourceDiversity() } returns 0

        val result = calculator.calculateInitialProgress()

        result.success shouldBe true
        val saved = captured.single { it.achievementId == "manga_100" }
        saved.isUnlocked shouldBe true
        saved.unlockedAt shouldBe originalUnlockedAt
    }

    @Test
    fun `retroactive calculation recalculates user level from total points`() = runTest {
        val achievement = Achievement(
            id = "manga_100",
            type = AchievementType.QUANTITY,
            category = AchievementCategory.MANGA,
            threshold = 100,
            points = 900,
            title = "Read 100 Chapters",
        )

        coEvery { repository.getAll() } returns flowOf(listOf(achievement))
        coEvery { repository.getAllProgress() } returns flowOf(emptyList())
        coEvery { repository.insertOrUpdateProgress(any()) } returns Unit
        coEvery { mangaHandler.awaitOneOrNull<Long>(any(), any()) } returns 100L
        coEvery { diversityChecker.getGenreDiversity() } returns 0
        coEvery { diversityChecker.getSourceDiversity() } returns 0
        coEvery { diversityChecker.getMangaGenreDiversity() } returns 0
        coEvery { diversityChecker.getAnimeGenreDiversity() } returns 0
        coEvery { diversityChecker.getNovelGenreDiversity() } returns 0
        coEvery { diversityChecker.getMangaSourceDiversity() } returns 0
        coEvery { diversityChecker.getAnimeSourceDiversity() } returns 0
        coEvery { diversityChecker.getNovelSourceDiversity() } returns 0

        // Seed a default user profile row so updateXP has a target to update.
        database.userProfileQueries.insertProfileIfNotExists(
            user_id = "default",
            username = null,
            level = 1,
            current_xp = 0,
            xp_to_next_level = 100,
            total_xp = 0,
            titles = "[]",
            badges = "[]",
            unlocked_themes = "[]",
            achievements_unlocked = 0,
            total_achievements = 0,
            join_date = 0L,
            last_updated = 0L,
        )

        // 900 XP -> sqrt(9)+1 = 4
        calculator.calculateInitialProgress()

        val profile = database.userProfileQueries.getDefaultProfile().executeAsOneOrNull()
        profile shouldNotBe null
        profile!!.level shouldNotBe 1L
    }

    @Test
    fun `retroactive calculation replays rewards only for newly unlocked achievements`() = runTest {
        val alreadyUnlocked = Achievement(
            id = "manga_100",
            type = AchievementType.QUANTITY,
            category = AchievementCategory.MANGA,
            threshold = 100,
            points = 100,
            title = "Read 100 Chapters",
        )
        val newlyUnlocked = Achievement(
            id = "anime_50",
            type = AchievementType.QUANTITY,
            category = AchievementCategory.ANIME,
            threshold = 50,
            points = 50,
            title = "Watch 50 Episodes",
        )

        val existingManga100 = tachiyomi.domain.achievement.model.AchievementProgress(
            achievementId = "manga_100",
            progress = 150,
            maxProgress = 100,
            isUnlocked = true,
            unlockedAt = 1_700_000_000L,
        )

        coEvery { repository.getAll() } returns flowOf(listOf(alreadyUnlocked, newlyUnlocked))
        coEvery { repository.getAllProgress() } returns flowOf(listOf(existingManga100))
        coEvery { repository.insertOrUpdateProgress(any()) } returns Unit
        coEvery { mangaHandler.awaitOneOrNull<Long>(any(), any()) } returns 150L
        coEvery { animeHandler.awaitOneOrNull<Long>(any(), any()) } returns 50L
        coEvery { diversityChecker.getGenreDiversity() } returns 0
        coEvery { diversityChecker.getSourceDiversity() } returns 0
        coEvery { diversityChecker.getMangaGenreDiversity() } returns 0
        coEvery { diversityChecker.getAnimeGenreDiversity() } returns 0
        coEvery { diversityChecker.getNovelGenreDiversity() } returns 0
        coEvery { diversityChecker.getMangaSourceDiversity() } returns 0
        coEvery { diversityChecker.getAnimeSourceDiversity() } returns 0
        coEvery { diversityChecker.getNovelSourceDiversity() } returns 0

        calculator.calculateInitialProgress()

        coVerify(exactly = 0) {
            unlockableManager.unlockAchievementRewards(match { it.id == "manga_100" })
        }
        coVerify(exactly = 1) {
            unlockableManager.unlockAchievementRewards(match { it.id == "anime_50" })
        }
    }

    @Test
    fun `retroactive unlock still grants non xp rewards once`() = runTest {
        val achievementWithRewards = Achievement(
            id = "manga_100",
            type = AchievementType.QUANTITY,
            category = AchievementCategory.MANGA,
            threshold = 100,
            points = 100,
            title = "Read 100 Chapters",
            rewards = listOf(
                tachiyomi.domain.achievement.model.Reward.badge(
                    badgeId = "early_adopter",
                    badgeName = "Early Adopter",
                ),
            ),
        )

        coEvery { repository.getAll() } returns flowOf(listOf(achievementWithRewards))
        coEvery { repository.getAllProgress() } returns flowOf(emptyList())
        coEvery { repository.insertOrUpdateProgress(any()) } returns Unit
        coEvery { mangaHandler.awaitOneOrNull<Long>(any(), any()) } returns 150L
        coEvery { diversityChecker.getGenreDiversity() } returns 0
        coEvery { diversityChecker.getSourceDiversity() } returns 0
        coEvery { diversityChecker.getMangaGenreDiversity() } returns 0
        coEvery { diversityChecker.getAnimeGenreDiversity() } returns 0
        coEvery { diversityChecker.getNovelGenreDiversity() } returns 0
        coEvery { diversityChecker.getMangaSourceDiversity() } returns 0
        coEvery { diversityChecker.getAnimeSourceDiversity() } returns 0
        coEvery { diversityChecker.getNovelSourceDiversity() } returns 0

        calculator.calculateInitialProgress()

        coVerify(exactly = 1) {
            unlockableManager.unlockAchievementRewards(match { it.id == "manga_100" })
        }
        coVerify(exactly = 1) {
            userProfileManager.grantRewards(
                match { rewards ->
                    rewards.size == 1 &&
                        rewards.single().type == tachiyomi.domain.achievement.model.RewardType.BADGE &&
                        rewards.single().id == "badge_early_adopter"
                },
            )
        }
    }
}
