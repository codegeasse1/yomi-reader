package eu.kanade.tachiyomi.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupAnimeSource
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.LegacyBackup
import eu.kanade.tachiyomi.data.backup.models.MediaRoutingPolicy
import eu.kanade.tachiyomi.data.backup.models.MihonBackup
import eu.kanade.tachiyomi.data.backup.models.mergeLegacyPayloadIfPresent
import eu.kanade.tachiyomi.data.backup.models.toMihonBackupManga
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AniyomiBackupRestoreDiagnosisTest {

    private val parser = ProtoBuf

    @Test
    fun `legacy Aniyomi backup with anime at field 3 restores anime via LegacyBackup path`() {
        val legacy = LegacyBackup(
            backupManga = listOf(sampleManga("Manga A")),
            backupAnime = listOf(sampleAnime("Anime A")),
            backupAnimeSources = listOf(BackupAnimeSource(name = "AnimeSrc", sourceId = 42)),
        )
        val bytes = parser.encodeToByteArray(LegacyBackup.serializer(), legacy)

        assertTrue(BackupDetector.isLegacyBackup(bytes))

        val decoded = parser.decodeFromByteArray(LegacyBackup.serializer(), bytes).toBackup()
        assertEquals(listOf("Manga A"), decoded.backupManga.map { it.title })
        assertEquals(listOf("Anime A"), decoded.backupAnime.map { it.title })
    }

    @Test
    fun `legacy Aniyomi backup with anime but empty animeSources is detected as legacy`() {
        val legacy = LegacyBackup(
            backupManga = listOf(sampleManga("Manga A")),
            backupAnime = listOf(sampleAnime("Anime A")),
            backupAnimeSources = emptyList(),
        )
        val bytes = parser.encodeToByteArray(LegacyBackup.serializer(), legacy)

        assertTrue(BackupDetector.isLegacyBackup(bytes))
        assertFalse(BackupDetector.isMihonBackup(bytes))

        val decoded = parser.decodeFromByteArray(LegacyBackup.serializer(), bytes).toBackup()
        assertEquals(listOf("Anime A"), decoded.backupAnime.map { it.title })
    }

    @Test
    fun `legacy-shaped backup without categories is not misdetected as Mihon`() {
        val legacy = LegacyBackup(
            backupManga = listOf(sampleManga("Manga A")),
            backupAnime = listOf(sampleAnime("Anime A")),
            backupAnimeSources = emptyList(),
        )
        val bytes = parser.encodeToByteArray(LegacyBackup.serializer(), legacy)

        assertFalse(BackupDetector.isMihonBackup(bytes))

        val decoded = parser.decodeFromByteArray(LegacyBackup.serializer(), bytes).toBackup()
        assertEquals(listOf("Manga A"), decoded.backupManga.map { it.title })
        assertEquals(listOf("Anime A"), decoded.backupAnime.map { it.title })
    }

    @Test
    fun `native backup decode merges legacy anime from field 3 when field 501 is empty`() {
        val legacy = LegacyBackup(
            backupManga = listOf(sampleManga("Manga A")),
            backupAnime = listOf(sampleAnime("Anime A")),
            backupAnimeCategories = listOf(BackupCategory(name = "Watching", order = 0)),
            backupAnimeSources = emptyList(),
        )
        val bytes = parser.encodeToByteArray(LegacyBackup.serializer(), legacy)

        val decoded = parser.decodeFromByteArray(Backup.serializer(), bytes)
            .mergeLegacyPayloadIfPresent(parser.decodeFromByteArray(LegacyBackup.serializer(), bytes))

        assertEquals(listOf("Manga A"), decoded.backupManga.map { it.title })
        assertEquals(listOf("Anime A"), decoded.backupAnime.map { it.title })
        assertEquals(listOf("Watching"), decoded.backupAnimeCategories.map { it.name })
    }

    @Test
    fun `modern Aniyomi backup with anime at field 501 restores correctly`() {
        val modern = Backup(
            backupManga = listOf(sampleManga("Manga A")),
            backupAnime = listOf(sampleAnime("Anime A")),
            isLegacy = false,
        )
        val bytes = parser.encodeToByteArray(Backup.serializer(), modern)

        assertFalse(BackupDetector.isMihonBackup(bytes))
        assertFalse(BackupDetector.isLegacyBackup(bytes))

        val decoded = parser.decodeFromByteArray(Backup.serializer(), bytes)
        assertEquals(listOf("Manga A"), decoded.backupManga.map { it.title })
        assertEquals(listOf("Anime A"), decoded.backupAnime.map { it.title })
    }

    @Test
    fun `native decoder preserves declared media sections when source ids collide`() {
        val native = Backup(
            backupManga = listOf(sampleManga("Manga collision").copy(source = 77, url = "/manga")),
            backupNovel = listOf(
                eu.kanade.tachiyomi.data.backup.models.BackupNovel(
                    source = 77,
                    url = "/novel",
                    title = "Novel collision",
                ),
            ),
            backupNovelSources = listOf(
                eu.kanade.tachiyomi.data.backup.models.BackupSource(name = "NovelSrc", sourceId = 77),
            ),
            isLegacy = false,
        )
        val bytes = parser.encodeToByteArray(Backup.serializer(), native)
        val contentResolver = mockk<ContentResolver>()
        val context = mockk<Context>()
        val uri = mockk<Uri>()
        every { context.contentResolver } returns contentResolver
        every { contentResolver.openInputStream(uri) } returns bytes.inputStream()

        val decoded = BackupDecoder(
            context = context,
            parser = parser,
            mangaSourceManager = mockk(relaxed = true),
            novelSourceManager = mockk(relaxed = true),
            animeSourceManager = mockk(relaxed = true),
        ).decode(uri)

        assertEquals(listOf("Manga collision"), decoded.backupManga.map { it.title })
        assertEquals(listOf("Novel collision"), decoded.backupNovel.map { it.title })
        assertTrue(decoded.backupAnime.isEmpty())
    }

    @Test
    fun `external mihon entries never become anime even when an anime source is declared`() {
        val backup = Backup(
            backupManga = listOf(sampleManga("Anime entry").copy(source = 99)),
            backupAnimeSources = listOf(BackupAnimeSource(name = "AnimeSrc", sourceId = 99)),
        )

        val routed = MediaRoutingPolicy.ExternalMihonAsManga(
            novelSourceClassifier = { false },
            animeSourceClassifier = { it == 99L },
        ).route(backup)

        // A Mihon field 1 library is a manga library. The colliding id is only reported.
        assertEquals(listOf("Anime entry"), routed.backup.backupManga.map { it.title })
        assertTrue(routed.backup.backupAnime.isEmpty())
        assertEquals(1, routed.ambiguousEntries)
    }

    @Test
    fun `opt-in fallback keeps entry as manga when the id is known to two libraries`() {
        val backup = Backup(
            backupManga = listOf(sampleManga("Collision").copy(source = 77)),
        )

        val routed = MediaRoutingPolicy.LegacySisterExplicitFallback(
            mangaSourceClassifier = { it == 77L },
            novelSourceClassifier = { it == 77L },
            animeSourceClassifier = { false },
        ).route(backup)

        assertEquals(listOf("Collision"), routed.backup.backupManga.map { it.title })
        assertTrue(routed.backup.backupNovel.isEmpty())
        assertTrue(routed.backup.backupAnime.isEmpty())
        assertEquals(1, routed.ambiguousEntries)
    }

    @Test
    fun `opt-in fallback moves an entry only when its source is novel-only`() {
        val backup = Backup(
            backupManga = listOf(sampleManga("Novel entry").copy(source = 66)),
        )

        val routed = MediaRoutingPolicy.LegacySisterExplicitFallback(
            mangaSourceClassifier = { false },
            novelSourceClassifier = { it == 66L },
            animeSourceClassifier = { false },
        ).route(backup)

        assertTrue(routed.backup.backupManga.isEmpty())
        assertEquals(listOf("Novel entry"), routed.backup.backupNovel.map { it.title })
        assertEquals(0, routed.ambiguousEntries)
    }

    @Test
    fun `opt-in fallback leaves unknown sources as manga`() {
        val backup = Backup(
            backupManga = listOf(sampleManga("Unknown").copy(source = 88)),
        )

        val routed = MediaRoutingPolicy.LegacySisterExplicitFallback(
            mangaSourceClassifier = { false },
            novelSourceClassifier = { false },
            animeSourceClassifier = { false },
        ).route(backup)

        assertEquals(listOf("Unknown"), routed.backup.backupManga.map { it.title })
        assertTrue(routed.backup.backupNovel.isEmpty())
    }

    @Test
    fun `Mihon field one entries stay manga when anime source is installed`() {
        val mihon = MihonBackup(
            backupManga = listOf(
                sampleManga("Manga A").copy(source = 1).toMihonBackupManga(),
                sampleManga("Anime posing as manga").copy(source = 99).toMihonBackupManga(),
            ),
            backupSources = listOf(
                eu.kanade.tachiyomi.data.backup.models.BackupSource(name = "M", sourceId = 1),
                eu.kanade.tachiyomi.data.backup.models.BackupSource(name = "A", sourceId = 99),
            ),
        )
        val bytes = parser.encodeToByteArray(MihonBackup.serializer(), mihon)

        val withAnimeExt = decodeViaMihon(bytes)
        assertEquals(
            listOf("Manga A", "Anime posing as manga"),
            withAnimeExt.backupManga.map { it.title },
        )
        assertTrue(withAnimeExt.backupAnime.isEmpty())
        assertTrue(withAnimeExt.backupNovel.isEmpty())
    }

    private fun decodeViaMihon(bytes: ByteArray): Backup {
        return parser.decodeFromByteArray(MihonBackup.serializer(), bytes).toYomiBackup()
    }

    private fun sampleManga(title: String): BackupManga {
        return BackupManga(source = 1, url = "/manga", title = title)
    }

    private fun sampleAnime(title: String): BackupAnime {
        return BackupAnime(source = 42, url = "/anime", title = title)
    }
}
