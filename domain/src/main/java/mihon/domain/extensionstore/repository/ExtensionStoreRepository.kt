package mihon.domain.extensionstore.repository

import kotlinx.coroutines.flow.Flow
import mihon.domain.extensionstore.model.ExtensionStore

interface ExtensionStoreRepository {
    suspend fun insert(indexUrl: String): Result<Unit>

    suspend fun insertFromPreference(indexUrl: String, name: String)

    suspend fun refreshAll()

    suspend fun upsertStore(store: ExtensionStore)

    /**
     * Stores the name the user gave this store. Kept out of [upsertStore] so a metadata refresh,
     * which overwrites every remote field, cannot silently revert a rename.
     */
    suspend fun setCustomName(indexUrl: String, customName: String?)

    suspend fun getAll(): List<ExtensionStore>

    fun getAllAsFlow(): Flow<List<ExtensionStore>>

    fun getCountAsFlow(): Flow<Long>

    suspend fun remove(indexUrl: String)

    /**
     * One-time port from the legacy repo tables. Default no-op for implementations that don't need
     * it; the data implementations run it from [getAll], which is what trust checks and the store
     * screens go through, and remember that it is done in an app-state preference.
     */
    suspend fun ensureLegacyMigrated() {}
}
