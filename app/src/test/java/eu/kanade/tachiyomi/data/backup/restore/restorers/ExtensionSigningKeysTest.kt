package eu.kanade.tachiyomi.data.backup.restore.restorers

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ExtensionSigningKeysTest {

    @Test
    fun `placeholder keys are recognised regardless of case and padding`() {
        isPlaceholderSigningKey("NO_SIGNING_KEY") shouldBe true
        isPlaceholderSigningKey("no_signing_key") shouldBe true
        isPlaceholderSigningKey(" NOFINGERPRINT-abc123 ") shouldBe true
        isPlaceholderSigningKey("") shouldBe true
    }

    @Test
    fun `real fingerprints are not placeholders`() {
        isPlaceholderSigningKey("9c7e7f0d4b0a5f8f1e1d5a11b6c0d2e3") shouldBe false
    }
}
