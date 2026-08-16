package eu.kanade.tachiyomi.data.backup.restore

internal fun resolveRestoredText(
    backupValue: String?,
    backupVersion: Long,
    currentValue: String,
    currentVersion: Long,
): String {
    return if (backupVersion > currentVersion) {
        backupValue ?: currentValue
    } else {
        currentValue
    }
}
