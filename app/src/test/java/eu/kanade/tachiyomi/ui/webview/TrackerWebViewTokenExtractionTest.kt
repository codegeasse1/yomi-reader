package eu.kanade.tachiyomi.ui.webview

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TrackerWebViewTokenExtractionTest {

    @Test
    fun `extractNovelUpdatesCookie returns the full cookie header when wordpress login cookie is present`() {
        val cookie = "foo=bar; wordpress_logged_in_123=abc; path=/"

        extractNovelUpdatesCookie(cookie) shouldBe cookie
    }

    @Test
    fun `extractNovelListToken decodes base64 cookie and extracts access token`() {
        val cookie = "novellist=base64-eyJhY2Nlc3NfdG9rZW4iOiJ0b2tlbi0xMjMifQ==; path=/"

        extractNovelListToken(cookie) shouldBe "token-123"
    }

    @Test
    fun `normalizeNovelListToken accepts raw JWT`() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.signature"

        normalizeNovelListToken(jwt) shouldBe jwt
    }

    @Test
    fun `normalizeNovelListToken accepts Bearer JWT`() {
        val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.signature"

        normalizeNovelListToken("Bearer $jwt") shouldBe jwt
    }

    @Test
    fun `normalizeNovelListToken extracts access token from plain session JSON`() {
        val session = "{\"access_token\":\"json-token\"}"

        normalizeNovelListToken(session) shouldBe "json-token"
    }

    @Test
    fun `extractNovelListToken joins chunked cookies in numeric order`() {
        val encoded = java.util.Base64.getEncoder()
            .encodeToString("{\"access_token\":\"chunked-token\"}".toByteArray())
        val cookie = "foo=bar; novellist.1=${encoded.substring(
            10,
        )}; novellist.0=base64-${encoded.substring(0, 10)}; path=/"

        extractNovelListToken(cookie) shouldBe "chunked-token"
    }

    @Test
    fun `normalizeNovelListToken decodes base64url session JSON`() {
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"access_token\":\"url-token\"}".toByteArray())

        normalizeNovelListToken("base64-$encoded") shouldBe "url-token"
    }

    @Test
    fun `extractNovelListToken returns null for unrelated cookie header`() {
        extractNovelListToken("foo=bar; other=baz") shouldBe null
    }
}
