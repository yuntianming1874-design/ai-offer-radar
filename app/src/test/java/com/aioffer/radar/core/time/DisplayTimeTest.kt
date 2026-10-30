package com.aioffer.radar.core.time

import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayTimeTest {

    @Test
    fun formatsInShanghaiRegardlessOfSystemDefault() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals(
                "2026-06-01 08:00 北京时间",
                DisplayTime.format(Instant.parse("2026-06-01T00:00:00Z"))
            )
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test
    fun crossingUtcDateBoundaryUsesShanghaiDate() {
        assertEquals(
            "2026-01-01 00:30 北京时间",
            DisplayTime.format(Instant.parse("2025-12-31T16:30:00Z"))
        )
    }

    @Test
    fun formatsEpochAtFixedOffset() {
        assertEquals("1970-01-01 08:00 北京时间", DisplayTime.format(Instant.EPOCH))
    }
}
