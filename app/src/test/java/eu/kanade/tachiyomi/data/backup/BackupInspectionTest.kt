package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.backup.models.BackupAnime
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import eu.kanade.tachiyomi.data.backup.models.BackupNovel
import eu.kanade.tachiyomi.data.backup.restore.RestoreOptions
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupInspectionTest {

    @Test
    fun `empty backup is reported as empty`() {
        val inspection = BackupInspection.of(Backup(), BackupOrigin.TADAMI)

        assertTrue(inspection.isEmpty)
        assertEquals(BackupOrigin.TADAMI, inspection.origin)
        assertEquals(0, inspection.mangaCount)
        assertEquals(0, inspection.animeCount)
        assertEquals(0, inspection.novelCount)
        assertFalse(inspection.hasAppSettings)
    }

    @Test
    fun `counts entries and categories across all library types`() {
        val backup = Backup(
            backupManga = listOf(sampleManga("Manga A"), sampleManga("Manga B")),
            backupAnime = listOf(sampleAnime("Anime A")),
            backupNovel = listOf(sampleNovel("Novel A")),
            backupCategories = listOf(BackupCategory(name = "Manga cat")),
            backupAnimeCategories = listOf(BackupCategory(name = "Anime cat")),
        )

        val inspection = BackupInspection.of(backup, BackupOrigin.MIHON)

        assertFalse(inspection.isEmpty)
        assertEquals(2, inspection.mangaCount)
        assertEquals(1, inspection.animeCount)
        assertEquals(1, inspection.novelCount)
        assertEquals(2, inspection.categoriesCount)
        assertEquals(BackupOrigin.MIHON, inspection.origin)
    }

    @Test
    fun `inspection of decoded backup keeps origin`() {
        val decoded = DecodedBackup(Backup(), BackupOrigin.LEGACY_ANIYOMI)

        assertEquals(BackupOrigin.LEGACY_ANIYOMI, BackupInspection.of(decoded).origin)
    }

    @Test
    fun `backup options survive boolean array round trip`() {
        val options = BackupOptions(
            libraryEntries = true,
            backupManga = true,
            backupAnime = false,
            backupNovel = true,
            categories = false,
            chapters = true,
            tracking = false,
            history = true,
            readEntries = false,
            appSettings = true,
            extensionRepoSettings = false,
            customButton = true,
            sourceSettings = false,
            privateSettings = true,
            extensions = true,
            achievements = false,
            stats = true,
            sisterAppCompatible = true,
        )

        assertEquals(options, BackupOptions.fromBooleanArray(options.asBooleanArray()))
    }

    @Test
    fun `restore options survive boolean array round trip`() {
        val options = RestoreOptions(
            libraryEntries = true,
            restoreManga = false,
            restoreAnime = true,
            restoreNovel = false,
            categories = true,
            appSettings = false,
            extensionRepoSettings = true,
            customButtons = false,
            sourceSettings = true,
            extensions = true,
            achievements = false,
            stats = true,
        )

        assertEquals(options, RestoreOptions.fromBooleanArray(options.asBooleanArray()))
    }

    private fun sampleManga(title: String): BackupManga {
        return BackupManga(source = 1, url = "/manga", title = title)
    }

    private fun sampleAnime(title: String): BackupAnime {
        return BackupAnime(source = 42, url = "/anime", title = title)
    }

    private fun sampleNovel(title: String): BackupNovel {
        return BackupNovel(source = 7, url = "/novel", title = title)
    }
}
