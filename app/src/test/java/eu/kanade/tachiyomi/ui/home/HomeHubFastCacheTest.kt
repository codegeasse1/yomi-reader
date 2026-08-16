package eu.kanade.tachiyomi.ui.home

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HomeHubFastCacheTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    @BeforeEach
    fun setup() {
        context = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        editor = mockk(relaxed = true)

        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.apply() } returns Unit
    }

    @Test
    fun `save and load preserve warm state in memory for Anime`() {
        every { context.getSharedPreferences("home_hub_cache", Context.MODE_PRIVATE) } returns prefs

        val cache = HomeHubFastCache(context, HomeHubSection.Anime)
        val state = CachedHomeState(
            hero = CachedHeroItem(
                entryId = 1L,
                title = "Anime Hero",
                progressNumber = 12.0,
                coverUrl = "https://example.org/anime.jpg",
                coverLastModified = 101L,
                subId = 10L,
            ),
            history = listOf(
                CachedHistoryItem(
                    entryId = 2L,
                    title = "Anime History",
                    progressNumber = 11.0,
                    coverUrl = "https://example.org/anime-history.jpg",
                    coverLastModified = 102L,
                ),
            ),
            recommendations = listOf(
                CachedRecommendationItem(
                    entryId = 3L,
                    title = "Anime Rec",
                    coverUrl = "https://example.org/anime-rec.jpg",
                    coverLastModified = 103L,
                    totalCount = 12L,
                    progressCount = 6L,
                ),
            ),
            userName = "Otaku",
            userAvatar = "/tmp/avatar_anime.jpg",
            isInitialized = true,
        )

        cache.save(state)
        val loaded = cache.load()

        assertEquals(1L, loaded.hero?.entryId)
        assertEquals(10L, loaded.hero?.subId)
        assertEquals("Anime Hero", loaded.hero?.title)
        assertEquals(1, loaded.history.size)
        assertEquals(2L, loaded.history.first().entryId)
        assertEquals(1, loaded.recommendations.size)
        assertEquals(6L, loaded.recommendations.first().progressNumerator)
        assertEquals("Otaku", loaded.userName)
        assertEquals("/tmp/avatar_anime.jpg", loaded.userAvatar)
        assertTrue(loaded.isInitialized)
        assertFalse(loaded.isEmpty)
    }

    @Test
    fun `backward compatibility with legacy Anime JSON`() {
        every { context.getSharedPreferences("home_hub_cache", Context.MODE_PRIVATE) } returns prefs

        val heroJson = """
            {"animeId":1,"title":"Anime Hero","episodeNumber":12.0,"coverUrl":"url","coverLastModified":100,"episodeId":10}
        """.trimIndent()
        val historyJson = """
            [{"animeId":2,"title":"Anime History","episodeNumber":11.0,"coverUrl":"url","coverLastModified":102}]
        """.trimIndent()
        val recommendationsJson = """
            [{"animeId":3,"title":"Anime Rec","coverUrl":"url","coverLastModified":103,"totalCount":12,"seenCount":6}]
        """.trimIndent()

        every { prefs.getString("hero", null) } returns heroJson
        every { prefs.getString("history", null) } returns historyJson
        every { prefs.getString("recommendations", null) } returns recommendationsJson
        every { prefs.getString("user_name", "") } returns "Otaku"
        every { prefs.getString("user_avatar", "") } returns "/tmp/avatar_anime.jpg"
        every { prefs.getBoolean("initialized", false) } returns true

        val cache = HomeHubFastCache(context, HomeHubSection.Anime)
        val loaded = cache.load()

        assertEquals(1L, loaded.hero?.entryId)
        assertEquals(10L, loaded.hero?.subId)
        assertEquals(12.0, loaded.hero?.progressNumber)
        assertEquals(2L, loaded.history.first().entryId)
        assertEquals(3L, loaded.recommendations.first().entryId)
        assertEquals(6L, loaded.recommendations.first().progressNumerator)
    }

    @Test
    fun `backward compatibility with legacy Manga JSON`() {
        every { context.getSharedPreferences("manga_home_hub_cache", Context.MODE_PRIVATE) } returns prefs

        val heroJson = """
            {"mangaId":10,"title":"Manga Hero","chapterNumber":24.5,"coverUrl":"url","coverLastModified":200,"chapterId":20}
        """.trimIndent()
        val historyJson = """
            [{"mangaId":20,"title":"Manga History","chapterNumber":23.0,"coverUrl":"url","coverLastModified":202}]
        """.trimIndent()
        val recommendationsJson = """
            [{"mangaId":30,"title":"Manga Rec","coverUrl":"url","coverLastModified":203,"totalCount":10,"unreadCount":4}]
        """.trimIndent()

        every { prefs.getString("hero", null) } returns heroJson
        every { prefs.getString("history", null) } returns historyJson
        every { prefs.getString("recommendations", null) } returns recommendationsJson
        every { prefs.getString("user_name", "") } returns "Reader"
        every { prefs.getString("user_avatar", "") } returns "/tmp/avatar_manga.jpg"
        every { prefs.getBoolean("initialized", false) } returns true

        val cache = HomeHubFastCache(context, HomeHubSection.Manga)
        val loaded = cache.load()

        assertEquals(10L, loaded.hero?.entryId)
        assertEquals(20L, loaded.hero?.subId)
        assertEquals(24.5, loaded.hero?.progressNumber)
        assertEquals(20L, loaded.history.first().entryId)
        assertEquals(30L, loaded.recommendations.first().entryId)
        assertEquals(6L, loaded.recommendations.first().progressNumerator) // 10 - 4
    }

    @Test
    fun `backward compatibility with legacy Novel JSON`() {
        every { context.getSharedPreferences("novel_home_hub_cache", Context.MODE_PRIVATE) } returns prefs

        val heroJson = """
            {"novelId":100,"title":"Novel Hero","chapterNumber":5.0,"coverUrl":"url","coverLastModified":300,"chapterId":300}
        """.trimIndent()
        val historyJson = """
            [{"novelId":200,"title":"Novel History","chapterNumber":4.0,"coverUrl":"url","coverLastModified":302}]
        """.trimIndent()
        val recommendationsJson = """
            [{"novelId":300,"title":"Novel Rec","coverUrl":"url","coverLastModified":303,"totalCount":20,"readCount":15}]
        """.trimIndent()

        every { prefs.getString("hero", null) } returns heroJson
        every { prefs.getString("history", null) } returns historyJson
        every { prefs.getString("recommendations", null) } returns recommendationsJson
        every { prefs.getString("user_name", "") } returns "Writer"
        every { prefs.getString("user_avatar", "") } returns "/tmp/avatar_novel.jpg"
        every { prefs.getBoolean("initialized", false) } returns true

        val cache = HomeHubFastCache(context, HomeHubSection.Novel)
        val loaded = cache.load()

        assertEquals(100L, loaded.hero?.entryId)
        assertEquals(300L, loaded.hero?.subId)
        assertEquals(5.0, loaded.hero?.progressNumber)
        assertEquals(200L, loaded.history.first().entryId)
        assertEquals(300L, loaded.recommendations.first().entryId)
        assertEquals(15L, loaded.recommendations.first().progressNumerator)
    }
}
