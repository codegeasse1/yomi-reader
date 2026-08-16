package eu.kanade.tachiyomi.data.sync

import eu.kanade.domain.sync.SyncPreferences
import eu.kanade.tachiyomi.data.backup.create.BackupOptions

fun SyncPreferences.getCloudSyncOptions(): BackupOptions {
    val set = cloudSyncOptions().get()
    return BackupOptions(
        libraryEntries = "libraryEntries" in set,
        backupManga = "backupManga" in set,
        backupAnime = "backupAnime" in set,
        backupNovel = "backupNovel" in set,
        categories = "categories" in set,
        chapters = "chapters" in set,
        tracking = "tracking" in set,
        history = "history" in set,
        readEntries = "readEntries" in set,
        appSettings = "appSettings" in set,
        extensionRepoSettings = "extensionRepoSettings" in set,
        customButton = "customButton" in set,
        sourceSettings = "sourceSettings" in set,
        privateSettings = "privateSettings" in set,
        extensions = "extensions" in set,
        achievements = "achievements" in set,
        stats = "stats" in set,
        sisterAppCompatible = "sisterAppCompatible" in set,
    )
}

fun SyncPreferences.setCloudSyncOptions(options: BackupOptions) {
    val set = mutableSetOf<String>()
    if (options.libraryEntries) set.add("libraryEntries")
    if (options.backupManga) set.add("backupManga")
    if (options.backupAnime) set.add("backupAnime")
    if (options.backupNovel) set.add("backupNovel")
    if (options.categories) set.add("categories")
    if (options.chapters) set.add("chapters")
    if (options.tracking) set.add("tracking")
    if (options.history) set.add("history")
    if (options.readEntries) set.add("readEntries")
    if (options.appSettings) set.add("appSettings")
    if (options.extensionRepoSettings) set.add("extensionRepoSettings")
    if (options.customButton) set.add("customButton")
    if (options.sourceSettings) set.add("sourceSettings")
    if (options.privateSettings) set.add("privateSettings")
    if (options.extensions) set.add("extensions")
    if (options.achievements) set.add("achievements")
    if (options.stats) set.add("stats")
    if (options.sisterAppCompatible) set.add("sisterAppCompatible")
    cloudSyncOptions().set(set)
}
