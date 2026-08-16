package eu.kanade.presentation.reader.novel

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class NovelImageActionHelperTest {

    @Test
    fun `resolveImageFile returns null for blank url`() {
        runBlocking {
            val result = NovelImageActionHelper.resolveImageFile(
                context = mockk(relaxed = true),
                imageUrl = "   ",
            )
            result shouldBe null
        }
    }
}
