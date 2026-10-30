package com.aioffer.radar.core.time

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimeTickerTest {
    @Test
    fun emitsImmediatelyAndThenAtTheConfiguredInterval() = runTest {
        val clock = object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC

            override fun withZone(zone: ZoneId): Clock = this

            override fun instant(): Instant = Instant.ofEpochMilli(testScheduler.currentTime)
        }

        val emissions = timeTicker(clock, intervalMillis = 30_000L)
            .take(3)
            .toList()

        assertEquals(
            listOf(
                Instant.ofEpochMilli(0),
                Instant.ofEpochMilli(30_000),
                Instant.ofEpochMilli(60_000)
            ),
            emissions
        )
    }
}
