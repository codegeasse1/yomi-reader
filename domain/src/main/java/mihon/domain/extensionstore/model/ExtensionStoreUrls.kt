package mihon.domain.extensionstore.model

/**
 * File names that extension repos host next to their base url. A repo can legitimately be
 * referenced by any of them, because users paste whichever url they happen to find, and
 * because legacy repos, store indexes and novel plugin repos use different file names.
 *
 * Every conversion between a repo base url and an index url must therefore go through
 * [toExtensionStoreBaseUrl] instead of blindly appending or stripping one specific suffix.
 * Appending without normalizing first is what persisted urls such as
 * `.../repo/repo.json/repo.json`, which then fail with HTTP 404 on every store refresh and
 * extension list fetch.
 */
private val INDEX_FILE_NAMES = listOf(
    "repo.json",
    "index.min.json",
    "index.json",
    "plugins.min.json",
    "plugins.json",
)

/** Whether [this] points at a repo index/metadata file instead of a repo base url. */
fun String.isExtensionStoreIndexUrl(): Boolean {
    val trimmed = trim().trimEnd('/')
    return INDEX_FILE_NAMES.any { trimmed.endsWith("/$it", ignoreCase = true) }
}

/**
 * Canonical repo base url: the url without trailing slashes and without any (possibly
 * repeated) index file suffix. Idempotent, so it is safe to apply to already stored values.
 */
fun String.toExtensionStoreBaseUrl(): String {
    var url = trim().trimEnd('/')
    while (true) {
        val name = INDEX_FILE_NAMES.firstOrNull { url.endsWith("/$it", ignoreCase = true) } ?: break
        url = url.dropLast(name.length + 1).trimEnd('/')
    }
    return url
}

/** Canonical `repo.json` metadata url of a legacy repo, derived from any of its urls. */
fun String.toLegacyExtensionRepoUrl(): String = "${toExtensionStoreBaseUrl()}/repo.json"

/** Canonical `index.min.json` extension listing url of a legacy repo. */
fun String.toLegacyExtensionIndexUrl(): String = "${toExtensionStoreBaseUrl()}/index.min.json"

/**
 * Collapses a duplicated index file suffix while keeping the file name the repo was added
 * with, e.g. `.../repo/repo.json/repo.json` becomes `.../repo/repo.json`. Used to repair
 * values that were already written to the database.
 */
fun String.collapseDuplicateExtensionStoreSuffix(): String {
    val trimmed = trim().trimEnd('/')
    val base = trimmed.toExtensionStoreBaseUrl()
    if (base == trimmed) return trimmed
    val fileName = trimmed.removePrefix(base).trim('/').substringBefore('/')
    return if (fileName.isBlank()) base else "$base/$fileName"
}
