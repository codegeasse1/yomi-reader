package eu.kanade.tachiyomi.extension

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PrivateExtensionReplacementTest {

    @Test
    fun `a build signed by the same key may replace the installed private copy`() {
        canReplacePrivateExtension(
            installedVersionCode = 10,
            newVersionCode = 11,
            installedSignatures = listOf("aaa"),
            newSignatures = listOf("aaa"),
        ) shouldBe true
    }

    @Test
    fun `a build signed by another key may not`() {
        canReplacePrivateExtension(
            installedVersionCode = 10,
            newVersionCode = 11,
            installedSignatures = listOf("aaa"),
            newSignatures = listOf("bbb"),
        ) shouldBe false
    }

    @Test
    fun `an unsigned build may not`() {
        canReplacePrivateExtension(
            installedVersionCode = 10,
            newVersionCode = 11,
            installedSignatures = listOf("aaa"),
            newSignatures = emptyList(),
        ) shouldBe false
    }

    @Test
    fun `downgrades are refused even when the key matches`() {
        canReplacePrivateExtension(
            installedVersionCode = 11,
            newVersionCode = 10,
            installedSignatures = listOf("aaa"),
            newSignatures = listOf("aaa"),
        ) shouldBe false
    }

    @Test
    fun `a rotated key set keeps the old signature`() {
        canReplacePrivateExtension(
            installedVersionCode = 10,
            newVersionCode = 12,
            installedSignatures = listOf("aaa"),
            newSignatures = listOf("aaa", "bbb"),
        ) shouldBe true
    }
}
