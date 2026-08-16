package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.Backup

/**
 * A decoded backup together with its detected origin and the policy that produced it.
 *
 * [ambiguousSourceIds] counts entries whose media type could not be established without guessing.
 * It is shown in the restore preview so the user can see, before anything is written, that a file
 * cannot be fully classified.
 */
data class DecodedBackup(
    val backup: Backup,
    val origin: BackupOrigin,
    val policy: BackupImportPolicy = BackupImportPolicy.Default,
    val ambiguousSourceIds: Int = 0,
) {
    val summary: BackupContentSummary
        get() = backup.contentSummary()
}

/** Exact per media type counts of a decoded backup. */
fun Backup.contentSummary(): BackupContentSummary = BackupContentSummary(
    mangaCount = backupManga.size,
    animeCount = backupAnime.size,
    novelCount = backupNovel.size,
    categoriesCount = backupCategories.size + backupAnimeCategories.size + backupNovelCategories.size,
)

/**
 * Human-oriented summary of what a backup file contains, used to preview the
 * contents before restoring. Pure function over the decoded model so it can be
 * unit tested without Android dependencies.
 */
data class BackupInspection(
    val origin: BackupOrigin,
    val mangaCount: Int,
    val animeCount: Int,
    val novelCount: Int,
    val categoriesCount: Int,
    val hasAppSettings: Boolean,
    val hasSourceSettings: Boolean,
    val hasExtensions: Boolean,
    val hasAchievements: Boolean,
    val hasExtensionRepos: Boolean,
    val ambiguousSourceIds: Int = 0,
) {

    val isEmpty: Boolean
        get() = mangaCount == 0 &&
            animeCount == 0 &&
            novelCount == 0 &&
            categoriesCount == 0 &&
            !hasAppSettings &&
            !hasSourceSettings &&
            !hasExtensions &&
            !hasAchievements &&
            !hasExtensionRepos

    /**
     * Whether the "this is an old Yomi sister backup" opt-in may be offered for this file.
     *
     * Only a markerless Mihon shaped payload qualifies: a native, legacy, LNReader or
     * manifest-carrying sister backup already knows its own media types, so re-routing them by
     * source id could only corrupt an otherwise correct restore.
     */
    val canOfferLegacySisterImport: Boolean
        get() = origin.isMihonDerived && mangaCount > 0

    companion object {
        fun of(decoded: DecodedBackup): BackupInspection =
            of(decoded.backup, decoded.origin, decoded.ambiguousSourceIds)

        fun of(backup: Backup, origin: BackupOrigin, ambiguousSourceIds: Int = 0): BackupInspection {
            return BackupInspection(
                origin = origin,
                mangaCount = backup.backupManga.size,
                animeCount = backup.backupAnime.size,
                novelCount = backup.backupNovel.size,
                categoriesCount = backup.backupCategories.size +
                    backup.backupAnimeCategories.size +
                    backup.backupNovelCategories.size,
                hasAppSettings = backup.backupPreferences.isNotEmpty(),
                hasSourceSettings = backup.backupSourcePreferences.isNotEmpty(),
                hasExtensions = backup.backupExtensions.isNotEmpty(),
                hasAchievements = backup.backupAchievements.isNotEmpty() || backup.backupStats != null,
                hasExtensionRepos = backup.backupMangaExtensionRepo.isNotEmpty() ||
                    backup.backupAnimeExtensionRepo.isNotEmpty() ||
                    backup.backupNovelExtensionRepo.isNotEmpty() ||
                    backup.backupMangaExtensionStore.isNotEmpty() ||
                    backup.backupAnimeExtensionStore.isNotEmpty() ||
                    backup.backupNovelExtensionStore.isNotEmpty(),
                ambiguousSourceIds = ambiguousSourceIds,
            )
        }
    }
}
