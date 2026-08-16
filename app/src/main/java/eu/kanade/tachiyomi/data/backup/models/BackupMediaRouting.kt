package eu.kanade.tachiyomi.data.backup.models

/**
 * Result of applying a media routing policy to a decoded payload.
 *
 * [ambiguousEntries] counts entries the policy refused to classify. They stay in the manga section
 * (the section they were declared in) and are reported to the user instead of being guessed.
 */
data class RoutedBackup(
    val backup: Backup,
    val ambiguousEntries: Int = 0,
)

/**
 * How a decoded payload is split into manga, anime and novel sections.
 *
 * There is deliberately no universal policy: the media type of an entry can only come from the
 * section it was declared in, or from an explicit Yomi manifest, or from an explicit user choice.
 * A source id is not a global media type identifier and is never used to infer one on its own.
 */
sealed interface MediaRoutingPolicy {

    fun route(backup: Backup): RoutedBackup

    /**
     * Native Yomi and legacy Aniyomi backups: the sections are already unambiguous, so nothing is
     * reclassified. Field 1 is manga, 3/501 is anime, 5/508 is novel.
     */
    data object PreserveDeclaredSections : MediaRoutingPolicy {
        override fun route(backup: Backup): RoutedBackup = RoutedBackup(backup)
    }

    /**
     * External Mihon, TachiyomiSY and Komikku backups: field 1 is a manga library, full stop.
     *
     * Entries are never moved into the novel or anime sections, whatever extensions happen to be
     * installed. Source ids that also exist as novel or anime sources are only counted, so the
     * restore preview can mention them.
     */
    data class ExternalMihonAsManga(
        private val novelSourceClassifier: (Long) -> Boolean = { false },
        private val animeSourceClassifier: (Long) -> Boolean = { false },
    ) : MediaRoutingPolicy {
        override fun route(backup: Backup): RoutedBackup {
            val ambiguous = backup.backupManga
                .map { it.source }
                .distinct()
                .count { novelSourceClassifier(it) || animeSourceClassifier(it) }
            return RoutedBackup(backup, ambiguousEntries = ambiguous)
        }
    }

    /**
     * Yomi sister app compatible backups: the manifest is the only source of truth.
     *
     * Every flattened entry is restored as the type it was exported as, keyed by (sourceId, url), so
     * a LNReader novel comes back as a novel even when its source id collides with a manga source or
     * when no novel extension is installed at all.
     */
    data class RestoreFromYomiManifest(
        private val hints: Map<Pair<Long, String>, YomiMediaType>,
    ) : MediaRoutingPolicy {
        override fun route(backup: Backup): RoutedBackup {
            val mangas = mutableListOf<BackupManga>()
            val novels = backup.backupNovel.toMutableList()
            val animes = backup.backupAnime.toMutableList()
            var ambiguous = 0

            backup.backupManga.forEach { entry ->
                when (hints[entry.source to entry.url]) {
                    YomiMediaType.NOVEL -> novels += entry.toBackupNovel()
                    YomiMediaType.ANIME -> animes += entry.toBackupAnime()
                    YomiMediaType.MANGA -> mangas += entry
                    // An entry the manifest does not mention was exported as manga by an older
                    // writer, or added by another app afterwards. Keep it where it was declared.
                    null -> {
                        mangas += entry
                        ambiguous++
                    }
                }
            }

            return RoutedBackup(
                backup.withRoutedSections(mangas, novels, animes),
                ambiguousEntries = ambiguous,
            )
        }
    }

    /**
     * Opt-in fallback for markerless old sister exports.
     *
     * Only reachable when the user explicitly declared the file as an old Yomi backup. An entry
     * moves to the novel section only when its source is known as a novel source and not as a manga
     * or anime source; everything else stays manga and is counted as ambiguous.
     */
    data class LegacySisterExplicitFallback(
        private val mangaSourceClassifier: (Long) -> Boolean,
        private val novelSourceClassifier: (Long) -> Boolean,
        private val animeSourceClassifier: (Long) -> Boolean,
    ) : MediaRoutingPolicy {
        override fun route(backup: Backup): RoutedBackup {
            val mangas = mutableListOf<BackupManga>()
            val novels = backup.backupNovel.toMutableList()
            var ambiguous = 0

            backup.backupManga.forEach { entry ->
                val sourceId = entry.source
                val isNovel = novelSourceClassifier(sourceId)
                val isManga = mangaSourceClassifier(sourceId)
                val isAnime = animeSourceClassifier(sourceId)
                when {
                    isNovel && !isManga && !isAnime -> novels += entry.toBackupNovel()
                    isNovel -> {
                        // Known to more than one library type: unresolvable without guessing.
                        mangas += entry
                        ambiguous++
                    }
                    else -> mangas += entry
                }
            }

            return RoutedBackup(
                backup.withRoutedSections(mangas, novels, backup.backupAnime),
                ambiguousEntries = ambiguous,
            )
        }
    }
}

/**
 * Rebuild the typed sections plus the derived category and source lists.
 *
 * Categories are shared by a flattened export, so a novel or anime section that has entries but no
 * categories of its own inherits the flattened list.
 */
internal fun Backup.withRoutedSections(
    mangas: List<BackupManga>,
    novels: List<BackupNovel>,
    animes: List<BackupAnime>,
): Backup {
    val routedNovelSources = novels
        .map { novel ->
            backupSources.firstOrNull { it.sourceId == novel.source }
                ?: BackupSource(name = "", sourceId = novel.source)
        }
        .distinctBy { it.sourceId }
    val routedAnimeSources = animes
        .map { anime ->
            val source = backupSources.firstOrNull { it.sourceId == anime.source }
            BackupAnimeSource(name = source?.name.orEmpty(), sourceId = anime.source)
        }
        .distinctBy { it.sourceId }

    return copy(
        backupManga = mangas,
        backupNovel = novels,
        backupAnime = animes,
        backupNovelCategories = backupNovelCategories.ifEmpty {
            if (novels.isNotEmpty()) backupCategories else emptyList()
        },
        backupAnimeCategories = backupAnimeCategories.ifEmpty {
            if (animes.isNotEmpty()) backupCategories else emptyList()
        },
        backupNovelSources = (backupNovelSources + routedNovelSources).distinctBy { it.sourceId },
        backupAnimeSources = (backupAnimeSources + routedAnimeSources).distinctBy { it.sourceId },
    )
}
