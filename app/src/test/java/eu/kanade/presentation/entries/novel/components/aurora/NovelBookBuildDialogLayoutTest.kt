package eu.kanade.presentation.entries.novel.components.aurora

import androidx.compose.ui.unit.dp
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelBookBuildDialogLayoutTest {

    @Test
    fun `narrow dialog stacks build actions`() {
        resolveNovelBookBuildActionsLayout(360.dp) shouldBe NovelBookBuildActionsLayout.STACKED
    }

    @Test
    fun `wide dialog keeps build actions inline`() {
        resolveNovelBookBuildActionsLayout(480.dp) shouldBe NovelBookBuildActionsLayout.INLINE
    }
}
