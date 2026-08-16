package eu.kanade.tachiyomi.network.interceptor

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CloudflareChallengeResolverTest {

    @Test
    fun `interactive probe only treats human widgets as interactive`() {
        assertTrue(INTERACTIVE_WIDGET_PROBE.contains("cf-turnstile"))
        assertTrue(INTERACTIVE_WIDGET_PROBE.contains("challenges.cloudflare.com"))

        assertFalse(INTERACTIVE_WIDGET_PROBE.contains("challenge-stage"))
        assertFalse(INTERACTIVE_WIDGET_PROBE.contains("cf-please-wait"))
    }
}
