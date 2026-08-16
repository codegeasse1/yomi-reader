package eu.kanade.presentation.reader.novel

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NovelBookScrollSurfaceReportTest {

    @Test
    fun `parses a plain auto-scroll sync report`() {
        val report = parseBookAutoScrollSyncReport("""{"running": true, "forward": true}""")

        report?.loopRunning shouldBe true
        report?.canScrollForward shouldBe true
    }

    @Test
    fun `parses a report whose loop stopped at the document end`() {
        val report = parseBookAutoScrollSyncReport("""{"running": false, "forward": false}""")

        report?.loopRunning shouldBe false
        report?.canScrollForward shouldBe false
    }

    @Test
    fun `parses the double quoted and escaped string evaluateJavascript returns`() {
        // evaluateJavascript hands JSON back as a quoted and escaped JSON string.
        val report = parseBookAutoScrollSyncReport("\"{\\\"running\\\": true, \\\"forward\\\": false}\"")

        report?.loopRunning shouldBe true
        report?.canScrollForward shouldBe false
    }

    @Test
    fun `null and blank results mean the engine is not reachable`() {
        parseBookAutoScrollSyncReport(null) shouldBe null
        parseBookAutoScrollSyncReport("null") shouldBe null
        parseBookAutoScrollSyncReport("") shouldBe null
    }

    @Test
    fun `the sync script stops the loop when asked for zero pixels`() {
        val script = buildBookAutoScrollSyncJavascript(0)

        script shouldBe """
            (function() {
              var engine = window.__anBookEngine;
              if (engine && typeof engine.setAutoScroll === 'function') {
                engine.setAutoScroll(0);
                var running = typeof engine.autoScrollActive === 'function' ? engine.autoScrollActive() : false;
                var forward = typeof engine.canScrollForward === 'function' ? engine.canScrollForward() : true;
                return JSON.stringify({ running: running, forward: forward });
              }
              return JSON.stringify({ running: false, forward: false });
            })()
        """.trimIndent()
    }
}
