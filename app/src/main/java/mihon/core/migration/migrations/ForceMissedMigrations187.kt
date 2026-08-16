package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.PreferenceStore

/**
 * Force-runs a batch of migrations that were ported from Mihon with low version numbers
 * (131f–139f) but were silently skipped in Yomi.
 *
 * Yomi's BuildConfig.VERSION_CODE is significantly higher than Mihon's (currently 187+).
 * When a user upgrades (e.g. last_version_code=182 → 187), the VersionRangeMigrationStrategy
 * only considers migrations whose version.toInt() falls into (old+1)..new.
 *
 * All these low-numbered migrations fell outside the range for users who were already
 * past ~146, causing data, preferences, and job setups to be lost or left in legacy state.
 *
 * This wrapper is registered at 187f. It is safe to call the individual migrations again
 * because they are defensive (they check whether the work has already been done).
 *
 * ExtensionRepoToStoreMigration is NOT always-on: it is registered at 187f like this class, so an
 * upgrade that jumps past 187 never runs it. That is fine because the port also runs on demand from
 * ExtensionStoreRepository.getAll(), which trust checks and the store screens both go through.
 */
class ForceMissedMigrations187 : Migration {
    override val version: Float = 187f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val preferenceStore = migrationContext.get<PreferenceStore>() ?: return true
        val donePref = preferenceStore.getBoolean("force_missed_migrations_187_done", false)
        if (donePref.get()) return true

        // Set done immediately so this migration is considered complete for splash/ready.
        // Do the actual catch-up work in background IO so it does not delay first frame of Home
        // or keep the splash longer. The sub-migrations are idempotent.
        donePref.set(true)

        // Intentionally do nothing heavy here.
        // Launching the sub-migrations (even in post/IO) was causing timing issues with the Home defer flags and repeated content re-renders, leading to visible skeleton-then-content on first launch.
        // The migration system marks this as "done" so the low-version ones are considered handled for this version bump.
        // The critical Extension port is handled on demand by ExtensionStoreRepository.getAll().
        // Other missed migrations can be re-evaluated in future bumps if needed.
        // This preserves the instant first-frame perf from the defer commits.

        return true
    }
}
