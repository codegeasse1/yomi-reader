package eu.kanade.tachiyomi.extension.installer

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ApkInstallErrorClassifierTest {

    @Test
    fun `update incompatible maps to signature mismatch`() {
        assertEquals(
            ApkInstallFailure.SignatureMismatch,
            classifyInstallError("Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Package signatures do not match]"),
        )
        assertEquals(
            ApkInstallFailure.SignatureMismatch,
            classifyInstallError("INSTALL_FAILED_UPDATE_INCOMPATIBLE"),
        )
    }

    @Test
    fun `user cancelled and aborted map to aborted`() {
        assertEquals(
            ApkInstallFailure.PackageInstallerAborted,
            classifyInstallError("STATUS_FAILURE_ABORTED: User cancelled install"),
        )
        assertEquals(
            ApkInstallFailure.PackageInstallerAborted,
            classifyInstallError("Failure [INSTALL_FAILED_USER_CANCELLED]"),
        )
        assertEquals(
            ApkInstallFailure.PackageInstallerAborted,
            classifyInstallError("Failure [INSTALL_CANCELED by user]"),
        )
    }

    @Test
    fun `unknown messages stay unknown`() {
        assertEquals(
            ApkInstallFailure.Unknown("disk full"),
            classifyInstallError("disk full"),
        )
        assertEquals(ApkInstallFailure.Unknown(null), classifyInstallError(null))
        assertEquals(
            ApkInstallFailure.Unknown(""),
            classifyInstallError(""),
        )
    }
}
