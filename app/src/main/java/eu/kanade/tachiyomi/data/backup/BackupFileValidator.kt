package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import eu.kanade.tachiyomi.data.track.TrackerManager
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.source.novel.service.NovelSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class BackupFileValidator(
    private val context: Context,
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val mangaSourceManager: MangaSourceManager = Injekt.get(),
    private val novelSourceManager: NovelSourceManager = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
) {

    /**
     * Checks for critical backup file data.
     *
     * @param policy import rules chosen by the user; affects how entries are split.
     * @param expected when set, the content the caller believes the file holds. Used right after
     * writing a backup to prove nothing was lost or truncated on the way to storage.
     * @return Missing sources/trackers plus a content summary for preview UI.
     */
    fun validate(
        uri: Uri,
        policy: BackupImportPolicy = BackupImportPolicy.Default,
        expected: BackupContentSummary? = null,
    ): Results {
        val decoded = try {
            BackupDecoder(context).decodeDetailed(uri, policy)
        } catch (e: Exception) {
            throw IllegalStateException(e)
        }
        if (expected != null) {
            val actual = decoded.backup.contentSummary()
            check(actual == expected) {
                "Backup content mismatch: expected $expected but the stored file contains $actual"
            }
        }
        val backup = decoded.backup

        val sources = backup.backupSources.associate { it.sourceId to it.name }
        val animeSources = backup.backupAnimeSources.associate { it.sourceId to it.name }
        val novelSources = backup.backupNovelSources.associate { it.sourceId to it.name }
        val missingSources = sources
            .filter { mangaSourceManager.get(it.key) == null }
            .values.map {
                val id = it.toLongOrNull()
                if (id == null) {
                    it
                } else {
                    mangaSourceManager.getOrStub(id).toString()
                }
            }
            .distinct()
            .sorted() +
            animeSources
                .filter { animeSourceManager.get(it.key) == null }
                .values.map {
                    val id = it.toLongOrNull()
                    if (id == null) {
                        it
                    } else {
                        animeSourceManager.getOrStub(id).toString()
                    }
                }
                .distinct()
                .sorted() +
            novelSources
                .filter { novelSourceManager.get(it.key) == null }
                .values.map {
                    val id = it.toLongOrNull()
                    if (id == null) {
                        it
                    } else {
                        novelSourceManager.getOrStub(id).toString()
                    }
                }
                .distinct()
                .sorted()

        val animeTrackers = backup.backupAnime
            .flatMap { it.tracking }
            .map { it.syncId }
        val mangaTrackers = backup.backupManga
            .flatMap { it.tracking }
            .map { it.syncId }
        val trackers = (animeTrackers + mangaTrackers).distinct()
        val missingTrackers = trackers
            .mapNotNull { trackerManager.get(it.toLong()) }
            .filter { !it.isLoggedIn }
            .map { it.name }
            .sorted()

        return Results(
            missingSources = missingSources,
            missingTrackers = missingTrackers,
            inspection = BackupInspection.of(decoded),
        )
    }

    data class Results(
        val missingSources: List<String>,
        val missingTrackers: List<String>,
        val inspection: BackupInspection,
    )
}
