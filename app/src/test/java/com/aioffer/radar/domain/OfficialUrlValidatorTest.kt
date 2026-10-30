package com.aioffer.radar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfficialUrlValidatorTest {
    @Test
    fun httpsUrl_withValidHost_isAccepted() {
        assertEquals(
            "https://example.com/subscribe?plan=pro",
            OfficialUrlValidator.validate("https://example.com/subscribe?plan=pro")
        )
    }

    @Test
    fun production_rejectsHttpButDemoModeAllowsIt() {
        assertNull(OfficialUrlValidator.validate("http://example.com/subscribe"))
        assertEquals(
            "http://example.com/subscribe",
            OfficialUrlValidator.validate("http://example.com/subscribe", allowHttp = true)
        )
    }

    @Test
    fun uppercaseHttpsScheme_isAccepted() {
        assertEquals(
            "HTTPS://EXAMPLE.COM/Path?A=B",
            OfficialUrlValidator.validate("HTTPS://EXAMPLE.COM/Path?A=B")
        )
    }

    @Test
    fun emptyMalformedAndInvalidHosts_areRejected() {
        val invalidUrls = listOf(
            null,
            "",
            "   ",
            "not a url",
            "https://",
            "https:///subscribe",
            "https://exa mple.com/subscribe",
            "https://invalid_host/subscribe",
            "https://example.com:invalid/subscribe"
        )

        invalidUrls.forEach { url ->
            assertNull("Expected invalid URL: $url", OfficialUrlValidator.validate(url))
        }
    }

    @Test
    fun unsupportedSchemesAndUserInfo_areRejected() {
        val invalidUrls = listOf(
            "intent://example.com/subscribe",
            "javascript://example.com/alert",
            "file://example.com/subscribe",
            "ftp://example.com/subscribe",
            "https://user:password@example.com/subscribe"
        )

        invalidUrls.forEach { url ->
            assertNull(OfficialUrlValidator.validate(url))
        }
    }
}
