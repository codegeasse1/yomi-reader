package eu.kanade.tachiyomi.data.backup.create

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.backup.service.BackupPreferences

class BackupCreateJobTest {

    @Test
    fun `cloudBackupUri preference defaults to empty string`() {
        val store = mockk<PreferenceStore>()
        val pref = mockk<Preference<String>>()

        every { store.getString(any(), any()) } returns pref
        every { pref.get() } returns ""

        val backupPreferences = BackupPreferences(store)
        backupPreferences.cloudBackupUri().get() shouldBe ""

        verify { store.getString("__APP_STATE_cloud_backup_uri", "") }
    }

    @Test
    fun `cloudBackupUri preference can store a URI`() {
        val store = mockk<PreferenceStore>()
        val pref = mockk<Preference<String>>(relaxed = true)
        val testUri = "content://com.google.android.apps.drive/tree/someFolder"

        every { store.getString(any(), any()) } returns pref

        val backupPreferences = BackupPreferences(store)
        backupPreferences.cloudBackupUri().set(testUri)

        verify { pref.set(testUri) }
    }

    @Test
    fun `cloudBackupUri preference returns stored URI`() {
        val store = mockk<PreferenceStore>()
        val pref = mockk<Preference<String>>()
        val testUri = "content://com.google.android.apps.drive/tree/someFolder"

        every { store.getString(any(), any()) } returns pref
        every { pref.get() } returns testUri

        val backupPreferences = BackupPreferences(store)
        backupPreferences.cloudBackupUri().get() shouldBe testUri
    }
}
