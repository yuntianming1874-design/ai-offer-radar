package com.aioffer.radar.core.time

import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/**
 * Emits the current instant immediately and then once per [intervalMillis].
 *
 * Used by UI state holders so time-bound offers disappear from the "current"
 * section without waiting for a database change or a screen recreation.
 */
fun timeTicker(
    clock: Clock,
    intervalMillis: Long = 30_000L
): Flow<Instant> = flow {
    while (true) {
        emit(clock.instant())
        delay(intervalMillis)
    }
}.distinctUntilChanged()
