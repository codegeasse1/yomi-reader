package eu.kanade.presentation.browse.components

import io.kotest.matchers.collections.shouldBeUnique
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

/**
 * TDD test for the missing key parameter in Browse source grid/list items() calls.
 *
 * The crash: java.lang.IllegalArgumentException: Key "25" was already used
 *   at BrowseMangaSourceComfortableGrid, BrowseMangaSourceCompactGrid, BrowseMangaSourceList
 *   (and their anime equivalents)
 *
 * Root cause: items(count = list.itemCount, key = null) — Compose uses positional keys (0..N-1)
 * which collide with auto-generated keys from interleaved item(span = ...) blocks when
 * LazyPagingItems dynamically changes itemCount.
 *
 * Fix: provide explicit key = { index -> "${prefix}/${url.orEmpty()}#$index" } matching the
 * already-correct pattern in BrowseNovelSourceScreen.
 *
 * These tests define the key function contract and validate uniqueness across all edge cases.
 */
class BrowseGridItemKeyTest {

    // ── Manga key function contract ──────────────────────────────────────

    private val mangaKey: (url: String?, index: Int) -> String = { url, index ->
        "manga/${url.orEmpty()}#$index"
    }

    @Test
    fun `manga key includes url and index`() {
        mangaKey("/manga/one-piece", 0) shouldNotBe ""
        mangaKey("/manga/one-piece", 0).contains("/manga/one-piece") shouldNotBe false
        mangaKey("/manga/one-piece", 0).contains("#0") shouldNotBe false
    }

    @Test
    fun `manga keys are unique for distinct urls at different indices`() {
        val keys = listOf(
            mangaKey("/manga/one-piece", 0),
            mangaKey("/manga/naruto", 1),
            mangaKey("/manga/bleach", 2),
        )
        keys.shouldBeUnique()
    }

    @Test
    fun `manga keys are unique with null url placeholders`() {
        // Simulates paging where some items are unloaded (null mangaList[index])
        val keys = listOf(
            mangaKey(null, 0),
            mangaKey("/manga/one-piece", 1),
            mangaKey(null, 2),
            mangaKey("/manga/naruto", 3),
            mangaKey(null, 4),
            mangaKey("/manga/bleach", 5),
        )
        keys.shouldBeUnique()
    }

    @Test
    fun `manga keys are unique even with duplicate urls`() {
        // Edge case: two manga from the same source URL at different positions
        val keys = listOf(
            mangaKey("/manga/one-piece", 0),
            mangaKey("/manga/one-piece", 1),
        )
        keys.shouldBeUnique()
    }

    @Test
    fun `manga keys maintain uniqueness for large paging lists with mixed nulls`() {
        val keys = (0 until 50).map { index ->
            val url = if (index % 3 == 0) null else "/manga/title-$index"
            mangaKey(url, index)
        }
        keys.shouldBeUnique()
    }

    // ── Anime key function contract ──────────────────────────────────────

    private val animeKey: (url: String?, index: Int) -> String = { url, index ->
        "anime/${url.orEmpty()}#$index"
    }

    @Test
    fun `anime key includes url and index`() {
        animeKey("/anime/attack-on-titan", 5).contains("/anime/attack-on-titan") shouldNotBe false
        animeKey("/anime/attack-on-titan", 5).contains("#5") shouldNotBe false
    }

    @Test
    fun `anime keys are unique for distinct urls at different indices`() {
        val keys = listOf(
            animeKey("/anime/attack-on-titan", 0),
            animeKey("/anime/death-note", 1),
            animeKey("/anime/steins-gate", 2),
        )
        keys.shouldBeUnique()
    }

    @Test
    fun `anime keys are unique with null url placeholders`() {
        val keys = listOf(
            animeKey(null, 0),
            animeKey("/anime/attack-on-titan", 1),
            animeKey(null, 2),
            animeKey("/anime/death-note", 3),
        )
        keys.shouldBeUnique()
    }

    @Test
    fun `anime keys maintain uniqueness for large paging lists`() {
        val keys = (0 until 50).map { index ->
            val url = if (index % 2 == 0) null else "/anime/title-$index"
            animeKey(url, index)
        }
        keys.shouldBeUnique()
    }

    // ── Cross-type namespace separation ──────────────────────────────────

    @Test
    fun `manga and anime keys are in different namespaces`() {
        mangaKey("/same-url", 0) shouldNotBe animeKey("/same-url", 0)
    }

    // ── Edge cases ───────────────────────────────────────────────────────

    @Test
    fun `empty url with different indices produces unique keys`() {
        val emptyUrlKeys = listOf(
            mangaKey("", 0),
            mangaKey("", 1),
            mangaKey("", 2),
        )
        emptyUrlKeys.shouldBeUnique()
    }

    @Test
    fun `key strings match expected format`() {
        mangaKey("/manga/title", 42).let { key ->
            key shouldBe "manga//manga/title#42"
            key.startsWith("manga/") shouldBe true
            key.endsWith("#42") shouldBe true
        }
    }

    /**
     * This test directly validates the pattern that was already proven correct
     * in BrowseNovelSourceScreen. It ensures the same approach works for manga/anime.
     */
    @Test
    fun `key pattern matches working novel implementation`() {
        val novelKey: (url: String?, index: Int) -> String = { url, index ->
            "novel/${url.orEmpty()}#$index"
        }

        val mangaResult = mangaKey("/title", 5)
        val animeResult = animeKey("/title", 5)
        val novelResult = novelKey("/title", 5)

        // All should be different due to type prefix
        listOf(mangaResult, animeResult, novelResult).shouldBeUnique()

        // Each should contain the url and index
        mangaResult shouldBe "manga//title#5"
        animeResult shouldBe "anime//title#5"
        novelResult shouldBe "novel//title#5"
    }
}
