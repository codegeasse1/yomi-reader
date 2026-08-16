package eu.kanade.tachiyomi.data.backup.restore.restorers

// Kept in sync with the values the Trust*Extension interactors already skip; their companions are
// private, and each media type declares its own copy.
private const val NO_SIGNING_KEY = "no_signing_key"
private const val PLACEHOLDER_FINGERPRINT_PREFIX = "nofingerprint-"

/**
 * Whether a stored signing key is a placeholder rather than a real fingerprint.
 *
 * A store whose `repo.json` could not be reached is persisted with `NO_SIGNING_KEY`, and legacy repos
 * added without one get a synthesized `NOFINGERPRINT-<hash>`. Several rows can therefore carry the
 * same value, so it must never be used as an identity - restoring the second such store would
 * otherwise fail as "same signing key".
 */
internal fun isPlaceholderSigningKey(signingKey: String): Boolean {
    val key = signingKey.trim().lowercase()
    return key.isEmpty() ||
        key == NO_SIGNING_KEY ||
        key.startsWith(PLACEHOLDER_FINGERPRINT_PREFIX)
}
